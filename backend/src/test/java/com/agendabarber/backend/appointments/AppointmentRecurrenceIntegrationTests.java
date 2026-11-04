package com.agendabarber.backend.appointments;

import com.agendabarber.backend.appointments.AppointmentRecurrenceService.Request;
import com.agendabarber.backend.appointments.AppointmentRecurrenceService.Series;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AppointmentRecurrenceIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_weekly_bookings")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static AppointmentRecurrenceService recurrences;
    private static TransactionTemplate tx;

    @BeforeAll
    static void prepare() {
        DataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(9, Flyway.configure().dataSource(data)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted);
        jdbc = new JdbcTemplate(data);
        recurrences = new AppointmentRecurrenceService(jdbc);
        tx = new TransactionTemplate(new DataSourceTransactionManager(data));
    }

    @Test
    void previewsAndBooksThursdayAndFridayWithoutDuplicates() {
        Owner owner = owner();
        Request request = request(owner);
        var preview = recurrences.preview(owner.barber(), request);
        assertEquals(8, preview.available());
        assertEquals(0, preview.unavailable());
        String key = UUID.randomUUID().toString();
        Series result = tx.execute(status -> recurrences.create(
                owner.barber(), owner.user(), key, request));
        assertEquals(8, result.totalAppointments());
        assertEquals(8, result.scheduledAppointments());
        Series replay = tx.execute(status -> recurrences.create(
                owner.barber(), owner.user(), key, request));
        assertEquals(result.id(), replay.id());
        assertEquals(8, count("appointments", owner.barber()));
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM appointment_events e "
                + "JOIN appointments a ON a.id=e.appointment_id "
                + "WHERE a.recurrence_id=?", Integer.class, result.id()));
        assertThrows(AppointmentRecurrenceService.RecurrenceConflictException.class,
                () -> tx.execute(status -> recurrences.create(
                        owner.barber(), owner.user(), key,
                        new Request(owner.client(), owner.service(), request.startsOn(),
                                LocalTime.of(15, 0), List.of(4, 5), 4,
                                new BigDecimal("50.00"), null))));
        assertEquals(8, count("appointments", owner.barber()));
    }

    @Test
    void oneBlockedDateRollsBackEntireSeries() {
        Owner owner = owner();
        Request request = request(owner);
        var check = recurrences.preview(owner.barber(), request);
        assertEquals(8, check.available());
        var busy = check.occurrences().getFirst();
        jdbc.update("INSERT INTO blocked_periods(barber_id,starts_at,ends_at) VALUES(?,?,?)",
                owner.barber(), busy.startsAt().atOffset(java.time.ZoneOffset.UTC),
                busy.startsAt().plusSeconds(3600).atOffset(java.time.ZoneOffset.UTC));
        assertEquals(1, recurrences.preview(owner.barber(), request).unavailable());
        assertThrows(AppointmentRecurrenceService.RecurrenceConflictException.class,
                () -> tx.execute(status -> recurrences.create(
                        owner.barber(), owner.user(), UUID.randomUUID().toString(), request)));
        assertEquals(0, count("appointments", owner.barber()));
        assertEquals(0, count("appointment_recurrences", owner.barber()));
    }

    @Test
    void cancelFuturePreservesCompletedReceiptAndHidesOtherTenants() {
        Owner owner = owner();
        Request request = request(owner);
        Series series = tx.execute(status -> recurrences.create(
                owner.barber(), owner.user(), UUID.randomUUID().toString(), request));
        Owner stranger = owner();
        assertTrue(recurrences.list(stranger.barber()).isEmpty());
        assertThrows(AppointmentRecurrenceService.RecurrenceNotFoundException.class,
                () -> recurrences.get(stranger.barber(), series.id()));

        UUID completed = jdbc.queryForObject(
                "SELECT id FROM appointments WHERE recurrence_id=? ORDER BY starts_at LIMIT 1",
                UUID.class, series.id());
        jdbc.update("UPDATE appointments SET status='COMPLETED',completed_at=now(),"
                + "received_amount=50 WHERE id=?", completed);
        Series cancelled = tx.execute(status -> recurrences.cancelFuture(owner.barber(), series.id()));
        assertEquals(0, cancelled.scheduledAppointments());
        assertEquals(8, cancelled.totalAppointments());
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM appointments "
                + "WHERE recurrence_id=? AND status='CANCELLED'", Integer.class, series.id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM appointments "
                + "WHERE recurrence_id=? AND status='COMPLETED' AND received_amount=50",
                Integer.class, series.id()));
        assertEquals(15, jdbc.queryForObject("SELECT count(*) FROM appointment_events e "
                + "JOIN appointments a ON a.id=e.appointment_id WHERE a.recurrence_id=?",
                Integer.class, series.id()));
        assertThrows(AppointmentRecurrenceService.RecurrenceNotFoundException.class,
                () -> tx.execute(status -> recurrences.cancelFuture(stranger.barber(), series.id())));
        // The repeat cancellation is idempotent and cannot alter completed history.
        tx.execute(status -> recurrences.cancelFuture(owner.barber(), series.id()));
        assertEquals(15, jdbc.queryForObject("SELECT count(*) FROM appointment_events e "
                + "JOIN appointments a ON a.id=e.appointment_id WHERE a.recurrence_id=?",
                Integer.class, series.id()));
    }

    private static Request request(Owner owner) {
        LocalDate monday = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                .plusWeeks(2).with(DayOfWeek.MONDAY);
        return new Request(owner.client(), owner.service(), monday, LocalTime.of(14, 0),
                List.of(4, 5), 4, new BigDecimal("50.00"), null);
    }

    private static Owner owner() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@tests.invalid", "test-only-password-hash");
        jdbc.update("INSERT INTO barbers(id,user_id,timezone) VALUES(?,?,?)",
                barber, user, "America/Sao_Paulo");
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Client')", client, barber);
        jdbc.update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price) "
                + "VALUES(?,?,'Haircut',60,50)", service, barber);
        for (int weekday = 1; weekday <= 7; weekday++) {
            jdbc.update("INSERT INTO working_hours(barber_id,weekday,opens_at,closes_at) "
                    + "VALUES(?,?,TIME '08:00',TIME '18:00')", barber, weekday);
        }
        return new Owner(user, barber, client, service);
    }

    private static int count(String table, UUID barberId) {
        if (!table.equals("appointments") && !table.equals("appointment_recurrences")) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE barber_id=?",
                Integer.class, barberId);
    }

    private record Owner(UUID user, UUID barber, UUID client, UUID service) {
    }
}
