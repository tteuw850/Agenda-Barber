package com.agendabarber.backend.common;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class CreateIdempotencyIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_idempotency_test")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static CreateIdempotencyExecutor executor;

    @BeforeAll
    static void setup() {
        DataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(7, Flyway.configure().dataSource(data)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted);
        jdbc = new JdbcTemplate(data);
        executor = new CreateIdempotencyExecutor(jdbc, new DataSourceTransactionManager(data));
    }

    @Test
    void replayReturnsSameIdentityWithoutSecondInsertAndRejectsReuse() {
        Owner owner = owner();
        String key = UUID.randomUUID().toString();
        AtomicInteger writes = new AtomicInteger();
        Supplier<UUID> write = () -> insertClient(owner.barber(), writes);
        UUID first = run(owner, key, "CLIENT_CREATE", "Alice", write);
        UUID second = run(owner, key, "CLIENT_CREATE", "Alice", write);
        assertEquals(first, second);
        assertEquals(1, writes.get());
        assertEquals(1, countClients(owner.barber()));
        assertThrows(CreateIdempotencyExecutor.IdempotencyConflictException.class,
                () -> run(owner, key, "CLIENT_CREATE", "Bob", write));
        assertThrows(CreateIdempotencyExecutor.IdempotencyConflictException.class,
                () -> run(owner, key, "SERVICE_CREATE", "Alice", write));
        assertEquals(1, writes.get());
    }

    @Test
    void transactionRollbackAllowsSafeRetryWithSameKey() {
        Owner owner = owner();
        String key = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> run(owner, key, "CLIENT_CREATE", "A",
                () -> {
                    insertClient(owner.barber(), new AtomicInteger());
                    throw new IllegalStateException("Simulated failure before commit");
                }));
        assertEquals(0, countClients(owner.barber()));
        UUID saved = run(owner, key, "CLIENT_CREATE", "A",
                () -> insertClient(owner.barber(), new AtomicInteger()));
        assertEquals(saved, run(owner, key, "CLIENT_CREATE", "A",
                () -> {
                    throw new AssertionError("Replay must not write");
                }));
        assertEquals(1, countClients(owner.barber()));
    }

    @Test
    void concurrentSameKeyHasSingleCommittedCreation() throws Exception {
        Owner owner = owner();
        String key = UUID.randomUUID().toString();
        AtomicInteger writes = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<UUID>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start");
                }
                return run(owner, key, "CLIENT_CREATE", "Same",
                        () -> insertClient(owner.barber(), writes));
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers not ready");
            }
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, writes.get());
        assertEquals(1, countClients(owner.barber()));
    }

    private static UUID run(Owner owner, String key, String operation, String payload,
            Supplier<UUID> writer) {
        return executor.run(owner.barber(), owner.user(), key, operation, payload,
                writer, id -> id,
                id -> jdbc.queryForObject("SELECT id FROM clients WHERE barber_id=? AND id=?",
                        UUID.class, owner.barber(), id));
    }

    private static UUID insertClient(UUID barber, AtomicInteger writes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Teste')", id, barber);
        writes.incrementAndGet();
        return id;
    }

    private static int countClients(UUID barber) {
        return jdbc.queryForObject("SELECT count(*) FROM clients WHERE barber_id=?",
                Integer.class, barber);
    }

    private static Owner owner() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@tests.invalid", "test-only-password-hash");
        jdbc.update("INSERT INTO barbers(id,user_id) VALUES(?,?)", barber, user);
        return new Owner(user, barber);
    }

    private record Owner(UUID user, UUID barber) {
    }
}
