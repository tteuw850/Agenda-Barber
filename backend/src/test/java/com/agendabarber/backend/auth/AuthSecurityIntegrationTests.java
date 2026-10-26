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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @Value("$" + "{local.server.port}")
    private int port;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

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
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
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
