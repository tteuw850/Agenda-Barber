package com.agendabarber.backend.scheduleconfig;

import com.agendabarber.backend.scheduleconfig.ScheduleDtos.FreeSlot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class AvailableSlotsBatchIntegrationTests {
    private static final LocalDate MONDAY = LocalDate.parse("2031-06-02");
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("batch_availability")
            .withUsername("test_only")
            .withPassword("test_only");

    private static JdbcTemplate jdbc;
    private static ScheduleConfigService service;

    @BeforeAll
    static void setup() {
        DataSource db = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertEquals(10, Flyway.configure().dataSource(db)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted);
        jdbc = new JdbcTemplate(db);
        service = new ScheduleConfigService(jdbc);
    }

    @Test
    void listsEntireDayWithNoBusyPeriodsAndEmptyDaysQuickly() {
        Owner owner = owner();
        UUID haircut = haircut(owner);
        hours(owner);
        assertEquals(List.of("09:00", "09:15", "09:30", "09:45", "10:00",
                "10:15", "10:30", "10:45", "11:00", "11:15", "11:30"),
                labels(service.availableSlots(owner.barber(), MONDAY, haircut)));
        assertEquals(List.of(), service.availableSlots(owner.barber(), MONDAY.plusDays(1), haircut));
    }

    @Test
    void batchesAppointmentsOneTimeBlocksAndWeeklyBlocksWithTenantIsolation() {
        Owner owner = owner();
        UUID haircut = haircut(owner);
        hours(owner);

        appointment(owner, haircut, "2031-06-02T12:30:00Z", "2031-06-02T13:00:00Z", "SCHEDULED");
        appointment(owner, haircut, "2031-06-02T12:00:00Z", "2031-06-02T12:30:00Z", "CANCELLED");
        jdbc.update("""
                INSERT INTO blocked_periods(barber_id,starts_at,ends_at)
                VALUES(?,?,?)
                """, owner.barber(), OffsetDateTime.parse("2031-06-02T13:30:00Z"),
                OffsetDateTime.parse("2031-06-02T14:00:00Z"));
        jdbc.update("""
                INSERT INTO recurring_blocks(barber_id,weekday,starts_at,ends_at,valid_from,valid_until)
                VALUES(?,1,'11:30','12:00',?,?)
                """, owner.barber(), LocalDate.parse("2031-06-01"), LocalDate.parse("2031-06-30"));

        Owner another = owner();
        UUID otherHaircut = haircut(another);
        hours(another);
        appointment(another, otherHaircut, "2031-06-02T12:00:00Z",
                "2031-06-02T15:00:00Z", "SCHEDULED");

        assertEquals(List.of("09:00", "10:00", "11:00"),
                labels(service.availableSlots(owner.barber(), MONDAY, haircut)));
        assertEquals(List.of("09:00", "10:00", "11:00"),
                labels(service.availableSlots(owner.barber(), MONDAY, haircut)));
    }

    @Test
    void previousDayBlockStillOverlapsLocalMorning() {
        Owner owner = owner();
        UUID haircut = haircut(owner);
        hours(owner);
        jdbc.update("""
                INSERT INTO blocked_periods(barber_id,starts_at,ends_at)
                VALUES(?,?,?)
                """, owner.barber(), OffsetDateTime.parse("2031-06-02T02:30:00Z"),
                OffsetDateTime.parse("2031-06-02T12:15:00Z"));
        assertEquals(List.of("09:15", "09:30", "09:45", "10:00", "10:15", "10:30",
                "10:45", "11:00", "11:15", "11:30"),
                labels(service.availableSlots(owner.barber(), MONDAY, haircut)));
    }

    @Test
    void missingServiceDoesNotLeakOtherBarberData() {
        Owner first = owner();
        Owner second = owner();
        UUID othersService = haircut(second);
        hours(first);
        assertThrows(ScheduleConfigService.ScheduleNotFoundException.class,
                () -> service.availableSlots(first.barber(), MONDAY, othersService));
    }

    private static List<String> labels(List<FreeSlot> slots) {
        return slots.stream().map(slot -> slot.localStart().toString()).toList();
    }

    private static void hours(Owner owner) {
        jdbc.update("""
                INSERT INTO working_hours(barber_id,weekday,enabled,opens_at,closes_at)
                VALUES(?,1,true,?,?)
                """, owner.barber(), LocalTime.of(9, 0), LocalTime.NOON);
    }

    private static UUID haircut(Owner owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services(id,barber_id,name,duration_minutes,default_price)
                VALUES(?,?,'Standard haircut',30,?)
                """, id, owner.barber(), new BigDecimal("30.00"));
        return id;
    }

    private static void appointment(Owner owner, UUID haircut, String start, String end, String status) {
        jdbc.update("""
                INSERT INTO appointments(barber_id,client_id,service_id,starts_at,ends_at,
                    service_name_snapshot,price_snapshot,status)
                VALUES(?,?,?, ?, ?, 'Standard haircut', 30.00, ?::appointment_status)
                """, owner.barber(), owner.client(), haircut, OffsetDateTime.parse(start),
                OffsetDateTime.parse(end), status);
    }

    private static Owner owner() {
        UUID user = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'BARBER')",
                user, user + "@test.invalid", "test-only-hash-for-synthetic-tests");
        jdbc.update("INSERT INTO barbers(id,user_id,timezone) VALUES(?,?,'America/Sao_Paulo')",
                barber, user);
        jdbc.update("INSERT INTO clients(id,barber_id,name) VALUES(?,?,'Availability Test')", client, barber);
        return new Owner(barber, client);
    }

    private record Owner(UUID barber, UUID client) {
    }
}
