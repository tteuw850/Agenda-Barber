package com.agendabarber.backend.clientcatalog;

import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ClientDebtIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("debt_integration")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static ClientDebtService service;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setup() {
        DataSource db = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(10, Flyway.configure().dataSource(db)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted);
        jdbc = new JdbcTemplate(db);
        service = new ClientDebtService(jdbc);
        tx = new TransactionTemplate(new DataSourceTransactionManager(db));
    }

    @Test
    void recordsChargesAndPartialPaymentsWithoutDoublePosting() {
        Owner owner = owner();
        UUID key = UUID.randomUUID();
        var charge = new ClientDebtController.Input("CHARGE", new BigDecimal("25.00"), "Test charge");
        var first = tx.execute(status -> service.add(owner.barber(), owner.client(), key, charge));
        assertEquals(new BigDecimal("25.00"), first.balance());
        var replay = tx.execute(status -> service.add(owner.barber(), owner.client(), key, charge));
        assertEquals(new BigDecimal("25.00"), replay.balance());
        assertEquals(1, entryCount(owner));
        tx.execute(status -> service.add(owner.barber(), owner.client(), UUID.randomUUID(),
                new ClientDebtController.Input("PAYMENT", new BigDecimal("10.00"), "Part paid")));
        assertEquals(new BigDecimal("15.00"),
                service.get(owner.barber(), owner.client()).balance());
        assertEquals(2, entryCount(owner));
        assertEquals(1, service.list(owner.barber()).size());
    }

    @Test
    void rejectsCrossTenantReadsOverpaymentsAndIdempotencyCollisions() {
        Owner first = owner();
        Owner other = owner();
        UUID key = UUID.randomUUID();
        tx.execute(status -> service.add(first.barber(), first.client(), key,
                new ClientDebtController.Input("CHARGE", new BigDecimal("20.00"), "Original")));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.get(other.barber(), first.client())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> tx.execute(status -> service.add(other.barber(), first.client(),
                        UUID.randomUUID(), new ClientDebtController.Input(
                                "CHARGE", new BigDecimal("1.00"), null)))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> tx.execute(status -> service.add(first.barber(), first.client(),
                        UUID.randomUUID(), new ClientDebtController.Input(
                                "PAYMENT", new BigDecimal("50.00"), null)))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> tx.execute(status -> service.add(first.barber(), first.client(), key,
                        new ClientDebtController.Input("CHARGE", new BigDecimal("20.00"),
                                "Changed note")))).getStatusCode());
        assertEquals(1, entryCount(first));
        assertTrue(service.list(other.barber()).isEmpty());
    }

    private static Owner owner() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@test.invalid", "test-only-hash-not-for-login");
        jdbc.update("INSERT INTO barbers(id,user_id,timezone) VALUES(?,?,?)",
                barber, user, "America/Sao_Paulo");
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Test Client')",
                client, barber);
        return new Owner(barber, client);
    }

    private static int entryCount(Owner owner) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM client_debt_entries WHERE barber_id=?",
                Integer.class, owner.barber());
    }

    private record Owner(UUID barber, UUID client) {
    }
}
