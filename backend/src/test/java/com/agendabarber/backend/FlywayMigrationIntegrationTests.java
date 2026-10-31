package com.agendabarber.backend;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class FlywayMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_barber_test")
            .withUsername("test_only")
            .withPassword("test_only");

    private static Flyway flyway;

    @BeforeAll
    static void migrateEmptyDatabase() {
        flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(8, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }

    @Test
    void migratesNineTablesAndValidatesChecksum() throws SQLException {
        assertEquals(9, count(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('users','barbers','clients','services','appointments','blocked_periods',"
                        + "'working_hours','expenses','idempotency_records')"));
        assertEquals(8, count("SELECT count(*) FROM flyway_schema_history WHERE success"));
        assertEquals(1, count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name='auth_refresh_tokens'"));
        assertEquals(1, count("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'ex_appointments_barber_active_overlap'"));
        flyway.validate();
    }

    @Test
    void usernameMigrationRequiresValidUniqueUsernamesAndPreservesExistingAccounts()
            throws SQLException {
        Fixture first = fixture();
        Fixture second = fixture();
        assertEquals(2, count("SELECT count(*) FROM users WHERE id IN ('"
                + first.userId() + "','" + second.userId() + "') AND username IS NULL"));
        execute("UPDATE users SET username='ebinho' WHERE id=?", first.userId());
        SQLException duplicate = assertThrows(SQLException.class, () ->
                execute("UPDATE users SET username='ebinho' WHERE id=?", second.userId()));
        assertEquals("23505", duplicate.getSQLState());
        SQLException invalid = assertThrows(SQLException.class, () ->
                execute("UPDATE users SET username='invalid username' WHERE id=?", second.userId()));
        assertEquals("23514", invalid.getSQLState());
        assertEquals(1, count("SELECT count(*) FROM users WHERE username='ebinho'"));
    }

    @Test
    void rejectsActiveOverlapsButAllowsAdjacentSlots() throws SQLException {
        Fixture f = fixture();
        insertAppointment(f, f.clientId(), f.serviceId(), "2030-03-01T10:00:00Z",
                "2030-03-01T11:00:00Z", "SCHEDULED");
        SQLException overlap = assertThrows(SQLException.class,
                () -> insertAppointment(f, f.clientId(), f.serviceId(),
                        "2030-03-01T10:30:00Z", "2030-03-01T11:30:00Z", "IN_PROGRESS"));
        assertEquals("23P01", overlap.getSQLState());
        insertAppointment(f, f.clientId(), f.serviceId(), "2030-03-01T11:00:00Z",
                "2030-03-01T12:00:00Z", "SCHEDULED");
        insertAppointment(f, f.clientId(), f.serviceId(), "2030-03-01T10:30:00Z",
                "2030-03-01T11:30:00Z", "CANCELLED");
    }

    @Test
    void rejectsCrossBarberClientAndServiceReferences() throws SQLException {
        Fixture a = fixture();
        Fixture b = fixture();
        SQLException foreignKey = assertThrows(SQLException.class,
                () -> insertAppointment(a, b.clientId(), a.serviceId(), "2030-03-02T10:00:00Z",
                        "2030-03-02T11:00:00Z", "SCHEDULED"));
        assertEquals("23503", foreignKey.getSQLState());
        SQLException serviceKey = assertThrows(SQLException.class,
                () -> insertAppointment(a, a.clientId(), b.serviceId(), "2030-03-02T10:00:00Z",
                        "2030-03-02T11:00:00Z", "SCHEDULED"));
        assertEquals("23503", serviceKey.getSQLState());
    }

    @Test
    void rejectsInvalidDurationAndNegativeAmounts() throws SQLException {
        Fixture f = fixture();
        SQLException duration = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO services(id, barber_id, name, duration_minutes, default_price) "
                        + "VALUES(?, ?, 'Invalid', 0, 10)",
                UUID.randomUUID(), f.barberId()));
        assertEquals("23514", duration.getSQLState());
        SQLException amount = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO expenses(id, barber_id, occurred_on, description, amount, category) "
                        + "VALUES(?, ?, DATE '2030-01-01', 'Rent', -10, 'OVERHEAD')",
                UUID.randomUUID(), f.barberId()));
        assertEquals("23514", amount.getSQLState());
    }

    @Test
    void rejectsDuplicateIdempotencyKeyPerUser() throws SQLException {
        Fixture f = fixture();
        String statement = "INSERT INTO idempotency_records"
                + "(id, user_id, idempotency_key, request_hash, operation) "
                + "VALUES(?, ?, 'req-123', ?, 'CREATE_APPOINTMENT')";
        execute(statement, UUID.randomUUID(), f.userId(), "a".repeat(64));
        SQLException duplicate = assertThrows(SQLException.class, () ->
                execute(statement, UUID.randomUUID(), f.userId(), "a".repeat(64)));
        assertEquals("23505", duplicate.getSQLState());
    }

    @Test
    void maintainsAuditTimestamps() throws SQLException {
        Fixture f = fixture();
        execute("UPDATE services SET name='Updated' WHERE id=?", f.serviceId());
        assertEquals(1, count("SELECT count(*) FROM services WHERE id='"
                + f.serviceId() + "' AND updated_at >= created_at"));
    }

    @Test
    void v3IconKeyDefaultsAndRejectsUnknownVisuals() throws SQLException {
        Fixture f = fixture();
        assertEquals(1, count("SELECT count(*) FROM services WHERE id='" + f.serviceId()
                + "' AND icon_key='SCISSORS'"));
        SQLException invalid = assertThrows(SQLException.class,
                () -> execute("UPDATE services SET icon_key='WRONG' WHERE id=?", f.serviceId()));
        assertEquals("23514", invalid.getSQLState());
    }


    @Test
    void clientCatalogMigrationAddsPhotoArchiveAndUniquePhoneConstraint() throws SQLException {
        Fixture f = fixture();
        assertEquals(1, count("SELECT count(*) FROM clients WHERE id='" + f.clientId()
                + "' AND active=true AND photo_url IS NULL"));
        execute("UPDATE clients SET photo_url='https://img.example.test/avatar.png' WHERE id=?",
                f.clientId());
        SQLException badPhoto = assertThrows(SQLException.class, () ->
                execute("UPDATE clients SET photo_url='http://insecure.test/avatar.png' WHERE id=?",
                        f.clientId()));
        assertEquals("23514", badPhoto.getSQLState());
        execute("UPDATE clients SET phone='31999998888' WHERE id=?", f.clientId());
        SQLException duplicate = assertThrows(SQLException.class, () ->
                execute("INSERT INTO clients(barber_id,name,phone) VALUES(?, 'Duplicate', '31999998888')",
                        f.barberId()));
        assertEquals("23505", duplicate.getSQLState());
    }

    @Test
    void v5AddsRecurringBlocksAndSingleBlockNotesWithoutChangingEarlierVersions() throws SQLException {
        assertEquals(1, count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name='recurring_blocks'"));
        assertEquals(1, count("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name='blocked_periods' AND column_name='notes'"));
        assertEquals(8, count("SELECT count(*) FROM flyway_schema_history WHERE success"));
    }


    @Test
    void v6TracksAppointmentHistoryAndPreservesCompositeTenantConstraint() throws SQLException {
        assertEquals(1, count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name='appointment_events'"));
        assertEquals(1, count("SELECT count(*) FROM pg_constraint "
                + "WHERE conname='uq_appointments_barber_id'"));
    }

    private static Fixture fixture() throws SQLException {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        execute("INSERT INTO users(id, email, password_hash, role) "
                + "VALUES(?, ?, 'not-a-real-password-hash-for-tests', 'BARBER')",
                user, user + "@tests.invalid");
        execute("INSERT INTO barbers(id, user_id) VALUES(?, ?)", barber, user);
        execute("INSERT INTO clients(id, barber_id, name) VALUES(?, ?, 'Sample Client')", client, barber);
        execute("INSERT INTO services(id, barber_id, name, duration_minutes, default_price) "
                + "VALUES(?, ?, 'Haircut', 60, 50.00)", service, barber);
        return new Fixture(user, barber, client, service);
    }

    private static void insertAppointment(Fixture f, UUID client, UUID service,
            String start, String end, String status) throws SQLException {
        execute("INSERT INTO appointments"
                + "(id, barber_id, client_id, service_id, starts_at, ends_at, "
                + "service_name_snapshot, price_snapshot, status) "
                + "VALUES(?, ?, ?, ?, ?, ?, 'Haircut', ?, ?::appointment_status)",
                UUID.randomUUID(), f.barberId(), client, service,
                OffsetDateTime.parse(start), OffsetDateTime.parse(end),
                new BigDecimal("50.00"), status);
    }

    private static void execute(String sql, Object... arguments) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
            statement.executeUpdate();
        }
    }

    private static int count(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            assertTrue(results.next());
            return results.getInt(1);
        }
    }

    private record Fixture(UUID userId, UUID barberId, UUID clientId, UUID serviceId) {
    }
}
