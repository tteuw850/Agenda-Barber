package com.agendabarber.backend.appointments;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded, tenant-scoped weekly booking series.
 * All writes (series, appointments, events and idempotency) are atomic.
 * No background job, recurring infinite inserts or revenue generation.
 */
@Service
@Profile("neon")
public class AppointmentRecurrenceService {
    public record Request(UUID clientId, UUID serviceId, LocalDate startsOn,
                          LocalTime localTime, List<Integer> weekdays, int weeks,
                          BigDecimal price, String notes) {
    }

    public record Occurrence(LocalDate date, Instant startsAt, String state) {
    }

    public record Preview(List<Occurrence> occurrences, int available, int unavailable) {
    }

    public record Series(UUID id, LocalDate startsOn, LocalTime localTime,
                         List<Integer> weekdays, int weeks, long totalAppointments,
                         long scheduledAppointments, Instant cancelledAt) {
    }

    private record Span(Instant start, Instant end) {
        boolean conflicts(Instant from, Instant to) {
            return from.isBefore(end) && to.isAfter(start);
        }
    }

    private record Working(int weekday, boolean enabled, LocalTime open, LocalTime close) {
        boolean contains(int day, LocalTime start, LocalTime end) {
            return enabled && weekday == day && !start.isBefore(open) && !end.isAfter(close);
        }
    }

    private record WeeklyBlock(int weekday, LocalTime start, LocalTime end,
                               LocalDate validFrom, LocalDate validUntil) {
        boolean conflicts(LocalDate date, LocalTime from, LocalTime to) {
            return weekday == date.getDayOfWeek().getValue() && !validFrom.isAfter(date)
                    && !validUntil.isBefore(date) && from.isBefore(end) && to.isAfter(start);
        }
    }

    private final JdbcTemplate jdbc;

    public AppointmentRecurrenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record NewBooking(UUID id, Occurrence occurrence) {
    }

    /** Pure expansion in the barber's local calendar; caps guard against runaways. */
    static List<LocalDate> dates(Request request) {
        validate(request);
        List<LocalDate> result = new ArrayList<>();
        Set<Integer> selected = Set.copyOf(request.weekdays());
        LocalDate end = request.startsOn().plusWeeks(request.weeks());
        for (LocalDate d = request.startsOn(); d.isBefore(end); d = d.plusDays(1)) {
            if (selected.contains(d.getDayOfWeek().getValue())) {
                result.add(d);
            }
        }
        if (result.isEmpty() || result.size() > 60) {
            throw new InvalidRecurrenceException();
        }
        return result;
    }

    private static void validate(Request request) {
        if (request == null || request.clientId() == null || request.serviceId() == null
                || request.startsOn() == null || request.localTime() == null
                || request.weekdays() == null || !Set.of(4, 12, 26).contains(request.weeks())
                || request.weekdays().isEmpty() || request.weekdays().size() > 7
                || request.weekdays().stream().anyMatch(d -> d == null || d < 1 || d > 7)
                || request.weekdays().stream().distinct().count() != request.weekdays().size()
                || request.localTime().getSecond() != 0 || request.localTime().getNano() != 0
                || request.notes() != null && request.notes().length() > 2000
                || request.price() != null && (request.price().signum() < 0
                    || request.price().scale() > 2 || request.price().precision()
                    - request.price().scale() > 10)
                || request.startsOn().isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(2))
                || request.startsOn().isAfter(LocalDate.now(ZoneOffset.UTC).plusYears(1))) {
            throw new InvalidRecurrenceException();
        }
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID barberId, Request request) {
        List<LocalDate> days = dates(request);
        ZoneId zone = zone(barberId);
        Boolean clientExists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM clients WHERE id=? AND barber_id=? AND active=true)",
                Boolean.class, request.clientId(), barberId);
        if (!Boolean.TRUE.equals(clientExists)) {
            throw new AppointmentService.AppointmentNotFoundException();
        }
        Integer minutes;
        try {
            minutes = jdbc.queryForObject(
                    "SELECT duration_minutes FROM services WHERE id=? AND barber_id=? AND active=true",
                    Integer.class, request.serviceId(), barberId);
        } catch (EmptyResultDataAccessException notFound) {
            throw new AppointmentService.AppointmentNotFoundException();
        }
        if (minutes == null || minutes < 1) {
            throw new InvalidRecurrenceException();
        }
        List<Working> hours = jdbc.query(
                "SELECT weekday,enabled,opens_at,closes_at FROM working_hours WHERE barber_id=?",
                (rs, n) -> new Working(rs.getInt(1), rs.getBoolean(2),
                        rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)),
                barberId);
        Instant from = request.startsOn().atStartOfDay(zone).toInstant();
        Instant through = request.startsOn().plusWeeks(request.weeks()).atStartOfDay(zone).toInstant();
        List<Span> occupied = jdbc.query(
                "SELECT starts_at,ends_at FROM appointments "
                    + "WHERE barber_id=? AND status IN ('SCHEDULED','IN_PROGRESS') "
                    + "AND starts_at<? AND ends_at>? "
                    + "UNION ALL SELECT starts_at,ends_at FROM blocked_periods "
                    + "WHERE barber_id=? AND starts_at<? AND ends_at>?",
                (rs, n) -> new Span(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()),
                barberId, utc(through), utc(from), barberId, utc(through), utc(from));
        List<WeeklyBlock> blocks = jdbc.query(
                "SELECT weekday,starts_at,ends_at,valid_from,valid_until FROM recurring_blocks "
                    + "WHERE barber_id=? AND valid_until>=? AND valid_from<?",
                (rs, n) -> new WeeklyBlock(rs.getInt(1),
                        rs.getObject(2, LocalTime.class), rs.getObject(3, LocalTime.class),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)),
                barberId, request.startsOn(), request.startsOn().plusWeeks(request.weeks()));
        List<Occurrence> result = new ArrayList<>();
        Instant now = Instant.now();
        for (LocalDate date : days) {
            LocalDateTime local = LocalDateTime.of(date, request.localTime());
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) {
                result.add(new Occurrence(date, null, "DST_INVALID"));
                continue;
            }
            Instant start = ZonedDateTime.ofLocal(local, zone, offsets.get(0)).toInstant();
            Instant end = start.plus(Duration.ofMinutes(minutes));
            LocalTime localEnd = end.atZone(zone).toLocalTime();
            String state = "AVAILABLE";
            if (!start.isAfter(now)) {
                state = "PAST";
            } else if (!start.atZone(zone).toLocalDate().equals(end.atZone(zone).toLocalDate())
                    || hours.stream().noneMatch(h -> h.contains(date.getDayOfWeek().getValue(),
                        request.localTime(), localEnd))) {
                state = "OUTSIDE_HOURS";
            } else if (occupied.stream().anyMatch(span -> span.conflicts(start, end))) {
                state = "OCCUPIED";
            } else if (blocks.stream().anyMatch(block ->
                    block.conflicts(date, request.localTime(), localEnd))) {
                state = "BLOCKED";
            }
            result.add(new Occurrence(date, start, state));
        }
        int available = (int) result.stream().filter(o -> "AVAILABLE".equals(o.state())).count();
        return new Preview(result, available, result.size() - available);
    }

    /**
     * Parent transaction is REQUIRED: the nested scheduler TransactionTemplate joins it.
     * If one future date is blocked, all of the series rolls back (no partial bookings).
     */
    @Transactional
    public Series create(UUID barberId, UUID userId, String key, Request request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new InvalidRecurrenceException();
        }
        List<LocalDate> days = dates(request);
        String fingerprint = hash(request.clientId() + "|" + request.serviceId() + "|"
                + request.startsOn() + "|" + request.localTime() + "|"
                + request.weekdays().stream().sorted().toList() + "|" + request.weeks()
                + "|" + request.price() + "|" + request.notes());
        // Serializes with all normal appointment/block writers for this barber.
        jdbc.queryForObject("SELECT timezone FROM barbers WHERE id=? FOR UPDATE", String.class, barberId);
        List<UUID> prior = jdbc.query(
                "SELECT id FROM appointment_recurrences WHERE user_id=? AND request_key=?",
                (rs, n) -> rs.getObject(1, UUID.class), userId, key);
        if (!prior.isEmpty()) {
            String oldFingerprint = jdbc.queryForObject(
                    "SELECT request_hash FROM appointment_recurrences WHERE id=? AND barber_id=?",
                    String.class, prior.get(0), barberId);
            if (!fingerprint.equals(oldFingerprint)) {
                throw new RecurrenceConflictException();
            }
            return get(barberId, prior.get(0));
        }
        Preview check = preview(barberId, request);
        if (check.unavailable() > 0) {
            throw new RecurrenceConflictException();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_recurrences "
                    + "(id,barber_id,user_id,client_id,service_id,starts_on,local_time,"
                    + "weekdays,weeks,price,notes,request_key,request_hash) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, barberId, userId, request.clientId(), request.serviceId(),
                request.startsOn(), request.localTime(),
                request.weekdays().stream().sorted().map(String::valueOf)
                        .reduce((a, b) -> a + "," + b).orElseThrow(),
                request.weeks(), request.price(), request.notes(), key, fingerprint);
        // Under the same barber lock, write the bounded series in two JDBC batches.
        // The PostgreSQL exclusion constraint remains the last concurrency safeguard.
        var service = jdbc.queryForMap(
                "SELECT name,duration_minutes,default_price FROM services "
                        + "WHERE barber_id=? AND id=? AND active=true",
                barberId, request.serviceId());
        String serviceName = (String) service.get("name");
        int durationMinutes = ((Number) service.get("duration_minutes")).intValue();
        BigDecimal price = request.price() == null
                ? (BigDecimal) service.get("default_price") : request.price();
        List<NewBooking> bookings = check.occurrences().stream()
                .map(occurrence -> new NewBooking(UUID.randomUUID(), occurrence)).toList();
        try {
            jdbc.batchUpdate("INSERT INTO appointments "
                            + "(id,barber_id,client_id,service_id,starts_at,ends_at,"
                            + "service_name_snapshot,price_snapshot,notes,status,recurrence_id) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,'SCHEDULED'::appointment_status,?)",
                    bookings, bookings.size(), (ps, booking) -> {
                        ps.setObject(1, booking.id());
                        ps.setObject(2, barberId);
                        ps.setObject(3, request.clientId());
                        ps.setObject(4, request.serviceId());
                        ps.setObject(5, utc(booking.occurrence().startsAt()));
                        ps.setObject(6, utc(booking.occurrence().startsAt()
                                .plus(Duration.ofMinutes(durationMinutes))));
                        ps.setString(7, serviceName);
                        ps.setBigDecimal(8, price);
                        ps.setString(9, request.notes());
                        ps.setObject(10, id);
                    });
            jdbc.batchUpdate("INSERT INTO appointment_events "
                            + "(barber_id,appointment_id,event_type,previous_status,new_status) "
                            + "VALUES(?,?,'CREATED',NULL,'SCHEDULED'::appointment_status)",
                    bookings, bookings.size(), (ps, booking) -> {
                        ps.setObject(1, barberId);
                        ps.setObject(2, booking.id());
                    });
        } catch (DataIntegrityViolationException collision) {
            throw new RecurrenceConflictException();
        }
        return get(barberId, id);
    }

    @Transactional(readOnly = true)
    public List<Series> list(UUID barberId) {
        return jdbc.query(seriesSelect()
                        + "WHERE r.barber_id=? ORDER BY r.created_at DESC LIMIT 100",
                (rs, n) -> map(rs), barberId);
    }

    @Transactional(readOnly = true)
    public Series get(UUID barberId, UUID id) {
        List<Series> results = jdbc.query(seriesSelect() + "WHERE r.barber_id=? AND r.id=?",
                (rs, n) -> map(rs), barberId, id);
        if (results.isEmpty()) {
            throw new RecurrenceNotFoundException();
        }
        return results.get(0);
    }

    @Transactional
    public Series cancelFuture(UUID barberId, UUID seriesId) {
        jdbc.queryForObject("SELECT timezone FROM barbers WHERE id=? FOR UPDATE",
                String.class, barberId);
        get(barberId, seriesId);
        // Audit every cancelled occurrence, never change COMPLETED or IN_PROGRESS.
        jdbc.update("WITH cancelled AS ("
                    + "UPDATE appointments SET status='CANCELLED',version=version+1 "
                    + "WHERE barber_id=? AND recurrence_id=? AND status='SCHEDULED' "
                    + "AND starts_at>now() RETURNING id) "
                    + "INSERT INTO appointment_events "
                    + "(barber_id,appointment_id,event_type,previous_status,new_status) "
                    + "SELECT ?,id,'CANCELLED','SCHEDULED'::appointment_status,"
                    + "'CANCELLED'::appointment_status FROM cancelled",
                barberId, seriesId, barberId);
        jdbc.update("UPDATE appointment_recurrences SET cancelled_at=COALESCE(cancelled_at,now()) "
                    + "WHERE id=? AND barber_id=?", seriesId, barberId);
        return get(barberId, seriesId);
    }

    private String seriesSelect() {
        return "SELECT r.id,r.starts_on,r.local_time,r.weekdays,r.weeks,r.cancelled_at,"
                + "(SELECT count(*) FROM appointments a WHERE a.recurrence_id=r.id) AS total,"
                + "(SELECT count(*) FROM appointments a WHERE a.recurrence_id=r.id "
                + "AND a.status='SCHEDULED') AS scheduled FROM appointment_recurrences r ";
    }

    private Series map(java.sql.ResultSet rs) throws java.sql.SQLException {
        String days = rs.getString("weekdays");
        java.sql.Timestamp cancelled = rs.getTimestamp("cancelled_at");
        return new Series(rs.getObject("id", UUID.class),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("local_time", LocalTime.class),
                java.util.Arrays.stream(days.split(",")).map(Integer::parseInt).toList(),
                rs.getInt("weeks"), rs.getLong("total"), rs.getLong("scheduled"),
                cancelled == null ? null : cancelled.toInstant());
    }

    private ZoneId zone(UUID barberId) {
        String zone = jdbc.queryForObject("SELECT timezone FROM barbers WHERE id=?",
                String.class, barberId);
        if (zone == null) throw new RecurrenceNotFoundException();
        return ZoneId.of(zone);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static final class InvalidRecurrenceException extends RuntimeException {
    }

    public static final class RecurrenceNotFoundException extends RuntimeException {
    }

    public static final class RecurrenceConflictException extends RuntimeException {
    }
}
