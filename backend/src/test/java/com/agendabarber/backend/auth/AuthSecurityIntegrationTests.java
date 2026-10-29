package com.agendabarber.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import com.agendabarber.backend.scheduling.AgendaSchedulingService;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("neon")
@Testcontainers
@Import(AuthSecurityIntegrationTests.ScopeProbeConfig.class)
class AuthSecurityIntegrationTests {
    private static final String ISSUER = "https://auth.agenda-barber.invalid";
    private static final String AUDIENCE = "agenda-barber-api";
    private static final String PASSWORD = "TestOnlyStrongPass#2026";
    private static final KeyPair KEYS = createKeys();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_auth_test").withUsername("tests").withPassword("tests");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("AUTH_JWT_PRIVATE_KEY_PEM", () -> pem("PRIVATE KEY", KEYS.getPrivate().getEncoded()));
        registry.add("AUTH_JWT_PUBLIC_KEY_PEM", () -> pem("PUBLIC KEY", KEYS.getPublic().getEncoded()));
        registry.add("AUTH_JWT_ISSUER", () -> ISSUER);
        registry.add("AUTH_JWT_AUDIENCE", () -> AUDIENCE);
    }

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load();
        assertEquals(7, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @Value("$" + "{local.server.port}")
    private int port;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AgendaSchedulingService scheduling;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loginProducesRs256AndScopedIdentity() throws Exception {
        Account a = account();
        assertEquals(401, get("/api/v1/auth/me", null).statusCode());
        HttpResponse<String> login = login(a.email(), PASSWORD);
        assertEquals(200, login.statusCode());
        JsonNode body = mapper.readTree(login.body());
        assertEquals("Bearer", body.get("tokenType").asText());
        assertEquals(900, body.get("expiresInSeconds").asInt());
        String jwt = body.get("accessToken").asText();
        String header = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), StandardCharsets.UTF_8);
        assertEquals("RS256", mapper.readTree(header).get("alg").asText());
        HttpResponse<String> me = get("/api/v1/auth/me", jwt);
        assertEquals(200, me.statusCode());
        assertEquals(a.barber().toString(), mapper.readTree(me.body()).get("barberId").asText());
        assertFalse(me.body().contains(a.email()));
        assertFalse(me.body().contains(PASSWORD));
    }

    @Test
    void wrongAndUnknownCredentialsReturnSameGeneric401() throws Exception {
        Account a = account();
        HttpResponse<String> wrong = login(a.email(), "Invalid-Test-Only");
        HttpResponse<String> unknown = login(UUID.randomUUID() + "@tests.invalid", "Invalid-Test-Only");
        assertEquals(401, wrong.statusCode());
        assertEquals(401, unknown.statusCode());
        assertEquals(wrong.body(), unknown.body());
        assertFalse(wrong.body().contains(a.email()));
        assertFalse(wrong.body().contains("Invalid-Test-Only"));
    }

    @Test
    void scopeRequiresTheAuthenticatedBarber() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        assertEquals(200, get("/__tests/scoped/" + a.barber(), jwt).statusCode());
        assertEquals(403, get("/__tests/scoped/" + UUID.randomUUID(), jwt).statusCode());
    }

    @Test
    void inactiveAccountCannotAuthenticateOrReuseJwt() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        update("UPDATE users SET active=false WHERE id=?", a.user());
        assertEquals(401, login(a.email(), PASSWORD).statusCode());
        assertEquals(401, get("/api/v1/auth/me", jwt).statusCode());
    }

    @Test
    void badSignatureAudienceIssuerAndExpiryReturn401() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        String[] parts = jwt.split("\\.");
        parts[2] = parts[2].substring(0, 5) + (parts[2].charAt(5) == 'A' ? 'B' : 'A')
                + parts[2].substring(6);
        assertEquals(401, get("/api/v1/auth/me", String.join(".", parts)).statusCode());
        assertEquals(401, get("/api/v1/auth/me",
                forge(a, ISSUER, "wrong-audience", Instant.now().plusSeconds(600))).statusCode());
        assertEquals(401, get("/api/v1/auth/me",
                forge(a, "https://wrong.invalid", AUDIENCE, Instant.now().plusSeconds(600))).statusCode());
        assertEquals(401, get("/api/v1/auth/me",
                forge(a, ISSUER, AUDIENCE, Instant.now().minusSeconds(120))).statusCode());
    }

    @Test
    void sixthLoginAttemptReturns429() throws Exception {
        String email = UUID.randomUUID() + "@tests.invalid";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(email, "Wrong-Test-Only").statusCode());
        }
        HttpResponse<String> response = login(email, "Wrong-Test-Only");
        assertEquals(429, response.statusCode());
        assertEquals("900", response.headers().firstValue("Retry-After").orElse(""));
    }

    @Test
    void storedPasswordIsBcryptAndKeysFailClosed() throws Exception {
        Account a = account();
        try (Connection db = database();
                PreparedStatement statement = db.prepareStatement("SELECT password_hash FROM users WHERE id=?")) {
            statement.setObject(1, a.user());
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                String hash = result.getString(1);
                assertNotEquals(PASSWORD, hash);
                assertTrue(hash.startsWith("$2"));
                assertTrue(passwordEncoder.matches(PASSWORD, hash));
            }
        }
        assertThrows(IllegalStateException.class,
                () -> new AuthJwtConfig().jwtKeyMaterial(new org.springframework.mock.env.MockEnvironment()));
    }

    @Test
    void refreshRotatesSingleUseTokenAndNeverStoresItsRawValue() throws Exception {
        Account a = account();
        JsonNode initial = mapper.readTree(login(a.email(), PASSWORD).body());
        String refresh = initial.get("refreshToken").asText();
        HttpResponse<String> response = refresh(refresh);
        assertEquals(200, response.statusCode());
        JsonNode rotated = mapper.readTree(response.body());
        assertNotEquals(refresh, rotated.get("refreshToken").asText());
        assertEquals(900, rotated.get("expiresInSeconds").asInt());
        assertEquals(1209600, rotated.get("refreshExpiresInSeconds").asInt());
        assertEquals(200, get("/api/v1/auth/me", rotated.get("accessToken").asText()).statusCode());
        try (Connection db = database();
                PreparedStatement query = db.prepareStatement(
                        "SELECT count(*) FROM auth_refresh_tokens WHERE token_hash=?")) {
            query.setString(1, refresh);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    void replayRevokesEntireTokenFamilyIncludingLatestJwt() throws Exception {
        Account a = account();
        JsonNode first = mapper.readTree(login(a.email(), PASSWORD).body());
        String oldRefresh = first.get("refreshToken").asText();
        JsonNode next = mapper.readTree(refresh(oldRefresh).body());
        assertEquals(401, refresh(oldRefresh).statusCode());
        assertEquals(401, refresh(next.get("refreshToken").asText()).statusCode());
        assertEquals(401, get("/api/v1/auth/me", next.get("accessToken").asText()).statusCode());
    }

    @Test
    void logoutRevokesOneSessionAndItsJwtButNotAnotherSession() throws Exception {
        Account a = account();
        JsonNode first = mapper.readTree(login(a.email(), PASSWORD).body());
        JsonNode second = mapper.readTree(login(a.email(), PASSWORD).body());
        assertEquals(204, request("POST", "/api/v1/auth/logout", "{}", first.get("accessToken").asText())
                .statusCode());
        assertEquals(401, get("/api/v1/auth/me", first.get("accessToken").asText()).statusCode());
        assertEquals(401, refresh(first.get("refreshToken").asText()).statusCode());
        assertEquals(200, get("/api/v1/auth/me", second.get("accessToken").asText()).statusCode());
        assertEquals(200, refresh(second.get("refreshToken").asText()).statusCode());
        assertEquals(401, request("POST", "/api/v1/auth/logout", "{}", null).statusCode());
    }

    @Test
    void refreshRejectsUnknownMalformedAndInactiveAccount() throws Exception {
        Account a = account();
        JsonNode first = mapper.readTree(login(a.email(), PASSWORD).body());
        assertEquals(401, refresh("bad").statusCode());
        assertEquals(401, refresh("A".repeat(43)).statusCode());
        update("UPDATE users SET active=false WHERE id=?", a.user());
        assertEquals(401, refresh(first.get("refreshToken").asText()).statusCode());
    }

    private HttpResponse<String> refresh(String refreshToken) throws Exception {
        return request("POST", "/api/v1/auth/refresh",
                mapper.writeValueAsString(Map.of("refreshToken", refreshToken)), null);
    }

    @Test
    void changingCredentialVersionInvalidatesAccessAndRefreshTokens() throws Exception {
        Account a = account();
        JsonNode credentials = mapper.readTree(login(a.email(), PASSWORD).body());
        String access = credentials.get("accessToken").asText();
        String refresh = credentials.get("refreshToken").asText();
        assertEquals(200, get("/api/v1/auth/me", access).statusCode());
        update("UPDATE users SET credential_version=credential_version+1 WHERE id=?", a.user());
        assertEquals(401, get("/api/v1/auth/me", access).statusCode());
        assertEquals(401, refresh(refresh).statusCode());
    }

    @Test
    void replayRevokesOnlyAffectedSessionAndKeepsIndependentLogin() throws Exception {
        Account a = account();
        JsonNode first = mapper.readTree(login(a.email(), PASSWORD).body());
        JsonNode independent = mapper.readTree(login(a.email(), PASSWORD).body());
        String used = first.get("refreshToken").asText();
        JsonNode rotated = mapper.readTree(refresh(used).body());
        assertEquals(401, refresh(used).statusCode());
        assertEquals(401, get("/api/v1/auth/me", rotated.get("accessToken").asText()).statusCode());
        assertEquals(200, get("/api/v1/auth/me", independent.get("accessToken").asText()).statusCode());
        assertEquals(200, refresh(independent.get("refreshToken").asText()).statusCode());
    }

    @Test
    void simultaneousRefreshRequestsPermitOnlyOneRotationAndRevokeOnReplay() throws Exception {
        Account a = account();
        JsonNode first = mapper.readTree(login(a.email(), PASSWORD).body());
        String raw = first.get("refreshToken").asText();
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<String>> left = executor.submit(() -> {
                go.await(15, TimeUnit.SECONDS);
                return refresh(raw);
            });
            Future<HttpResponse<String>> right = executor.submit(() -> {
                go.await(15, TimeUnit.SECONDS);
                return refresh(raw);
            });
            go.countDown();
            HttpResponse<String> one = left.get(60, TimeUnit.SECONDS);
            HttpResponse<String> two = right.get(60, TimeUnit.SECONDS);
            assertEquals(1, List.of(one, two).stream().filter(r -> r.statusCode() == 200).count());
            assertEquals(1, List.of(one, two).stream().filter(r -> r.statusCode() == 401).count());
            String winner = one.statusCode() == 200 ? one.body() : two.body();
            JsonNode rotated = mapper.readTree(winner);
            assertEquals(401, refresh(rotated.get("refreshToken").asText()).statusCode());
            assertEquals(401, get("/api/v1/auth/me", rotated.get("accessToken").asText()).statusCode());
        }
    }


    @Test
    void servicesCrudSupportsUx07CardsAndUx04DropdownWithStrictBarberIsolation() throws Exception {
        Account a = account();
        Account b = account();
        String aJwt = tokenFor(a);
        String bJwt = tokenFor(b);
        assertEquals(401, request("GET", "/api/v1/services", null, null).statusCode());
        String payload = """
                {"name":"  Corte Degrade  ","durationMinutes":45,"defaultPrice":45.50,
                 "isHaircut":true,"iconKey":"SCISSORS"}
                """;
        HttpResponse<String> created = request("POST", "/api/v1/services", payload, aJwt);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode view = mapper.readTree(created.body());
        String id = view.get("id").asText();
        assertEquals("/api/v1/services/" + id, created.headers().firstValue("Location").orElse(""));
        assertEquals("Corte Degrade", view.get("name").asText());
        assertEquals(45, view.get("durationMinutes").asInt());
        assertEquals(0, new BigDecimal("45.50").compareTo(view.get("defaultPrice").decimalValue()));
        assertTrue(view.get("isHaircut").asBoolean());
        assertTrue(view.get("active").asBoolean());
        assertEquals("SCISSORS", view.get("iconKey").asText());
        assertTrue(view.has("createdAt"));
        assertEquals(1, mapper.readTree(request("GET", "/api/v1/services?activeOnly=true",
                null, aJwt).body()).size());
        assertEquals(0, mapper.readTree(request("GET", "/api/v1/services", null, bJwt).body()).size());
        assertEquals(404, request("GET", "/api/v1/services/" + id, null, bJwt).statusCode());
        assertEquals(404, request("PATCH", "/api/v1/services/" + id,
                "{\"defaultPrice\":100}", bJwt).statusCode());
        assertEquals(404, request("DELETE", "/api/v1/services/" + id, null, bJwt).statusCode());

        HttpResponse<String> patched = request("PATCH", "/api/v1/services/" + id,
                "{\"durationMinutes\":60,\"defaultPrice\":50.00,\"iconKey\":\"COMBO\"}", aJwt);
        assertEquals(200, patched.statusCode(), patched.body());
        assertEquals(60, mapper.readTree(patched.body()).get("durationMinutes").asInt());
        assertEquals("COMBO", mapper.readTree(patched.body()).get("iconKey").asText());
        assertEquals(204, request("DELETE", "/api/v1/services/" + id, null, aJwt).statusCode());
        assertEquals(0, mapper.readTree(request("GET", "/api/v1/services?activeOnly=true",
                null, aJwt).body()).size());
        assertEquals(1, mapper.readTree(request("GET", "/api/v1/services",
                null, aJwt).body()).size());
        assertFalse(mapper.readTree(request("GET", "/api/v1/services/" + id,
                null, aJwt).body()).get("active").asBoolean());
        assertEquals(200, request("PATCH", "/api/v1/services/" + id,
                "{\"active\":true}", aJwt).statusCode());
        assertEquals(1, mapper.readTree(request("GET", "/api/v1/services?activeOnly=true",
                null, aJwt).body()).size());
    }

    @Test
    void servicesValidateMoneyDurationNamesAndVisualEnums() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        String base = "{\"name\":\"Barba\",\"durationMinutes\":30,"
                + "\"defaultPrice\":25.00,\"isHaircut\":false}";
        List<String> invalid = List.of(
                "{\"name\":\" \",\"durationMinutes\":30,\"defaultPrice\":12,\"isHaircut\":true}",
                "{\"name\":\"x\",\"durationMinutes\":0,\"defaultPrice\":12,\"isHaircut\":true}",
                "{\"name\":\"x\",\"durationMinutes\":481,\"defaultPrice\":12,\"isHaircut\":true}",
                "{\"name\":\"x\",\"durationMinutes\":30,\"defaultPrice\":-1,\"isHaircut\":true}",
                "{\"name\":\"x\",\"durationMinutes\":30,\"defaultPrice\":10.999,\"isHaircut\":true}",
                "{\"name\":\"x\",\"durationMinutes\":30,\"defaultPrice\":12}",
                "{\"name\":\"x\",\"durationMinutes\":30,\"defaultPrice\":12,\"isHaircut\":true,"
                        + "\"iconKey\":\"INVALID\"}");
        for (String input : invalid) {
            assertEquals(400, request("POST", "/api/v1/services", input, jwt).statusCode(), input);
        }
        HttpResponse<String> created = request("POST", "/api/v1/services", base, jwt);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode item = mapper.readTree(created.body());
        assertEquals("BEARD", item.get("iconKey").asText());
        String url = "/api/v1/services/" + item.get("id").asText();
        HttpResponse<String> haircut = request("POST", "/api/v1/services",
                "{\"name\":\"Corte Social\",\"durationMinutes\":30,"
                        + "\"defaultPrice\":40.00,\"isHaircut\":true}", jwt);
        assertEquals(201, haircut.statusCode(), haircut.body());
        assertEquals("SCISSORS", mapper.readTree(haircut.body()).get("iconKey").asText());
        HttpResponse<String> eyebrow = request("POST", "/api/v1/services",
                "{\"name\":\"Sobrancelha\",\"durationMinutes\":15,"
                        + "\"defaultPrice\":15.00,\"isHaircut\":false,\"iconKey\":\"EYEBROW\"}", jwt);
        assertEquals(201, eyebrow.statusCode(), eyebrow.body());
        assertEquals("EYEBROW", mapper.readTree(eyebrow.body()).get("iconKey").asText());
        assertEquals(400, request("PATCH", url, "{}", jwt).statusCode());
        assertEquals(400, request("PATCH", url, "{\"name\":\"   \"}", jwt).statusCode());
        assertEquals(400, request("PATCH", url, "{\"defaultPrice\":10.001}", jwt).statusCode());
        assertEquals(400, request("POST", "/api/v1/services", "{bad json}", jwt).statusCode());
    }

    @Test
    void serviceCatalogChangesDoNotRewriteHistoricalAppointmentSnapshot() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        HttpResponse<String> created = request("POST", "/api/v1/services",
                "{\"name\":\"Tradicional\",\"durationMinutes\":60,"
                        + "\"defaultPrice\":40.00,\"isHaircut\":true}", jwt);
        assertEquals(201, created.statusCode(), created.body());
        UUID service = UUID.fromString(mapper.readTree(created.body()).get("id").asText());
        UUID client = UUID.randomUUID();
        update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Client')", client, a.barber());
        for (int weekday = 1; weekday <= 7; weekday++) {
            update("INSERT INTO working_hours(barber_id,weekday,opens_at,closes_at) "
                    + "VALUES(?,?,TIME '08:00',TIME '18:00')", a.barber(), weekday);
        }
        UUID appointment = scheduling.schedule(a.barber(), client, service,
                Instant.parse("2030-03-01T13:00:00Z"), Instant.parse("2030-03-01T14:00:00Z"));
        assertEquals(200, request("PATCH", "/api/v1/services/" + service,
                "{\"name\":\"Novo Nome\",\"defaultPrice\":70.00}", jwt).statusCode());
        assertEquals(204, request("DELETE", "/api/v1/services/" + service,
                null, jwt).statusCode());
        try (Connection connection = database();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT service_name_snapshot, price_snapshot FROM appointments WHERE id=?")) {
            statement.setObject(1, appointment);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("Tradicional", result.getString("service_name_snapshot"));
                assertEquals(new BigDecimal("40.00"), result.getBigDecimal("price_snapshot"));
            }
        }
    }


    @Test
    void clientsCrudExposesUx05FieldsAndUx06FavoritesAndSearch() throws Exception {
        Account owner = account();
        String jwt = tokenFor(owner);
        assertEquals(401, get("/api/v1/clients", null).statusCode());
        String payload = """
                {"name":"  Ana Silva  ","phone":"+55 (31) 99999-1234",
                 "email":"ANA@EXAMPLE.COM","birthDate":"2000-06-15",
                 "notes":"Prefere corte curto","favorite":true,
                 "photoUrl":"https://img.example.test/ana.png"}
                """;
        HttpResponse<String> created = request("POST", "/api/v1/clients", payload, jwt);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode view = mapper.readTree(created.body());
        String id = view.get("id").asText();
        assertEquals("/api/v1/clients/" + id,
                created.headers().firstValue("Location").orElse(""));
        assertEquals("Ana Silva", view.get("name").asText());
        assertEquals("31999991234", view.get("phone").asText());
        assertEquals("ana@example.com", view.get("email").asText());
        assertEquals("2000-06-15", view.get("birthDate").asText());
        assertEquals("https://img.example.test/ana.png", view.get("photoUrl").asText());
        assertTrue(view.get("favorite").asBoolean());
        assertEquals(0, view.get("completedAppointments").asLong());
        assertTrue(view.get("lastAppointmentAt").isNull());
        assertEquals(1, mapper.readTree(get("/api/v1/clients?filter=favorites", jwt).body()).size());
        assertEquals(1, mapper.readTree(get("/api/v1/clients?search=ana", jwt).body()).size());
        assertEquals(1, mapper.readTree(get("/api/v1/clients?search=99991234", jwt).body()).size());
        assertEquals(0, mapper.readTree(get("/api/v1/clients?filter=recent", jwt).body()).size());
        HttpResponse<String> edit = request("PATCH", "/api/v1/clients/" + id,
                "{\"favorite\":false,\"email\":null,\"photoUrl\":null,\"notes\":null}", jwt);
        assertEquals(200, edit.statusCode(), edit.body());
        JsonNode changed = mapper.readTree(edit.body());
        assertFalse(changed.get("favorite").asBoolean());
        assertTrue(changed.get("email").isNull());
        assertTrue(changed.get("photoUrl").isNull());
        assertTrue(changed.get("notes").isNull());
        assertEquals(0, mapper.readTree(get("/api/v1/clients?filter=favorites", jwt).body()).size());
        assertEquals(200, get("/api/v1/clients/" + id, jwt).statusCode());
    }

    @Test
    void clientsRejectInvalidInputsAndUnknownPatches() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        List<String> bad = List.of(
                "{\"name\":\"   \"}",
                "{\"name\":\"x\",\"phone\":\"123\"}",
                "{\"name\":\"x\",\"phone\":\"31(9)INVALID\"}",
                "{\"name\":\"x\",\"email\":\"bad-email\"}",
                "{\"name\":\"x\",\"birthDate\":\"2099-01-01\"}",
                "{\"name\":\"x\",\"photoUrl\":\"http://images.invalid/a.png\"}",
                "{\"name\":\"x\",\"notes\":\"" + "x".repeat(4001) + "\"}"
        );
        for (String payload : bad) {
            assertEquals(400, request("POST", "/api/v1/clients", payload, jwt).statusCode(),
                    payload.substring(0, Math.min(120, payload.length())));
        }
        HttpResponse<String> created = request("POST", "/api/v1/clients", "{\"name\":\"Test\"}", jwt);
        assertEquals(201, created.statusCode());
        String path = "/api/v1/clients/" + mapper.readTree(created.body()).get("id").asText();
        assertEquals(400, request("PATCH", path, "{}", jwt).statusCode());
        assertEquals(400, request("PATCH", path, "{\"name\":null}", jwt).statusCode());
        assertEquals(400, request("PATCH", path, "{\"favorite\":null}", jwt).statusCode());
        assertEquals(400, request("PATCH", path, "{\"unknown\":123}", jwt).statusCode());
        assertEquals(400, request("PATCH", path, "{\"name\":\" \"}", jwt).statusCode());
        assertEquals(400, request("PATCH", path, "{\"birthDate\":\"bad\"}", jwt).statusCode());
        assertEquals(400, get("/api/v1/clients?filter=unknown", jwt).statusCode());
        assertEquals(400, get("/api/v1/clients?limit=101", jwt).statusCode());
        assertEquals(400, get("/api/v1/clients?offset=-1", jwt).statusCode());
    }

    @Test
    void clientsAreScopedByBarberAndPhoneDuplicatesReturn409() throws Exception {
        Account a = account();
        Account b = account();
        String jwtA = tokenFor(a);
        String jwtB = tokenFor(b);
        String payload = "{\"name\":\"Cliente\",\"phone\":\"(31) 98888-7777\"}";
        HttpResponse<String> created = request("POST", "/api/v1/clients", payload, jwtA);
        assertEquals(201, created.statusCode());
        String id = mapper.readTree(created.body()).get("id").asText();
        String path = "/api/v1/clients/" + id;
        assertEquals(409, request("POST", "/api/v1/clients",
                "{\"name\":\"Duplicado\",\"phone\":\"31988887777\"}", jwtA).statusCode());
        assertEquals(201, request("POST", "/api/v1/clients", payload, jwtB).statusCode());
        assertEquals(0, mapper.readTree(get("/api/v1/clients?search=Cliente", jwtB).body()).size() - 1);
        assertEquals(404, get(path, jwtB).statusCode());
        assertEquals(404, request("PATCH", path, "{\"favorite\":true}", jwtB).statusCode());
        assertEquals(404, request("DELETE", path, null, jwtB).statusCode());
        assertEquals(204, request("DELETE", path, null, jwtA).statusCode());
        assertEquals(404, get(path, jwtA).statusCode());
        assertEquals(404, request("DELETE", path, null, jwtA).statusCode());
        assertEquals(0, mapper.readTree(get("/api/v1/clients", jwtA).body()).size());
    }

    @Test
    void recentClientsCountOnlyCompletedAppointmentsAndSoftDeletePreservesHistory() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        String first = "{\"name\":\"Primeiro\",\"phone\":\"31999998888\",\"favorite\":true}";
        HttpResponse<String> created = request("POST", "/api/v1/clients", first, jwt);
        assertEquals(201, created.statusCode());
        UUID firstId = UUID.fromString(mapper.readTree(created.body()).get("id").asText());
        HttpResponse<String> second = request("POST", "/api/v1/clients",
                "{\"name\":\"Segundo\"}", jwt);
        assertEquals(201, second.statusCode());
        UUID secondId = UUID.fromString(mapper.readTree(second.body()).get("id").asText());
        UUID serviceId = UUID.randomUUID();
        update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price)"
                + " VALUES(?,?,'Corte',30,30)", serviceId, a.barber());
        update("INSERT INTO appointments(barber_id,client_id,service_id,starts_at,ends_at,"
                + "service_name_snapshot,price_snapshot,status,completed_at)"
                + " VALUES(?,?,?,?,?,'Corte',30,'COMPLETED'::appointment_status,now())",
                a.barber(), firstId, serviceId, OffsetDateTime.parse("2025-03-01T13:00:00Z"),
                OffsetDateTime.parse("2025-03-01T13:30:00Z"));
        update("INSERT INTO appointments(barber_id,client_id,service_id,starts_at,ends_at,"
                + "service_name_snapshot,price_snapshot,status)"
                + " VALUES(?,?,?,?,?,'Corte',30,'CANCELLED'::appointment_status)",
                a.barber(), firstId, serviceId, OffsetDateTime.parse("2025-03-02T13:00:00Z"),
                OffsetDateTime.parse("2025-03-02T13:30:00Z"));
        List<String> resultNames = new java.util.ArrayList<>();
        JsonNode recent = mapper.readTree(get("/api/v1/clients?filter=recent", jwt).body());
        for (JsonNode row : recent) {
            resultNames.add(row.get("name").asText());
        }
        assertEquals(List.of("Primeiro"), resultNames);
        JsonNode item = recent.get(0);
        assertEquals(1L, item.get("completedAppointments").asLong());
        assertEquals(Instant.parse("2025-03-01T13:00:00Z"),
                Instant.parse(item.get("lastAppointmentAt").asText()));
        assertEquals(2, mapper.readTree(get("/api/v1/clients?filter=all", jwt).body()).size());
        assertEquals(1, mapper.readTree(get("/api/v1/clients?limit=1", jwt).body()).size());
        assertEquals(1, mapper.readTree(get("/api/v1/clients?offset=1", jwt).body()).size());
        assertEquals(204, request("DELETE", "/api/v1/clients/" + firstId, null, jwt).statusCode());
        assertEquals(0, mapper.readTree(get("/api/v1/clients?filter=recent", jwt).body()).size());
        assertEquals(200, get("/api/v1/clients/" + secondId, jwt).statusCode());
        try (Connection db = database();
                PreparedStatement statement = db.prepareStatement(
                        "SELECT count(*) FROM appointments WHERE barber_id=? AND client_id=?")) {
            statement.setObject(1, a.barber());
            statement.setObject(2, firstId);
            try (var rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Test
    void scheduleHttpRespectsBarberIdentityWorkingHoursAndClosedDays() throws Exception {
        Account a = account();
        Account b = account();
        String owner = tokenFor(a);
        String other = tokenFor(b);
        String friday = "2030-03-01";
        int weekday = java.time.LocalDate.parse(friday).getDayOfWeek().getValue();
        String hours = "{\"enabled\":true,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}";
        assertEquals(401, get("/api/v1/schedule/working-hours", null).statusCode());
        assertEquals(200, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                hours, owner).statusCode());
        assertEquals(1, mapper.readTree(get("/api/v1/schedule/working-hours", owner).body()).size());
        assertEquals(0, mapper.readTree(get("/api/v1/schedule/working-hours", other).body()).size());

        HttpResponse<String> service = request("POST", "/api/v1/services",
                "{\"name\":\"Corte\",\"durationMinutes\":60,\"defaultPrice\":50,\"isHaircut\":true}",
                owner);
        assertEquals(201, service.statusCode(), service.body());
        String id = mapper.readTree(service.body()).get("id").asText();
        String url = "/api/v1/schedule/available-slots?date=" + friday + "&serviceId=" + id;
        JsonNode slots = mapper.readTree(get(url, owner).body());
        assertTrue(slots.size() > 0);
        assertTrue(slots.toString().contains("2030-03-01T13:00:00Z"));
        assertEquals(404, get(url, other).statusCode());
        assertEquals(400, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":true,\"opensAt\":\"18:00\",\"closesAt\":\"08:00\"}", owner)
                .statusCode());
        assertEquals(400, request("PUT", "/api/v1/schedule/working-hours/8",
                hours, owner).statusCode());
        assertEquals(200, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":false,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}", owner)
                .statusCode());
        assertEquals(0, mapper.readTree(get(url, owner).body()).size());
    }

    @Test
    void scheduleHttpOneOffBlocksConflictWithBookingAndCanBeRemoved() throws Exception {
        Account a = account();
        Account b = account();
        String owner = tokenFor(a);
        String other = tokenFor(b);
        int weekday = java.time.LocalDate.parse("2030-03-01").getDayOfWeek().getValue();
        assertEquals(200, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":true,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}", owner)
                .statusCode());
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Client')", client, a.barber());
        update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price) "
                + "VALUES(?,?,'Haircut',60,50)", service, a.barber());
        scheduling.schedule(a.barber(), client, service,
                Instant.parse("2030-03-01T13:00:00Z"), Instant.parse("2030-03-01T14:00:00Z"));
        String overlap = "{\"startsAt\":\"2030-03-01T13:30:00Z\","
                + "\"endsAt\":\"2030-03-01T14:30:00Z\",\"reason\":\"Pausa\"}";
        assertEquals(409, request("POST", "/api/v1/schedule/blocks", overlap, owner).statusCode());
        String valid = "{\"startsAt\":\"2030-03-01T14:00:00Z\","
                + "\"endsAt\":\"2030-03-01T15:00:00Z\","
                + "\"reason\":\"Almoco\",\"notes\":\"Pausa de equipe\"}";
        HttpResponse<String> created = request("POST", "/api/v1/schedule/blocks", valid, owner);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode row = mapper.readTree(created.body());
        String path = "/api/v1/schedule/blocks/" + row.get("id").asText();
        assertEquals("Pausa de equipe", row.get("notes").asText());
        assertEquals(1, mapper.readTree(get("/api/v1/schedule/blocks?date=2030-03-01",
                owner).body()).size());
        assertEquals(0, mapper.readTree(get("/api/v1/schedule/blocks?date=2030-03-01",
                other).body()).size());
        assertEquals(404, request("DELETE", path, null, other).statusCode());
        assertEquals(409, request("POST", "/api/v1/schedule/blocks", valid, owner).statusCode());
        assertEquals(204, request("DELETE", path, null, owner).statusCode());
        assertEquals(404, request("DELETE", path, null, owner).statusCode());
        assertEquals(0, mapper.readTree(get("/api/v1/schedule/blocks?date=2030-03-01",
                owner).body()).size());
    }

    @Test
    void scheduleHttpWeeklyBlocksRemoveSlotsAndRejectOverlaps() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        String date = "2030-03-01";
        int weekday = java.time.LocalDate.parse(date).getDayOfWeek().getValue();
        request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":true,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}", jwt);
        HttpResponse<String> created = request("POST", "/api/v1/services",
                "{\"name\":\"Corte\",\"durationMinutes\":60,\"defaultPrice\":50,\"isHaircut\":true}", jwt);
        assertEquals(201, created.statusCode(), created.body());
        String service = mapper.readTree(created.body()).get("id").asText();
        String url = "/api/v1/schedule/available-slots?date=" + date + "&serviceId=" + service;
        String before = get(url, jwt).body();
        assertTrue(before.contains("2030-03-01T15:00:00Z"));
        String rule = "{\"weekday\":" + weekday + ",\"startsAt\":\"12:00\","
                + "\"endsAt\":\"13:00\",\"validFrom\":\"2030-03-01\","
                + "\"validUntil\":\"2030-05-31\",\"reason\":\"Pausa semanal\"}";
        HttpResponse<String> block = request("POST", "/api/v1/schedule/recurring-blocks", rule, jwt);
        assertEquals(201, block.statusCode(), block.body());
        String id = mapper.readTree(block.body()).get("id").asText();
        assertFalse(mapper.readTree(get(url, jwt).body()).findValuesAsText("startsAt")
                .contains("2030-03-01T15:00:00Z"));
        assertEquals(1, mapper.readTree(get("/api/v1/schedule/recurring-blocks", jwt).body()).size());
        assertEquals(409, request("POST", "/api/v1/schedule/recurring-blocks", rule, jwt)
                .statusCode());
        assertEquals(409, request("POST", "/api/v1/schedule/blocks",
                "{\"startsAt\":\"2030-03-01T15:00:00Z\","
                        + "\"endsAt\":\"2030-03-01T16:00:00Z\"}", jwt).statusCode());
        assertEquals(400, request("POST", "/api/v1/schedule/recurring-blocks",
                "{\"weekday\":" + weekday + ",\"startsAt\":\"14:00\","
                        + "\"endsAt\":\"12:00\",\"validFrom\":\"2030-03-01\","
                        + "\"validUntil\":\"2030-05-31\"}", jwt).statusCode());
        assertEquals(204, request("DELETE", "/api/v1/schedule/recurring-blocks/" + id,
                null, jwt).statusCode());
        assertTrue(get(url, jwt).body().contains("2030-03-01T15:00:00Z"));
    }

    @Test
    void changingWorkHoursCannotInvalidateActiveReservation() throws Exception {
        Account a = account();
        String jwt = tokenFor(a);
        int weekday = java.time.LocalDate.parse("2030-03-01").getDayOfWeek().getValue();
        assertEquals(200, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":true,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}", jwt)
                .statusCode());
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Client')", client, a.barber());
        update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price) "
                + "VALUES(?,?,'Haircut',60,50)", service, a.barber());
        scheduling.schedule(a.barber(), client, service,
                Instant.parse("2030-03-01T13:00:00Z"), Instant.parse("2030-03-01T14:00:00Z"));
        assertEquals(409, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":false,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}", jwt)
                .statusCode());
        assertEquals(409, request("PUT", "/api/v1/schedule/working-hours/" + weekday,
                "{\"enabled\":true,\"opensAt\":\"11:00\",\"closesAt\":\"18:00\"}", jwt)
                .statusCode());
        assertEquals(1, mapper.readTree(get("/api/v1/schedule/working-hours",
                jwt).body()).size());
    }


    @Test
    void appointmentsCreateIsIdempotentAndPreservesServiceSnapshot() throws Exception {
        BookingFixture a = bookingFixture();
        String key = "booking-" + UUID.randomUUID();
        String body = bookingBody(a.client(), a.service(), "2030-03-01T13:00:00Z");
        assertEquals(400, request("POST", "/api/v1/appointments", body, a.jwt()).statusCode());
        assertEquals(401, book(body, null, key).statusCode());
        HttpResponse<String> created = book(body, a.jwt(), key);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode initial = mapper.readTree(created.body());
        String id = initial.get("id").asText();
        assertEquals("SCHEDULED", initial.get("status").asText());
        assertEquals("Cliente Teste", initial.get("clientName").asText());
        assertEquals("31999998888", initial.get("clientPhone").asText());
        assertEquals("Corte Inicial", initial.get("serviceName").asText());
        assertEquals(60, initial.get("durationMinutes").asInt());
        assertEquals(0, initial.get("price").decimalValue().compareTo(new BigDecimal("47.50")));
        assertEquals(201, book(body, a.jwt(), key).statusCode());
        assertEquals(id, mapper.readTree(book(body, a.jwt(), key).body()).get("id").asText());
        assertEquals(409, book(body.replace("13:00", "14:00"), a.jwt(), key).statusCode());
        assertEquals(409, book(body, a.jwt(), "different-" + UUID.randomUUID()).statusCode());
        assertEquals(1, mapper.readTree(get("/api/v1/appointments/" + id + "/history",
                a.jwt()).body()).size());
        update("UPDATE services SET name='Corte Modificado', default_price=90 WHERE id=?", a.service());
        JsonNode persisted = mapper.readTree(get("/api/v1/appointments/" + id, a.jwt()).body());
        assertEquals("Corte Inicial", persisted.get("serviceName").asText());
        assertEquals(0, persisted.get("price").decimalValue().compareTo(new BigDecimal("47.50")));
        assertEquals(1, countAppointments(a.barber()));
        assertEquals(1, countBySql("SELECT count(*) FROM idempotency_records WHERE "
                + "resource_id=?", UUID.fromString(id)));
    }

    @Test
    void appointmentsEditRescheduleAndTransitionsGenerateHistoryAtomically() throws Exception {
        BookingFixture a = bookingFixture();
        String body = bookingBody(a.client(), a.service(), "2030-03-01T13:00:00Z");
        HttpResponse<String> created = book(body, a.jwt(), "editing-" + UUID.randomUUID());
        assertEquals(201, created.statusCode(), created.body());
        String id = mapper.readTree(created.body()).get("id").asText();
        String path = "/api/v1/appointments/" + id;
        assertEquals(400, request("PATCH", path, "{}", a.jwt()).statusCode());
        assertEquals(400, request("PATCH", path, "{\"price\":-1}", a.jwt()).statusCode());
        assertEquals(400, request("PATCH", path, "{\"badField\":true}", a.jwt()).statusCode());
        HttpResponse<String> edit = request("PATCH", path,
                "{\"startsAt\":\"2030-03-01T14:00:00Z\",\"price\":55.50,"
                        + "\"notes\":\"Cliente pediu corte curto\"}", a.jwt());
        assertEquals(200, edit.statusCode(), edit.body());
        JsonNode edited = mapper.readTree(edit.body());
        assertEquals("2030-03-01T14:00:00Z", edited.get("startsAt").asText());
        assertEquals("2030-03-01T15:00:00Z", edited.get("endsAt").asText());
        assertEquals(0, edited.get("price").decimalValue().compareTo(new BigDecimal("55.50")));
        assertEquals(1, edited.get("version").asInt());
        assertEquals(200, request("POST", path + "/start", null, a.jwt()).statusCode());
        assertEquals(200, request("POST", path + "/start", null, a.jwt()).statusCode());
        assertEquals(409, request("PATCH", path, "{\"notes\":\"too late\"}", a.jwt()).statusCode());
        assertEquals(200, request("POST", path + "/complete", null, a.jwt()).statusCode());
        JsonNode completed = mapper.readTree(get(path, a.jwt()).body());
        assertEquals("COMPLETED", completed.get("status").asText());
        assertFalse(completed.get("completedAt").isNull());
        assertEquals(200, request("POST", path + "/complete", null, a.jwt()).statusCode());
        assertEquals(409, request("POST", path + "/cancel", null, a.jwt()).statusCode());
        JsonNode history = mapper.readTree(get(path + "/history", a.jwt()).body());
        assertEquals(4, history.size()); // create, edit, start, complete (no duplicate events)
        assertEquals("CREATED", history.get(0).get("eventType").asText());
        assertEquals("COMPLETED", history.get(3).get("newStatus").asText());
        JsonNode client = mapper.readTree(get("/api/v1/clients/" + a.client(), a.jwt()).body());
        assertEquals(1, client.get("completedAppointments").asInt());
    }

    @Test
    void appointmentsListScopesByBarberDateStatusAndSupportsCancellation() throws Exception {
        BookingFixture a = bookingFixture();
        BookingFixture b = bookingFixture();
        String body = bookingBody(a.client(), a.service(), "2030-03-01T13:00:00Z");
        HttpResponse<String> created = book(body, a.jwt(), "listing-" + UUID.randomUUID());
        assertEquals(201, created.statusCode(), created.body());
        String id = mapper.readTree(created.body()).get("id").asText();
        String path = "/api/v1/appointments?date=2030-03-01";
        assertEquals(1, mapper.readTree(get(path + "&view=day", a.jwt()).body()).size());
        assertEquals(1, mapper.readTree(get(path + "&view=week", a.jwt()).body()).size());
        assertEquals(1, mapper.readTree(get(path + "&view=month", a.jwt()).body()).size());
        assertEquals(0, mapper.readTree(get(path, b.jwt()).body()).size());
        assertEquals(404, get("/api/v1/appointments/" + id, b.jwt()).statusCode());
        assertEquals(404, get("/api/v1/appointments/" + id + "/history", b.jwt()).statusCode());
        assertEquals(404, request("POST", "/api/v1/appointments/" + id + "/start",
                null, b.jwt()).statusCode());
        assertEquals(400, get(path + "&view=year", a.jwt()).statusCode());
        assertEquals(400, get(path + "&status=INVALID", a.jwt()).statusCode());
        assertEquals(400, get(path + "&limit=501", a.jwt()).statusCode());
        assertEquals(400, get("/api/v1/appointments", a.jwt()).statusCode());
        assertEquals(200, request("POST", "/api/v1/appointments/" + id + "/cancel",
                null, a.jwt()).statusCode());
        assertEquals(0, mapper.readTree(get(path + "&status=SCHEDULED",
                a.jwt()).body()).size());
        assertEquals(1, mapper.readTree(get(path + "&status=CANCELLED",
                a.jwt()).body()).size());
        assertEquals(409, request("POST", "/api/v1/appointments/" + id + "/complete",
                null, a.jwt()).statusCode());
    }

    @Test
    void appointmentsRejectClosedDayAndBlockedTimeAndCrossTenantSelections() throws Exception {
        BookingFixture a = bookingFixture();
        BookingFixture b = bookingFixture();
        String other = bookingBody(b.client(), a.service(), "2030-03-01T13:00:00Z");
        assertEquals(404, book(other, a.jwt(), "foreign-" + UUID.randomUUID()).statusCode());
        String body = bookingBody(a.client(), a.service(), "2030-03-01T13:00:00Z");
        assertEquals(201, book(body, a.jwt(), "first-" + UUID.randomUUID()).statusCode());
        assertEquals(409, book(body, a.jwt(), "overlap-" + UUID.randomUUID()).statusCode());
        String blocked = bookingBody(a.client(), a.service(), "2030-03-01T15:00:00Z");
        String block = "{\"startsAt\":\"2030-03-01T15:00:00Z\","
                + "\"endsAt\":\"2030-03-01T16:00:00Z\",\"reason\":\"Pausa\"}";
        assertEquals(201, request("POST", "/api/v1/schedule/blocks", block, a.jwt()).statusCode());
        assertEquals(409, book(blocked, a.jwt(), "blocked-" + UUID.randomUUID()).statusCode());
        assertEquals(409, request("PATCH", "/api/v1/appointments/"
                + appointmentIdByBarber(a.barber()), "{\"startsAt\":\"2030-03-01T15:00:00Z\"}",
                a.jwt()).statusCode());
        String closed = bookingBody(a.client(), a.service(), "2030-03-03T13:00:00Z");
        assertEquals(409, book(closed, a.jwt(), "closed-" + UUID.randomUUID()).statusCode());
    }

    @Test
    void concurrentIdenticalAppointmentRequestsCommitOnlyOneBooking() throws Exception {
        BookingFixture a = bookingFixture();
        String body = bookingBody(a.client(), a.service(), "2030-03-01T13:00:00Z");
        String key = "race-" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<String>> one = workers.submit(() -> {
                assertTrue(latch.await(10, TimeUnit.SECONDS));
                return book(body, a.jwt(), key);
            });
            Future<HttpResponse<String>> two = workers.submit(() -> {
                assertTrue(latch.await(10, TimeUnit.SECONDS));
                return book(body, a.jwt(), key);
            });
            latch.countDown();
            HttpResponse<String> first = one.get(60, TimeUnit.SECONDS);
            HttpResponse<String> second = two.get(60, TimeUnit.SECONDS);
            assertEquals(201, first.statusCode(), first.body());
            assertEquals(201, second.statusCode(), second.body());
            assertEquals(mapper.readTree(first.body()).get("id").asText(),
                    mapper.readTree(second.body()).get("id").asText());
        }
        assertEquals(1, countAppointments(a.barber()));
    }

    @Test
    void simultaneousDifferentKeysCannotReserveOverlappingAppointments() throws Exception {
        BookingFixture a = bookingFixture();
        String body = bookingBody(a.client(), a.service(), "2030-03-01T13:00:00Z");
        CountDownLatch latch = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<String>> one = workers.submit(() -> {
                assertTrue(latch.await(10, TimeUnit.SECONDS));
                return book(body, a.jwt(), "first-" + UUID.randomUUID());
            });
            Future<HttpResponse<String>> two = workers.submit(() -> {
                assertTrue(latch.await(10, TimeUnit.SECONDS));
                return book(body, a.jwt(), "second-" + UUID.randomUUID());
            });
            latch.countDown();
            HttpResponse<String> first = one.get(60, TimeUnit.SECONDS);
            HttpResponse<String> second = two.get(60, TimeUnit.SECONDS);
            assertEquals(1, List.of(first, second).stream()
                    .filter(response -> response.statusCode() == 201).count());
            assertEquals(1, List.of(first, second).stream()
                    .filter(response -> response.statusCode() == 409).count());
        }
        assertEquals(1, countAppointments(a.barber()));
    }

    private BookingFixture bookingFixture() throws Exception {
        Account account = account();
        String token = tokenFor(account);
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        update("INSERT INTO clients(id,barber_id,name,phone) "
                + "VALUES(?,?,'Cliente Teste','31999998888')", client, account.barber());
        update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price) "
                + "VALUES(?,?,'Corte Inicial',60,45)", service, account.barber());
        update("INSERT INTO working_hours(barber_id,weekday,opens_at,closes_at) "
                + "VALUES(?,5,TIME '08:00',TIME '18:00')", account.barber());
        return new BookingFixture(account.barber(), client, service, token);
    }

    private String bookingBody(UUID client, UUID service, String start) {
        return "{\"clientId\":\"" + client + "\",\"serviceId\":\"" + service + "\","
                + "\"startsAt\":\"" + start + "\",\"price\":47.50,\"notes\":\"Observacao\"}";
    }

    private HttpResponse<String> book(String body, String token, String key) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/appointments"))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        builder.POST(HttpRequest.BodyPublishers.ofString(body));
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private int countAppointments(UUID barber) throws Exception {
        return countBySql("SELECT count(*) FROM appointments WHERE barber_id=?", barber);
    }

    private int countBySql(String sql, UUID id) throws Exception {
        try (Connection db = database();
                PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String appointmentIdByBarber(UUID barber) throws Exception {
        try (Connection db = database();
                PreparedStatement statement = db.prepareStatement(
                        "SELECT id FROM appointments WHERE barber_id=?")) {
            statement.setObject(1, barber);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class).toString();
            }
        }
    }

    private record BookingFixture(UUID barber, UUID client, UUID service, String jwt) {
    }

    private Account account() throws Exception {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        String email = user + "@tests.invalid";
        update("INSERT INTO users(id,email,password_hash,role,active) VALUES(?,?,?,'BARBER',true)",
                user, email, passwordEncoder.encode(PASSWORD));
        update("INSERT INTO barbers(id,user_id) VALUES(?,?)", barber, user);
        return new Account(user, barber, email);
    }

    private String tokenFor(Account a) throws Exception {
        HttpResponse<String> response = login(a.email(), PASSWORD);
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body()).get("accessToken").asText();
    }

    private String forge(Account a, String issuer, String audience, Instant expiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .issuedAt(Instant.now().minusSeconds(180)).expiresAt(expiry)
                .id(UUID.randomUUID().toString()).subject(a.user().toString())
                .claim("barber_id", a.barber().toString()).claim("role", "BARBER").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return request("POST", "/api/v1/auth/login",
                mapper.writeValueAsString(Map.of("email", email, "password", password)), null);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return request("GET", path, null, token);
    }

    private HttpResponse<String> request(String method, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static Connection database() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
    }

    private static void update(String sql, Object... parameters) throws Exception {
        try (Connection db = database(); PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    private static KeyPair createKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException("Test-only RSA generation failed");
        }
    }

    private static String pem(String name, byte[] bytes) {
        String value = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes);
        return "-----BEGIN " + name + "-----\n" + value + "\n-----END " + name + "-----";
    }

    private record Account(UUID user, UUID barber, String email) {
    }

    @TestConfiguration
    static class ScopeProbeConfig {
        @Bean
        ScopeProbeController scopeProbeController() {
            return new ScopeProbeController();
        }

        @Bean
        @Primary
        LoginRateLimiter isolatedLoginLimiter(java.time.Clock clock) {
            // Keep the per-account limiter real, but isolate request IP counters
            // by account; otherwise unrelated test methods collectively trip 429.
            return new LoginRateLimiter(clock) {
                @Override
                public synchronized void check(String email, String address) {
                    super.check(email, email + "|" + address);
                }
            };
        }
    }

    @RestController
    public static class ScopeProbeController {
        @GetMapping("/__tests/scoped/{barberId}")
        @PreAuthorize("@barberAccess.owns(authentication, #barberId)")
        public Map<String, String> scope(@PathVariable("barberId") @P("barberId") UUID barberId) {
            return Map.of("status", "ok");
        }
    }
}
