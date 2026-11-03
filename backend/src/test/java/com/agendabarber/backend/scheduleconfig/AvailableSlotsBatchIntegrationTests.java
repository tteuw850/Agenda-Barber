package com.agendabarber.backend.scheduleconfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class AvailableSlotsBatchIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("slots_test")
            .withUsername("test_only")
            .withPassword("test_only");
    private static JdbcTemplate jdbc;
    private static ScheduleConfigService schedule;

    @BeforeAll
    static void setup() {
        DataSource source = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        schedule = new ScheduleConfigService(jdbc);
    }

    @Test
    void returnsFreeSlotsAndExcludesOneTimeAndRecurringBlocksWithoutLeakingOtherBarbers() {
        UUID barber = barber();
        UUID otherBarber = barber();
        UUID service = UUID.randomUUID();
        LocalDate day = LocalDate.of(2031, 6, 2); // Monday, Sao Paulo time
        jdbc.update("INSERT INTO services(id,barber_id,name,duration_minutes,default_price) "
                + "VALUES(?,?,?,30,25)", service, barber, "Corte");
        jdbc.update("INSERT INTO working_hours(barber_id,weekday,enabled,opens_at,closes_at) "
                + "VALUES(?,1,true,'09:00','11:00')", barber);
        assertEquals(7, schedule.availableSlots(barber, day, service).size());

        jdbc.update("INSERT INTO blocked_periods(barber_id,starts_at,ends_at) VALUES(?,?,?)",
                barber, Instant.parse("2031-06-02T12:30:00Z"),
                Instant.parse("2031-06-02T13:00:00Z"));
        jdbc.update("INSERT INTO blocked_periods(barber_id,starts_at,ends_at) VALUES(?,?,?)",
                otherBarber, Instant.parse("2031-06-02T12:00:00Z"),
                Instant.parse("2031-06-02T12:30:00Z"));
        assertEquals(4, schedule.availableSlots(barber, day, service).size());

        jdbc.update("INSERT INTO recurring_blocks(barber_id,weekday,starts_at,ends_at,valid_from,valid_until) "
                + "VALUES(?,1,'10:15','10:45',?,?)", barber, day, day);
        var free = schedule.availableSlots(barber, day, service);
        assertEquals(1, free.size());
        assertEquals(LocalTime.of(9, 0), free.getFirst().localStart());
    }

    private static UUID barber() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@tests.invalid", "test-only-password-hash");
        jdbc.update("INSERT INTO barbers(id,user_id) VALUES(?,?)", barber, user);
        return barber;
    }
}
