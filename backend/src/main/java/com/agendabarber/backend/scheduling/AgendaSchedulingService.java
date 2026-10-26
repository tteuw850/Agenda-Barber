package com.agendabarber.backend.scheduling;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Internal scheduling guard. All appointment and blocked-period writers must
 * lock the same barber row before checking conflicts. The authenticated API
 * must later derive barberId from the user's session, never request input.
 */
@Service
@Profile("neon")
public final class AgendaSchedulingService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AgendaSchedulingService(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public UUID schedule(UUID barberId, UUID clientId, UUID serviceId, Instant start, Instant end) {
        Objects.requireNonNull(barberId);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(serviceId);
        checkRange(start, end);
        try {
            return tx.execute(status -> {
                ZoneId zone = lockBarber(barberId);
                Snapshot service = jdbc.queryForObject(
                        "SELECT name, duration_minutes, default_price FROM services "
                                + "WHERE barber_id=? AND id=? AND active=true",
                        (rs, number) -> new Snapshot(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)),
                        barberId, serviceId);
                if (service == null) {
                    throw new IllegalArgumentException("Service not found");
                }
                Duration duration = Duration.between(start, end);
                if (duration.toMinutes() != service.minutes() || duration.toSeconds() % 60 != 0) {
                    throw new IllegalArgumentException("Slot length must match service duration");
                }
                Integer clients = jdbc.queryForObject(
                        "SELECT count(*) FROM clients WHERE barber_id=? AND id=?",
                        Integer.class, barberId, clientId);
                if (clients == null || clients != 1) {
                    throw new IllegalArgumentException("Client not in barber scope");
                }
                requireWorkingHours(barberId, start, end, zone);
                requireNoAppointment(barberId, start, end);
                requireNoBlock(barberId, start, end);
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO appointments "
                                + "(id,barber_id,client_id,service_id,starts_at,ends_at,"
                                + "service_name_snapshot,price_snapshot,status) "
                                + "VALUES(?,?,?,?,?,?,?,?,'SCHEDULED'::appointment_status)",
                        id, barberId, clientId, serviceId, utc(start), utc(end),
                        service.name(), service.price());
                return id;
            });
        } catch (DataIntegrityViolationException error) {
            if (hasSqlState(error, "23P01")) {
                throw new ScheduleConflictException(ConflictType.APPOINTMENT_OVERLAP);
            }
            throw error;
        }
    }

    public UUID block(UUID barberId, Instant start, Instant end, String reason) {
        Objects.requireNonNull(barberId);
        checkRange(start, end);
        return tx.execute(status -> {
            lockBarber(barberId);
            requireNoAppointment(barberId, start, end);
            requireNoBlock(barberId, start, end);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO blocked_periods(id,barber_id,starts_at,ends_at,reason) "
                            + "VALUES(?,?,?,?,?)", id, barberId, utc(start), utc(end), reason);
            return id;
        });
    }

    private ZoneId lockBarber(UUID barberId) {
        String zone = jdbc.queryForObject(
                "SELECT timezone FROM barbers WHERE id=? FOR UPDATE", String.class, barberId);
        return ZoneId.of(Objects.requireNonNull(zone));
    }

    private void requireWorkingHours(UUID barberId, Instant start, Instant end, ZoneId zone) {
        ZonedDateTime from = start.atZone(zone);
        ZonedDateTime to = end.atZone(zone);
        if (!from.toLocalDate().equals(to.toLocalDate())) {
            throw new ScheduleConflictException(ConflictType.OUTSIDE_WORKING_HOURS);
        }
        DayOfWeek day = from.getDayOfWeek();
        LocalTime fromTime = from.toLocalTime();
        LocalTime toTime = to.toLocalTime();
        Boolean open = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM working_hours "
                        + "WHERE barber_id=? AND weekday=? AND enabled=true "
                        + "AND opens_at<=?::time AND closes_at>=?::time)",
                Boolean.class, barberId, day.getValue(), fromTime, toTime);
        if (!Boolean.TRUE.equals(open)) {
            throw new ScheduleConflictException(ConflictType.OUTSIDE_WORKING_HOURS);
        }
    }

    private void requireNoAppointment(UUID barberId, Instant start, Instant end) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM appointments "
                        + "WHERE barber_id=? AND status IN ('SCHEDULED','IN_PROGRESS') "
                        + "AND starts_at<? AND ends_at>?)",
                Boolean.class, barberId, utc(end), utc(start));
        if (Boolean.TRUE.equals(exists)) {
            throw new ScheduleConflictException(ConflictType.APPOINTMENT_OVERLAP);
        }
    }

    private void requireNoBlock(UUID barberId, Instant start, Instant end) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM blocked_periods "
                        + "WHERE barber_id=? AND starts_at<? AND ends_at>?)",
                Boolean.class, barberId, utc(end), utc(start));
        if (Boolean.TRUE.equals(exists)) {
            throw new ScheduleConflictException(ConflictType.BLOCKED_PERIOD_OVERLAP);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void checkRange(Instant start, Instant end) {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End must follow start");
        }
    }

    private static boolean hasSqlState(Throwable error, String state) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private record Snapshot(String name, int minutes, BigDecimal price) {
    }

    public enum ConflictType {
        APPOINTMENT_OVERLAP,
        BLOCKED_PERIOD_OVERLAP,
        OUTSIDE_WORKING_HOURS
    }

    /** Safe domain error for future HTTP 409 conversion, not a public endpoint. */
    public static final class ScheduleConflictException extends RuntimeException {
        private final ConflictType type;

        public ScheduleConflictException(ConflictType type) {
            super("Requested slot is unavailable");
            this.type = type;
        }

        public ConflictType type() {
            return type;
        }
    }
}
