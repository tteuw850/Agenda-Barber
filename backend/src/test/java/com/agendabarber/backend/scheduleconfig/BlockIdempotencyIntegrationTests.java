package com.agendabarber.backend.scheduleconfig;

import com.agendabarber.backend.common.CreateIdempotencyExecutor;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.SingleBlockInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.SingleBlockView;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WeeklyBlockInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WeeklyBlockView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
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
class BlockIdempotencyIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_blocks_idempotency")
            .withUsername("test_only")
            .withPassword("test_only");
    private static JdbcTemplate jdbc;
    private static ScheduleConfigService schedule;
    private static CreateIdempotencyExecutor idempotency;

    @BeforeAll
    static void setup() {
        DataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(8, Flyway.configure().dataSource(data)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted);
        jdbc = new JdbcTemplate(data);
        schedule = new ScheduleConfigService(jdbc);
        idempotency = new CreateIdempotencyExecutor(jdbc, new DataSourceTransactionManager(data));
    }

    @Test
    void sameOneTimeBlockCreatesOnlyOneRowAndConflictsOnChangedPayload() {
        Owner owner = owner();
        SingleBlockInput input = new SingleBlockInput(
                Instant.parse("2031-06-02T13:00:00Z"),
                Instant.parse("2031-06-02T14:00:00Z"), "Almoco", "Pausa");
        String key = UUID.randomUUID().toString();
        SingleBlockView first = single(owner, key, input);
        assertEquals(first, single(owner, key, input));
        assertEquals(1, count("blocked_periods", owner.barber()));
        assertThrows(CreateIdempotencyExecutor.IdempotencyConflictException.class,
                () -> single(owner, key, new SingleBlockInput(
                        input.startsAt(), input.endsAt(), "Outro", input.notes())));
    }

    @Test
    void weeklyBlockReplaysAndRejectsCrossOperationKeyReuse() {
        Owner owner = owner();
        WeeklyBlockInput input = new WeeklyBlockInput(1, LocalTime.NOON,
                LocalTime.of(13, 0), LocalDate.parse("2031-07-01"),
                LocalDate.parse("2031-07-29"), "Almoco", null);
        String key = UUID.randomUUID().toString();
        WeeklyBlockView first = weekly(owner, key, input);
        assertEquals(first, weekly(owner, key, input));
        assertEquals(1, count("recurring_blocks", owner.barber()));
        assertThrows(CreateIdempotencyExecutor.IdempotencyConflictException.class,
                () -> single(owner, key, new SingleBlockInput(
                        Instant.parse("2031-09-01T13:00:00Z"),
                        Instant.parse("2031-09-01T14:00:00Z"), null, null)));
        assertEquals(0, count("blocked_periods", owner.barber()));
    }

    private static SingleBlockView single(Owner owner, String key, SingleBlockInput input) {
        return idempotency.run(owner.barber(), owner.user(), key, "SINGLE_BLOCK_CREATE",
                input, () -> schedule.createBlock(owner.barber(), input),
                SingleBlockView::id, id -> schedule.getBlock(owner.barber(), id));
    }

    private static WeeklyBlockView weekly(Owner owner, String key, WeeklyBlockInput input) {
        return idempotency.run(owner.barber(), owner.user(), key, "RECURRING_BLOCK_CREATE",
                input, () -> schedule.createRecurring(owner.barber(), input),
                WeeklyBlockView::id, id -> schedule.getRecurring(owner.barber(), id));
    }

    private static int count(String table, UUID barber) {
        if (!table.equals("blocked_periods") && !table.equals("recurring_blocks")) {
            throw new IllegalArgumentException("Unknown test table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE barber_id=?",
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
