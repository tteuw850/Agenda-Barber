package com.agendabarber.backend.scheduling;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
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
                requireNoRecurringBlock(barberId, start, end, zone);
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

    /**
     * Persist booking, event and idempotency outcome in ONE transaction.
     * A unique barber-row lock serializes bookings and schedule/block writers.
     * No intermediate reservation with nullable resource_id can become visible.
     */
    public UUID bookAtomic(UUID barberId, UUID userId, UUID clientId, UUID serviceId,
                           Instant start, BigDecimal price, String notes,
                           String key, String fingerprint) {
        try {
            return tx.execute(status -> {
                ZoneId zone = lockBarber(barberId);
                List<BookingKey> found = jdbc.query("SELECT operation,request_hash,resource_id "
                                + "FROM idempotency_records WHERE user_id=? AND idempotency_key=?",
                        (rs, n) -> new BookingKey(rs.getString(1), rs.getString(2),
                                rs.getObject(3, UUID.class)), userId, key);
                if (!found.isEmpty()) {
                    BookingKey prior = found.get(0);
                    if (!"APPOINTMENT_CREATE".equals(prior.operation())
                            || !fingerprint.equals(prior.fingerprint())
                            || prior.resourceId() == null) {
                        throw new ScheduleConflictException(ConflictType.IDEMPOTENCY_CONFLICT);
                    }
                    return prior.resourceId();
                }

                Snapshot service;
                try {
                    service = jdbc.queryForObject("SELECT name,duration_minutes,default_price "
                                    + "FROM services WHERE barber_id=? AND id=? AND active=true",
                            (rs, n) -> new Snapshot(rs.getString(1), rs.getInt(2),
                                    rs.getBigDecimal(3)), barberId, serviceId);
                } catch (EmptyResultDataAccessException missing) {
                    throw new IllegalArgumentException("Service not found");
                }
                Boolean client = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clients "
                                + "WHERE id=? AND barber_id=? AND active=true)",
                        Boolean.class, clientId, barberId);
                if (!Boolean.TRUE.equals(client)) {
                    throw new IllegalArgumentException("Client not found");
                }
                Instant end = start.plus(Duration.ofMinutes(service.minutes()));
                requireWorkingHours(barberId, start, end, zone);
                requireNoAppointment(barberId, start, end);
                requireNoBlock(barberId, start, end);
                requireNoRecurringBlock(barberId, start, end, zone);

                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO appointments "
                                + "(id,barber_id,client_id,service_id,starts_at,ends_at,"
                                + "service_name_snapshot,price_snapshot,notes,status) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,'SCHEDULED'::appointment_status)",
                        id, barberId, clientId, serviceId, utc(start), utc(end),
                        service.name(), price == null ? service.price() : price, notes);
                jdbc.update("INSERT INTO appointment_events "
                                + "(barber_id,appointment_id,event_type,previous_status,new_status) "
                                + "VALUES(?,?,'CREATED',NULL,'SCHEDULED'::appointment_status)",
                        barberId, id);
                jdbc.update("INSERT INTO idempotency_records "
                                + "(user_id,idempotency_key,request_hash,operation,resource_id) "
                                + "VALUES(?,?,?,'APPOINTMENT_CREATE',?)",
                        userId, key, fingerprint, id);
                return id;
            });
        } catch (DataIntegrityViolationException error) {
            if (hasSqlState(error, "23P01")) {
                throw new ScheduleConflictException(ConflictType.APPOINTMENT_OVERLAP);
            }
            throw error;
        }
    }

    private record BookingKey(String operation, String fingerprint, UUID resourceId) {
    }

    public UUID block(UUID barberId, Instant start, Instant end, String reason) {
        Objects.requireNonNull(barberId);
        checkRange(start, end);
        return tx.execute(status -> {
            ZoneId zone = lockBarber(barberId);
            requireNoAppointment(barberId, start, end);
            requireNoBlock(barberId, start, end);
            requireNoRecurringBlock(barberId, start, end, zone);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO blocked_periods(id,barber_id,starts_at,ends_at,reason) "
                            + "VALUES(?,?,?,?,?)", id, barberId, utc(start), utc(end), reason);
            return id;
        });
    }

    /** Rebook an unstarted appointment within the same barber transaction/lock discipline. */
    public void reschedule(UUID barberId, UUID id, UUID clientId, UUID serviceId,
                           Instant start, BigDecimal price, String notes) {
        try {
            tx.executeWithoutResult(status -> {
                ZoneId zone = lockBarber(barberId);
                String state;
                try {
                    state = jdbc.queryForObject(
                            "SELECT status::text FROM appointments WHERE barber_id=? AND id=? FOR UPDATE",
                            String.class, barberId, id);
                } catch (EmptyResultDataAccessException ignored) {
                    throw new IllegalArgumentException("Appointment not found");
                }
                if (!"SCHEDULED".equals(state)) {
                    throw new ScheduleConflictException(ConflictType.APPOINTMENT_OVERLAP);
                }
                Snapshot service;
                try {
                    service = jdbc.queryForObject(
                            "SELECT name,duration_minutes,default_price FROM services "
                                    + "WHERE barber_id=? AND id=? AND active=true",
                            (rs, n) -> new Snapshot(rs.getString(1), rs.getInt(2),
                                    rs.getBigDecimal(3)), barberId, serviceId);
                } catch (EmptyResultDataAccessException ignored) {
                    throw new IllegalArgumentException("Service not found");
                }
                Boolean client = jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM clients "
                                + "WHERE id=? AND barber_id=? AND active=true)",
                        Boolean.class, clientId, barberId);
                if (!Boolean.TRUE.equals(client)) {
                    throw new IllegalArgumentException("Client not found");
                }
                Instant end = start.plus(Duration.ofMinutes(service.minutes()));
                requireWorkingHours(barberId, start, end, zone);
                Boolean busy = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments "
                                + "WHERE barber_id=? AND id<>? AND status IN ('SCHEDULED','IN_PROGRESS') "
                                + "AND starts_at<? AND ends_at>?)",
                        Boolean.class, barberId, id, utc(end), utc(start));
                if (Boolean.TRUE.equals(busy)) {
                    throw new ScheduleConflictException(ConflictType.APPOINTMENT_OVERLAP);
                }
                requireNoBlock(barberId, start, end);
                requireNoRecurringBlock(barberId, start, end, zone);
                jdbc.update("UPDATE appointments SET client_id=?,service_id=?,starts_at=?,ends_at=?,"
                                + "service_name_snapshot=?,price_snapshot=?,notes=?,version=version+1 "
                                + "WHERE barber_id=? AND id=?",
                        clientId, serviceId, utc(start), utc(end),
                        service.name(), price == null ? service.price() : price, notes, barberId, id);
            });
        } catch (DataIntegrityViolationException error) {
            if (hasSqlState(error, "23P01")) {
                throw new ScheduleConflictException(ConflictType.APPOINTMENT_OVERLAP);
            }
            throw error;
        }
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

    private void requireNoRecurringBlock(UUID barberId, Instant start, Instant end, ZoneId zone) {
        LocalDate first = start.atZone(zone).toLocalDate();
        LocalDate last = end.atZone(zone).toLocalDate();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (date.equals(last) && !date.equals(first)
                    && end.atZone(zone).toLocalTime().equals(LocalTime.MIDNIGHT)) {
                continue;
            }
            LocalTime lower = date.equals(first) ? start.atZone(zone).toLocalTime() : LocalTime.MIN;
            LocalTime upper = date.equals(last) ? end.atZone(zone).toLocalTime() : LocalTime.MAX;
            Boolean conflict = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM recurring_blocks "
                            + "WHERE barber_id=? AND weekday=? AND valid_from<=? AND valid_until>=? "
                            + "AND starts_at<? AND ends_at>?)",
                    Boolean.class, barberId, date.getDayOfWeek().getValue(),
                    date, date, upper, lower);
            if (Boolean.TRUE.equals(conflict)) {
                throw new ScheduleConflictException(ConflictType.BLOCKED_PERIOD_OVERLAP);
            }
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
        OUTSIDE_WORKING_HOURS,
        IDEMPOTENCY_CONFLICT
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
