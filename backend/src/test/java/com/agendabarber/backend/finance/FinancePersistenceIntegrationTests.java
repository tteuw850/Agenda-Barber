package com.agendabarber.backend.finance;

import com.agendabarber.backend.appointments.AppointmentService;
import com.agendabarber.backend.finance.ExpenseDtos.ExpenseInput;
import com.agendabarber.backend.scheduling.AgendaSchedulingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
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

@Testcontainers
class FinancePersistenceIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_finance_test")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static ExpenseService expenses;
    private static AppointmentService appointments;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setup() {
        DriverManagerDataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway flyway = Flyway.configure().dataSource(data)
                .locations("classpath:db/migration").load();
        assertEquals(8, flyway.migrate().migrationsExecuted);
        flyway.validate();
        jdbc = new JdbcTemplate(data);
        expenses = new ExpenseService(jdbc);
        appointments = new AppointmentService(jdbc, new AgendaSchedulingService(data));
        tx = new TransactionTemplate(new DataSourceTransactionManager(data));
    }

    @Test
    void repeatExpenseCreateIsIdempotentAndCannotChangeRequest() {
        Fixture owner = fixture();
        ExpenseInput item = new ExpenseInput(LocalDate.parse("2030-04-01"),
                "Aluguel", new BigDecimal("150.00"), "Fixo");
        String key = UUID.randomUUID().toString();
        UUID id = tx.execute(status -> expenses.create(owner.barber(), owner.user(), key, item).id());
        UUID repeat = tx.execute(status -> expenses.create(owner.barber(), owner.user(), key, item).id());
        assertEquals(id, repeat);
        assertEquals(1, expenses.list(owner.barber(), LocalDate.parse("2030-04-01"),
                LocalDate.parse("2030-04-30"), 100, 0).size());
        ExpenseInput changed = new ExpenseInput(item.occurredOn(),
                "Outra conta", new BigDecimal("150.00"), "Fixo");
        assertThrows(ExpenseService.ExpenseConflictException.class,
                () -> tx.execute(status -> expenses.create(owner.barber(), owner.user(), key, changed)));
    }

    @Test
    void expensesStayIsolatedAndSoftDeletedRowsDisappear() {
        Fixture owner = fixture();
        Fixture other = fixture();
        ExpenseInput value = new ExpenseInput(LocalDate.parse("2030-04-15"),
                "Material", new BigDecimal("25.00"), "Produtos");
        UUID id = tx.execute(status -> expenses.create(
                owner.barber(), owner.user(), UUID.randomUUID().toString(), value).id());
        assertThrows(ExpenseService.ExpenseNotFoundException.class,
                () -> expenses.get(other.barber(), id));
        assertThrows(ExpenseService.ExpenseNotFoundException.class,
                () -> tx.execute(status -> expenses.update(other.barber(), id, value)));
        assertThrows(ExpenseService.ExpenseNotFoundException.class,
                () -> tx.execute(status -> {
                    expenses.delete(other.barber(), id);
                    return null;
                }));
        tx.executeWithoutResult(status -> expenses.delete(owner.barber(), id));
        tx.executeWithoutResult(status -> expenses.delete(owner.barber(), id));
        assertThrows(ExpenseService.ExpenseNotFoundException.class,
                () -> expenses.get(owner.barber(), id));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE id=? AND deleted_at IS NOT NULL", Integer.class, id));
    }

    @Test
    void completionPreservesBookedPriceAndIdempotentlyStoresActualReceipt() {
        Fixture owner = fixture();
        UUID appointmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO appointments(id,barber_id,client_id,service_id,"
                        + "starts_at,ends_at,service_name_snapshot,price_snapshot,status)"
                        + " VALUES(?,?,?,?,?,?, 'Corte',75.00,'IN_PROGRESS'::appointment_status)",
                appointmentId, owner.barber(), owner.client(), owner.service(),
                OffsetDateTime.parse("2030-05-01T13:00:00Z"),
                OffsetDateTime.parse("2030-05-01T14:00:00Z"));
        tx.execute(status -> appointments.complete(
                owner.barber(), appointmentId, new BigDecimal("62.50")));
        tx.execute(status -> appointments.complete(
                owner.barber(), appointmentId, new BigDecimal("62.50")));
        assertEquals(new BigDecimal("75.00"), appointments.get(owner.barber(), appointmentId).price());
        assertEquals(new BigDecimal("62.50"), appointments.get(owner.barber(), appointmentId).receivedAmount());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM appointment_events WHERE appointment_id=? AND event_type='COMPLETED'",
                Integer.class, appointmentId));
        assertThrows(AppointmentService.AppointmentConflictException.class,
                () -> tx.execute(status -> appointments.complete(
                        owner.barber(), appointmentId, new BigDecimal("80.00"))));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE id=? AND status='COMPLETED'"
                        + " AND received_amount=62.50", Integer.class, appointmentId));
    }

    private static Fixture fixture() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@tests.invalid", "not-a-real-password-hash-for-tests");
        jdbc.update("INSERT INTO barbers(id,user_id) VALUES(?,?)", barber, user);
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Cliente')", client, barber);
        jdbc.update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price)"
                + " VALUES(?,?,'Corte',60,75.00)", service, barber);
        return new Fixture(user, barber, client, service);
    }

    private record Fixture(UUID user, UUID barber, UUID client, UUID service) {
    }
}
