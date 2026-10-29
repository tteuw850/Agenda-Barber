package com.agendabarber.backend.appointments;

import com.agendabarber.backend.appointments.AppointmentDtos.AppointmentView;
import com.agendabarber.backend.appointments.AppointmentDtos.CreateAppointment;
import com.agendabarber.backend.appointments.AppointmentDtos.HistoryItem;
import com.agendabarber.backend.scheduling.AgendaSchedulingService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** JWT-derived tenant isolation, atomic state changes and idempotent bookings. */
@Service
@Profile("neon")
public class AppointmentService {
    private static final Set<String> STATUSES =
            Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "NO_SHOW");
    private static final Set<String> EDITABLE =
            Set.of("clientId", "serviceId", "startsAt", "price", "notes");
    private static final String SELECT = "SELECT a.id,a.client_id,c.name AS client_name,"
            + "c.phone AS client_phone,a.service_id,a.service_name_snapshot,a.price_snapshot,"
            + "a.starts_at,a.ends_at,a.status::text AS state,a.notes,a.completed_at,"
            + "a.version,a.created_at,a.updated_at,a.received_amount FROM appointments a "
            + "JOIN clients c ON c.id=a.client_id AND c.barber_id=a.barber_id ";
    private static final RowMapper<AppointmentView> MAPPER = (rs, index) -> map(rs);

    private final JdbcTemplate jdbc;
    private final AgendaSchedulingService scheduling;

    public AppointmentService(JdbcTemplate jdbc, AgendaSchedulingService scheduling) {
        this.jdbc = jdbc;
        this.scheduling = scheduling;
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> list(UUID barberId, LocalDate date,
                                       String view, String status, int limit, int offset) {
        if (limit < 1 || limit > 500 || offset < 0 || offset > 10000) {
            throw new InvalidAppointmentException();
        }
        LocalDate first;
        LocalDate last;
        switch (view) {
            case "day" -> {
                first = date;
                last = date.plusDays(1);
            }
            case "week" -> {
                first = date.with(java.time.DayOfWeek.MONDAY);
                last = first.plusWeeks(1);
            }
            case "month" -> {
                first = date.with(TemporalAdjusters.firstDayOfMonth());
                last = first.plusMonths(1);
            }
            default -> throw new InvalidAppointmentException();
        }
        if (!"all".equals(status) && !STATUSES.contains(status)) {
            throw new InvalidAppointmentException();
        }
        ZoneId zone = timezone(barberId);
        Instant from = first.atStartOfDay(zone).toInstant();
        Instant to = last.atStartOfDay(zone).toInstant();
        String sql = SELECT + "WHERE a.barber_id=? AND a.starts_at>=? AND a.starts_at<?"
                + ("all".equals(status) ? "" : " AND a.status::text=?")
                + " ORDER BY a.starts_at,a.id LIMIT ? OFFSET ?";
        if ("all".equals(status)) {
            return jdbc.query(sql, MAPPER, barberId, from.atOffset(ZoneOffset.UTC),
                    to.atOffset(ZoneOffset.UTC), limit, offset);
        }
        return jdbc.query(sql, MAPPER, barberId, from.atOffset(ZoneOffset.UTC),
                to.atOffset(ZoneOffset.UTC), status, limit, offset);
    }

    @Transactional(readOnly = true)
    public AppointmentView get(UUID barberId, UUID appointmentId) {
        try {
            return jdbc.queryForObject(SELECT + "WHERE a.barber_id=? AND a.id=?",
                    MAPPER, barberId, appointmentId);
        } catch (EmptyResultDataAccessException ignored) {
            throw new AppointmentNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(UUID barberId, UUID appointmentId) {
        get(barberId, appointmentId);
        return jdbc.query("SELECT id,event_type,previous_status::text,new_status::text,happened_at "
                        + "FROM appointment_events WHERE barber_id=? AND appointment_id=? "
                        + "ORDER BY happened_at,id",
                (rs, n) -> new HistoryItem(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()),
                barberId, appointmentId);
    }

    public AppointmentView create(UUID barberId, UUID userId, String key, CreateAppointment request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new InvalidAppointmentException();
        }
        // The scheduler owns the transaction. The booking, CREATED event and
        // idempotency row all commit or roll back together before returning.
        UUID id;
        try {
            id = scheduling.bookAtomic(barberId, userId, request.clientId(), request.serviceId(),
                    request.startsAt(), request.price(), cleanNotes(request.notes()),
                    key, hash(request));
        } catch (IllegalArgumentException unknownClientOrService) {
            throw new AppointmentNotFoundException();
        }
        return get(barberId, id);
    }

    @Transactional
    public AppointmentView edit(UUID barberId, UUID id, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty() || !EDITABLE.containsAll(patch.keySet())) {
            throw new InvalidAppointmentException();
        }
        AppointmentView current = get(barberId, id);
        UUID clientId = patch.containsKey("clientId")
                ? asUuid(patch.get("clientId")) : current.clientId();
        UUID serviceId = patch.containsKey("serviceId")
                ? asUuid(patch.get("serviceId")) : current.serviceId();
        Instant start = patch.containsKey("startsAt")
                ? asInstant(patch.get("startsAt")) : current.startsAt();
        BigDecimal price = patch.containsKey("price")
                ? asPrice(patch.get("price"))
                : (serviceId.equals(current.serviceId()) ? current.price() : null);
        String notes = patch.containsKey("notes")
                ? cleanNotes(asNullableString(patch.get("notes"))) : current.notes();
        try {
            scheduling.reschedule(barberId, id, clientId, serviceId, start, price, notes);
        } catch (IllegalArgumentException invalid) {
            throw new AppointmentNotFoundException();
        }
        event(barberId, id, "EDITED", "SCHEDULED", "SCHEDULED");
        return get(barberId, id);
    }

    /**
     * Actual revenue is established only by an explicit amount at completion.
     * Retries of the same amount are safe; changing a completed receipt is a conflict.
     */
    @Transactional
    public AppointmentView complete(UUID barberId, UUID id, BigDecimal amount) {
        BigDecimal received = validateReceivedAmount(amount);
        jdbc.queryForObject("SELECT id FROM barbers WHERE id=? FOR UPDATE", UUID.class, barberId);
        AppointmentView current = get(barberId, id);
        if ("COMPLETED".equals(current.status())) {
            if (current.receivedAmount() != null
                    && current.receivedAmount().compareTo(received) == 0) {
                return current;
            }
            throw new AppointmentConflictException();
        }
        if (!"IN_PROGRESS".equals(current.status())) {
            throw new AppointmentConflictException();
        }
        jdbc.update("UPDATE appointments SET status='COMPLETED', completed_at=now(),"
                        + "received_amount=?,version=version+1 WHERE barber_id=? AND id=?",
                received, barberId, id);
        event(barberId, id, "COMPLETED", current.status(), "COMPLETED");
        return get(barberId, id);
    }

    public static BigDecimal validateReceivedAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 10) {
            throw new InvalidAppointmentException();
        }
        return amount.setScale(2);
    }

    @Transactional
    public AppointmentView transition(UUID barberId, UUID id, String next) {
        if ("COMPLETED".equals(next)) {
            throw new InvalidAppointmentException();
        }
        // The barber lock also serializes state updates with rescheduling.
        jdbc.queryForObject("SELECT id FROM barbers WHERE id=? FOR UPDATE", UUID.class, barberId);
        AppointmentView current = get(barberId, id);
        if (current.status().equals(next)) {
            return current; // An HTTP retry must not duplicate the event.
        }
        boolean allowed = switch (next) {
            case "IN_PROGRESS" -> "SCHEDULED".equals(current.status());
            case "COMPLETED" -> "IN_PROGRESS".equals(current.status());
            case "CANCELLED" -> "SCHEDULED".equals(current.status())
                    || "IN_PROGRESS".equals(current.status());
            default -> false;
        };
        if (!allowed) {
            throw new AppointmentConflictException();
        }
        jdbc.update("UPDATE appointments SET status=?::appointment_status,"
                        + "completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END,"
                        + "version=version+1 WHERE barber_id=? AND id=?",
                next, next, barberId, id);
        String type = switch (next) {
            case "IN_PROGRESS" -> "STARTED";
            case "COMPLETED" -> "COMPLETED";
            default -> "CANCELLED";
        };
        event(barberId, id, type, current.status(), next);
        return get(barberId, id);
    }

    private void event(UUID barberId, UUID id, String kind, String before, String after) {
        jdbc.update("INSERT INTO appointment_events "
                        + "(barber_id,appointment_id,event_type,previous_status,new_status) "
                        + "VALUES(?,?,?,?::appointment_status,?::appointment_status)",
                barberId, id, kind, before, after);
    }

    private ZoneId timezone(UUID barberId) {
        return ZoneId.of(jdbc.queryForObject(
                "SELECT timezone FROM barbers WHERE id=?", String.class, barberId));
    }

    private static AppointmentView map(ResultSet rs) throws SQLException {
        Instant start = rs.getTimestamp("starts_at").toInstant();
        Instant end = rs.getTimestamp("ends_at").toInstant();
        java.sql.Timestamp completed = rs.getTimestamp("completed_at");
        return new AppointmentView(
                rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class),
                rs.getString("client_name"), rs.getString("client_phone"),
                rs.getObject("service_id", UUID.class), rs.getString("service_name_snapshot"),
                Math.toIntExact(Duration.between(start, end).toMinutes()),
                rs.getBigDecimal("price_snapshot"), start, end,
                rs.getString("state"), rs.getString("notes"),
                completed == null ? null : completed.toInstant(),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getBigDecimal("received_amount"));
    }

    private static UUID asUuid(Object value) {
        if (!(value instanceof String text)) {
            throw new InvalidAppointmentException();
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            throw new InvalidAppointmentException();
        }
    }

    private static Instant asInstant(Object value) {
        if (!(value instanceof String text)) {
            throw new InvalidAppointmentException();
        }
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException ignored) {
            throw new InvalidAppointmentException();
        }
    }

    private static String asNullableString(Object value) {
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new InvalidAppointmentException();
    }

    private static String cleanNotes(String notes) {
        if (notes == null) {
            return null;
        }
        if (notes.length() > 2000) {
            throw new InvalidAppointmentException();
        }
        return notes.strip();
    }

    private static BigDecimal asPrice(Object value) {
        if (!(value instanceof Number)) {
            throw new InvalidAppointmentException();
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            throw new InvalidAppointmentException();
        }
        if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 10) {
            throw new InvalidAppointmentException();
        }
        return amount;
    }

    private static String hash(CreateAppointment request) {
        String canonical = request.clientId() + "|" + request.serviceId() + "|"
                + request.startsAt() + "|" + request.price() + "|" + request.notes();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public static final class AppointmentNotFoundException extends RuntimeException {
        public AppointmentNotFoundException() {
            super("Appointment or selection not found");
        }
    }

    public static final class InvalidAppointmentException extends RuntimeException {
        public InvalidAppointmentException() {
            super("Invalid appointment data");
        }
    }

    public static final class AppointmentConflictException extends RuntimeException {
        public AppointmentConflictException() {
            super("Appointment state or idempotency conflict");
        }
    }
}
