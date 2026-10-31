package com.agendabarber.backend.reports;

import com.agendabarber.backend.reports.ReportDtos.Dashboard;
import com.agendabarber.backend.reports.ReportDtos.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class ReportPersistenceIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_reports_test")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static ReportService reports;

    @BeforeAll
    static void setup() {
        DriverManagerDataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway flyway = Flyway.configure().dataSource(data)
                .locations("classpath:db/migration").load();
        assertEquals(8, flyway.migrate().migrationsExecuted);
        flyway.validate();
        jdbc = new JdbcTemplate(data);
        reports = new ReportService(jdbc);
    }

    @Test
    void filtersByCompletionTimeLocalCalendarAndOwnerWithoutFabricatingReceipts() {
        Fixture owner = fixture();
        Fixture other = fixture();
        UUID beard = service(owner, "Barba", false);
        // 02:59 UTC is still March 31 in Sao Paulo, 03:00 UTC starts April 1.
        appointment(owner, owner.haircut(), "2030-04-01T02:59:00Z",
                "COMPLETED", "90.00");
        appointment(owner, owner.haircut(), "2030-04-01T03:00:00Z",
                "COMPLETED", "100.00");
        appointment(owner, beard, "2030-04-01T12:00:00Z",
                "COMPLETED", "50.00");
        appointment(owner, owner.haircut(), "2030-04-02T11:00:00Z",
                "COMPLETED", null); // legacy receipt is unknown, not zero
        appointment(owner, owner.haircut(), "2030-04-01T13:00:00Z",
                "SCHEDULED", null);
        UUID cancelled = appointment(owner, owner.haircut(), "2030-04-01T14:00:00Z",
                "CANCELLED", null);
        jdbc.update("INSERT INTO appointment_events(barber_id,appointment_id,event_type,"
                        + "new_status,happened_at) VALUES(?,?,'CANCELLED',"
                        + "'CANCELLED'::appointment_status,?)",
                owner.barber(), cancelled, OffsetDateTime.parse("2030-04-01T14:00:00Z"));
        appointment(other, other.haircut(), "2030-04-01T10:00:00Z",
                "COMPLETED", "900.00");
        expense(owner, "2030-04-01", "30.00", false);
        expense(owner, "2030-04-01", "25.00", true);
        expense(other, "2030-04-01", "700.00", false);

        Summary aprilFirst = reports.summary(owner.barber(),
                LocalDate.parse("2030-04-01"), LocalDate.parse("2030-04-01"));
        assertEquals(2, aprilFirst.completedAppointments());
        assertEquals(1, aprilFirst.cancellations());
        assertEquals(1, aprilFirst.haircuts());
        assertEquals(2, aprilFirst.receiptedAppointments());
        assertEquals(0, aprilFirst.missingReceipts());
        assertEquals(new BigDecimal("150.00"), aprilFirst.revenue());
        assertEquals(new BigDecimal("30.00"), aprilFirst.expenses());
        assertEquals(new BigDecimal("120.00"), aprilFirst.estimatedProfit());
        assertEquals(new BigDecimal("75.00"), aprilFirst.averageTicket());

        Dashboard dashboard = reports.dashboard(owner.barber(),
                LocalDate.parse("2030-04-01"), LocalDate.parse("2030-04-03"));
        assertEquals(3, dashboard.summary().completedAppointments());
        assertEquals(2, dashboard.summary().haircuts());
        assertEquals(1, dashboard.summary().missingReceipts());
        assertEquals(new BigDecimal("75.00"), dashboard.summary().averageTicket());
        assertEquals(3, dashboard.daily().size());
        assertEquals(0, dashboard.daily().get(2).completedAppointments());
        assertEquals(new BigDecimal("0.00"), dashboard.daily().get(2).revenue());
        assertEquals(2, dashboard.popularServices().getFirst().completedAppointments());
        assertEquals(new BigDecimal("100.00"), dashboard.popularServices().getFirst().revenue());
        assertEquals("America/Sao_Paulo", dashboard.summary().timezone());
    }

    @Test
    void returnsZeroTicketWhenOnlyUnknownReceiptsAndValidatesBounds() {
        Fixture owner = fixture();
        appointment(owner, owner.haircut(), "2031-05-01T12:00:00Z",
                "COMPLETED", null);
        Summary summary = reports.summary(owner.barber(),
                LocalDate.parse("2031-05-01"), LocalDate.parse("2031-05-01"));
        assertEquals(new BigDecimal("0.00"), summary.revenue());
        assertEquals(new BigDecimal("0.00"), summary.averageTicket());
        assertEquals(1, summary.missingReceipts());
        assertEquals(0, summary.receiptedAppointments());
        assertThrows(ReportService.InvalidReportException.class, () -> reports.summary(
                owner.barber(), LocalDate.parse("2031-06-02"), LocalDate.parse("2031-06-01")));
        assertThrows(ReportService.InvalidReportException.class, () -> reports.summary(
                owner.barber(), LocalDate.parse("2031-01-01"), LocalDate.parse("2032-02-01")));
        assertThrows(ReportService.InvalidReportException.class, () -> reports.popularServices(
                owner.barber(), LocalDate.parse("2031-05-01"),
                LocalDate.parse("2031-05-01"), 51));
        assertThrows(ReportService.ReportNotFoundException.class, () -> reports.summary(
                UUID.randomUUID(), LocalDate.parse("2031-05-01"),
                LocalDate.parse("2031-05-01")));
    }

    private static Fixture fixture() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID haircut = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@tests.invalid", "test-password-hash-only");
        jdbc.update("INSERT INTO barbers(id,user_id) VALUES(?,?)", barber, user);
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Client')", client, barber);
        jdbc.update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price,"
                        + "is_haircut) VALUES(?,?,'Corte',30,75.00,true)",
                haircut, barber);
        return new Fixture(barber, client, haircut);
    }

    private static UUID service(Fixture fixture, String name, boolean haircut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO services(id,barber_id,name,duration_minutes,"
                        + "default_price,is_haircut) VALUES(?,?,?,30,30.00,?)",
                id, fixture.barber(), name, haircut);
        return id;
    }

    private static UUID appointment(
            Fixture fixture, UUID service, String completedAt, String status, String received) {
        OffsetDateTime start = OffsetDateTime.parse(completedAt).minusHours(1);
        OffsetDateTime end = start.plusMinutes(30);
        OffsetDateTime completed = "COMPLETED".equals(status)
                ? OffsetDateTime.parse(completedAt) : null;
        BigDecimal amount = received == null ? null : new BigDecimal(received);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointments"
                        + "(id,barber_id,client_id,service_id,starts_at,ends_at,"
                        + "service_name_snapshot,price_snapshot,status,completed_at,received_amount)"
                        + " VALUES(?,?,?,?,?,?,(SELECT name FROM services WHERE id=?),"
                        + "75.00,?::appointment_status,?,?)",
                id, fixture.barber(), fixture.client(), service,
                start, end, service, status, completed, amount);
        return id;
    }

    private static void expense(Fixture fixture, String on, String amount, boolean deleted) {
        jdbc.update("INSERT INTO expenses(id,barber_id,occurred_on,description,"
                        + "amount,category,deleted_at) VALUES(?,?,?,'Material',?,'Fixo',"
                        + "CASE WHEN ? THEN now() ELSE NULL END)",
                UUID.randomUUID(), fixture.barber(), LocalDate.parse(on),
                new BigDecimal(amount), deleted);
    }

    private record Fixture(UUID barber, UUID client, UUID haircut) {
    }
}
