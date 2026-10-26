package com.agendabarber.backend.scheduling;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AgendaSchedulingConcurrencyTests {
    private static final Instant START = Instant.parse("2030-03-01T13:00:00Z");
    private static final Instant END = Instant.parse("2030-03-01T14:00:00Z");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_scheduling_test")
            .withUsername("test_only")
            .withPassword("test_only");

    private static AgendaSchedulingService scheduling;

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        scheduling = new AgendaSchedulingService(
                new DriverManagerDataSource(postgres.getJdbcUrl(),
                        postgres.getUsername(), postgres.getPassword()));
    }

    @Test
    void concurrentAppointmentsForSameBarberYieldOnlyOneCommit() throws Exception {
        Fixture f = fixture("America/Sao_Paulo");
        List<String> results = race(
                () -> scheduling.schedule(f.barber(), f.client(), f.service(), START, END),
                () -> scheduling.schedule(f.barber(), f.client(), f.service(), START, END));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("APPOINTMENT_OVERLAP"::equals).count());
        assertEquals(1, count("SELECT count(*) FROM appointments WHERE barber_id=?", f.barber()));
    }

    @Test
    void appointmentAndBlockedPeriodRaceYieldOnlyOneCommit() throws Exception {
        Fixture f = fixture("America/Sao_Paulo");
        List<String> results = race(
                () -> scheduling.schedule(f.barber(), f.client(), f.service(), START, END),
                () -> scheduling.block(f.barber(), START, END, "Lunch"));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter(x -> !x.equals("OK")).count());
        assertEquals(1, count("SELECT "
                + "(SELECT count(*) FROM appointments WHERE barber_id=?) + "
                + "(SELECT count(*) FROM blocked_periods WHERE barber_id=?)",
                f.barber(), f.barber()));
    }

    @Test
    void twoConcurrentBlocksCannotOccupyTheSameSlot() throws Exception {
        Fixture f = fixture("America/Sao_Paulo");
        List<String> results = race(
                () -> scheduling.block(f.barber(), START, END, "Lunch"),
                () -> scheduling.block(f.barber(), START, END, "Holiday"));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("BLOCKED_PERIOD_OVERLAP"::equals).count());
        assertEquals(1, count("SELECT count(*) FROM blocked_periods WHERE barber_id=?", f.barber()));
    }

    @Test
    void existingAppointmentPreventsBlockAndExistingBlockPreventsAppointment() throws Exception {
        Fixture a = fixture("America/Sao_Paulo");
        scheduling.schedule(a.barber(), a.client(), a.service(), START, END);
        var occupied = assertThrows(AgendaSchedulingService.ScheduleConflictException.class,
                () -> scheduling.block(a.barber(), START, END, "Lunch"));
        assertEquals(AgendaSchedulingService.ConflictType.APPOINTMENT_OVERLAP, occupied.type());
        assertEquals(0, count("SELECT count(*) FROM blocked_periods WHERE barber_id=?", a.barber()));

        Fixture b = fixture("America/Sao_Paulo");
        scheduling.block(b.barber(), START, END, "Unavailable");
        var blocked = assertThrows(AgendaSchedulingService.ScheduleConflictException.class,
                () -> scheduling.schedule(b.barber(), b.client(), b.service(), START, END));
        assertEquals(AgendaSchedulingService.ConflictType.BLOCKED_PERIOD_OVERLAP, blocked.type());
        assertEquals(0, count("SELECT count(*) FROM appointments WHERE barber_id=?", b.barber()));
    }

    @Test
    void rejectsOutsideHoursAndClosedDaysUsingBarberTimeZone() throws Exception {
        Fixture f = fixture("America/Sao_Paulo");
        var outside = assertThrows(AgendaSchedulingService.ScheduleConflictException.class,
                () -> scheduling.schedule(f.barber(), f.client(), f.service(),
                        Instant.parse("2030-03-01T10:00:00Z"),
                        Instant.parse("2030-03-01T11:00:00Z")));
        assertEquals(AgendaSchedulingService.ConflictType.OUTSIDE_WORKING_HOURS, outside.type());

        execute("UPDATE working_hours SET enabled=false WHERE barber_id=?", f.barber());
        var closed = assertThrows(AgendaSchedulingService.ScheduleConflictException.class,
                () -> scheduling.schedule(f.barber(), f.client(), f.service(), START, END));
        assertEquals(AgendaSchedulingService.ConflictType.OUTSIDE_WORKING_HOURS, closed.type());
        assertEquals(0, count("SELECT count(*) FROM appointments WHERE barber_id=?", f.barber()));
    }

    @Test
    void adjacentSlotsAndSeparateBarbersAreValid() throws Exception {
        Fixture a = fixture("America/Sao_Paulo");
        Fixture b = fixture("America/Sao_Paulo");
        scheduling.schedule(a.barber(), a.client(), a.service(), START, END);
        scheduling.schedule(a.barber(), a.client(), a.service(), END, END.plusSeconds(3600));
        scheduling.schedule(b.barber(), b.client(), b.service(), START, END);
        assertEquals(2, count("SELECT count(*) FROM appointments WHERE barber_id=?", a.barber()));
        assertEquals(1, count("SELECT count(*) FROM appointments WHERE barber_id=?", b.barber()));
    }

    @Test
    void inactiveAppointmentStatusesReleaseTheSlotAndActiveStatusesDoNot() throws Exception {
        Fixture f = fixture("America/Sao_Paulo");
        UUID first = scheduling.schedule(f.barber(), f.client(), f.service(), START, END);
        execute("UPDATE appointments SET status='IN_PROGRESS' WHERE id=?", first);
        var active = assertThrows(AgendaSchedulingService.ScheduleConflictException.class,
                () -> scheduling.schedule(f.barber(), f.client(), f.service(), START, END));
        assertEquals(AgendaSchedulingService.ConflictType.APPOINTMENT_OVERLAP, active.type());
        execute("UPDATE appointments SET status='CANCELLED' WHERE id=?", first);
        UUID second = scheduling.schedule(f.barber(), f.client(), f.service(), START, END);
        execute("UPDATE appointments SET status='NO_SHOW' WHERE id=?", second);
        UUID third = scheduling.schedule(f.barber(), f.client(), f.service(), START, END);
        execute("UPDATE appointments SET status='COMPLETED', completed_at=now() WHERE id=?", third);
        scheduling.schedule(f.barber(), f.client(), f.service(), START, END);
        assertEquals(4, count("SELECT count(*) FROM appointments WHERE barber_id=?", f.barber()));
    }

    @Test
    void failedCrossBarberRequestDoesNotPartiallyWrite() throws Exception {
        Fixture a = fixture("America/Sao_Paulo");
        Fixture b = fixture("America/Sao_Paulo");
        assertThrows(IllegalArgumentException.class,
                () -> scheduling.schedule(a.barber(), b.client(), a.service(), START, END));
        assertEquals(0, count("SELECT count(*) FROM appointments WHERE barber_id=?", a.barber()));
    }

    @Test
    void rawConcurrentTransactionsProveDatabaseExclusionRule() throws Exception {
        Fixture f = fixture("America/Sao_Paulo");
        CountDownLatch begin = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> rawInsert(f, begin));
            Future<String> second = executor.submit(() -> rawInsert(f, begin));
            begin.countDown();
            List<String> outcomes = List.of(first.get(25, TimeUnit.SECONDS),
                    second.get(25, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("OK"::equals).count());
            // Raw concurrent GiST inserts may fail with exclusion violation or deadlock;
            // either must roll back, and only one transaction may commit.
            assertEquals(1, outcomes.stream()
                    .filter(state -> state.equals("23P01") || state.equals("40P01"))
                    .count(), outcomes.toString());
            assertEquals(1, count("SELECT count(*) FROM appointments WHERE barber_id=?", f.barber()));
        }
    }

    private static String rawInsert(Fixture f, CountDownLatch begin) throws Exception {
        assertTrue(begin.await(10, TimeUnit.SECONDS));
        try (Connection connection = db()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO appointments(id,barber_id,client_id,service_id,starts_at,ends_at,"
                            + "service_name_snapshot,price_snapshot,status) "
                            + "VALUES(?,?,?,?,?,?,'Haircut',50,'SCHEDULED'::appointment_status)")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, f.barber());
                statement.setObject(3, f.client());
                statement.setObject(4, f.service());
                statement.setObject(5, OffsetDateTime.ofInstant(START, ZoneOffset.UTC));
                statement.setObject(6, OffsetDateTime.ofInstant(END, ZoneOffset.UTC));
                statement.executeUpdate();
                connection.commit();
                return "OK";
            } catch (SQLException e) {
                connection.rollback();
                return e.getSQLState();
            }
        }
    }

    private static List<String> race(Callable<UUID> firstTask, Callable<UUID> secondTask)
            throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> attempt(go, firstTask));
            Future<String> second = executor.submit(() -> attempt(go, secondTask));
            go.countDown();
            return List.of(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS));
        }
    }

    private static String attempt(CountDownLatch go, Callable<UUID> operation) throws Exception {
        assertTrue(go.await(10, TimeUnit.SECONDS));
        try {
            operation.call();
            return "OK";
        } catch (AgendaSchedulingService.ScheduleConflictException error) {
            return error.type().name();
        }
    }

    private static Fixture fixture(String timezone) throws SQLException {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        execute("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
                user, user + "@tests.invalid", "test-hash-only-not-a-real-secret");
        execute("INSERT INTO barbers(id,user_id,timezone) VALUES(?,?,?)", barber, user, timezone);
        execute("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Client')", client, barber);
        execute("INSERT INTO services(id,barber_id,name,duration_minutes,default_price) "
                + "VALUES(?,?,'Haircut',60,?)", service, barber, new BigDecimal("50.00"));
        for (int day = 1; day <= 7; day++) {
            execute("INSERT INTO working_hours(barber_id,weekday,opens_at,closes_at) "
                    + "VALUES(?,?,TIME '08:00',TIME '18:00')", barber, day);
        }
        return new Fixture(barber, client, service);
    }

    private static void execute(String sql, Object... args) throws SQLException {
        try (Connection connection = db();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private static int count(String sql, Object... args) throws SQLException {
        try (Connection connection = db();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static Connection db() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
    }

    private record Fixture(UUID barber, UUID client, UUID service) {
    }
}
