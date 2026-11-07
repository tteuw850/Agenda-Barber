package com.agendabarber.backend.clientcatalog;

import java.math.BigDecimal;
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
class ClientRemovalIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("client_removal")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static ClientCatalogService service;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setup() {
        DataSource db = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(10, Flyway.configure().dataSource(db)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted);
        jdbc = new JdbcTemplate(db);
        service = new ClientCatalogService(jdbc, null);
        tx = new TransactionTemplate(new DataSourceTransactionManager(db));
    }

    @Test
    void removesUnreferencedClientOnlyFromActiveCatalog() {
        Owner owner = createOwner();
        deactivate(owner);
        assertEquals(0, service.list(owner.barber(), "", "all", 100, 0).size());
        assertEquals(false, isActive(owner));
        assertThrows(ClientCatalogService.ClientNotFoundException.class, () -> deactivate(owner));
    }

    @Test
    void keepsCompletedAppointmentHistoryAfterClientRemoval() {
        Owner owner = createOwner();
        UUID serviceId = createService(owner);
        jdbc.update("""
                INSERT INTO appointments(barber_id,client_id,service_id,starts_at,ends_at,
                    service_name_snapshot,price_snapshot,status,completed_at)
                VALUES(?,?,?,now()-interval '1 day',
                    now()-interval '1 day'+interval '30 minutes',
                    'Test haircut',20.00,'COMPLETED',now()-interval '1 day')
                """, owner.barber(), owner.client(), serviceId);
        deactivate(owner);
        assertEquals(false, isActive(owner));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE barber_id=? AND client_id=?",
                Integer.class, owner.barber(), owner.client()));
    }

    @Test
    void refusesRemovalWithOpenBookingUntilCancelled() {
        Owner owner = createOwner();
        UUID serviceId = createService(owner);
        jdbc.update("""
                INSERT INTO appointments(barber_id,client_id,service_id,starts_at,ends_at,
                    service_name_snapshot,price_snapshot,status)
                VALUES(?,?,?,now()+interval '10 days',
                    now()+interval '10 days 30 minutes',
                    'Test haircut',20.00,'SCHEDULED')
                """, owner.barber(), owner.client(), serviceId);
        assertThrows(ClientCatalogService.ClientHasOpenAppointmentsException.class,
                () -> deactivate(owner));
        assertTrue(isActive(owner));
        jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE barber_id=? AND client_id=?",
                owner.barber(), owner.client());
        deactivate(owner);
        assertEquals(false, isActive(owner));
    }

    @Test
    void refusesRemovalWhileOutstandingBalanceExists() {
        Owner owner = createOwner();
        jdbc.update("""
                INSERT INTO client_debt_entries(barber_id,client_id,amount,request_key)
                VALUES(?,?,?,?)
                """, owner.barber(), owner.client(), new BigDecimal("35.00"), UUID.randomUUID());
        assertThrows(ClientCatalogService.ClientHasOutstandingDebtException.class,
                () -> deactivate(owner));
        assertTrue(isActive(owner));
        jdbc.update("""
                INSERT INTO client_debt_entries(barber_id,client_id,amount,request_key)
                VALUES(?,?,?,?)
                """, owner.barber(), owner.client(), new BigDecimal("-35.00"), UUID.randomUUID());
        deactivate(owner);
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM client_debt_entries WHERE barber_id=? AND client_id=?",
                Integer.class, owner.barber(), owner.client()));
    }

    @Test
    void rejectsCrossBarberClientRemoval() {
        Owner first = createOwner();
        Owner second = createOwner();
        assertThrows(ClientCatalogService.ClientNotFoundException.class,
                () -> tx.execute(status -> {
                    service.deactivate(first.barber(), second.client());
                    return null;
                }));
        assertTrue(isActive(second));
    }

    private static void deactivate(Owner owner) {
        tx.execute(status -> {
            service.deactivate(owner.barber(), owner.client());
            return null;
        });
    }

    private static boolean isActive(Owner owner) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT active FROM clients WHERE barber_id=? AND id=?",
                Boolean.class, owner.barber(), owner.client()));
    }

    private static UUID createService(Owner owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services(id,barber_id,name,duration_minutes,default_price)
                VALUES(?,?,'Test haircut',30,20.00)
                """, id, owner.barber());
        return id;
    }

    private static Owner createOwner() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@test.invalid", "test-only-hash-not-for-login");
        jdbc.update("INSERT INTO barbers(id,user_id,timezone) VALUES(?,?,?)",
                barber, user, "America/Sao_Paulo");
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Removable Test Client')",
                client, barber);
        return new Owner(barber, client);
    }

    private record Owner(UUID barber, UUID client) {
    }
}
