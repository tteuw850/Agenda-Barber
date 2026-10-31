package com.agendabarber.backend.scheduleconfig;

import com.agendabarber.backend.scheduleconfig.ScheduleDtos.FreeSlot;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.SingleBlockInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.SingleBlockView;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WeeklyBlockInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WeeklyBlockView;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WorkingHoursInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WorkingHoursView;
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
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schedule configuration is serialized with appointment writers by locking barbers.id. */
@Service
@Profile("neon")
public class ScheduleConfigService {
    private final JdbcTemplate jdbc;

    public ScheduleConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursView> workingHours(UUID barberId) {
        return jdbc.query("SELECT weekday,enabled,opens_at,closes_at FROM working_hours "
                        + "WHERE barber_id=? ORDER BY weekday,opens_at",
                (rs, n) -> new WorkingHoursView(rs.getInt(1), rs.getBoolean(2),
                        rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)), barberId);
    }

    @Transactional
    public WorkingHoursView setHours(UUID barberId, int weekday, WorkingHoursInput input) {
        if (weekday < 1 || weekday > 7 || !input.opensAt().isBefore(input.closesAt())) {
            throw new InvalidScheduleException();
        }
        ZoneId zone = lockBarber(barberId);
        jdbc.query("SELECT starts_at,ends_at FROM appointments WHERE barber_id=? "
                        + "AND status IN ('SCHEDULED','IN_PROGRESS')",
                rs -> {
                    ZonedDateTime start = rs.getTimestamp(1).toInstant().atZone(zone);
                    ZonedDateTime end = rs.getTimestamp(2).toInstant().atZone(zone);
                    if (start.getDayOfWeek().getValue() == weekday
                            && (!input.enabled() || !start.toLocalDate().equals(end.toLocalDate())
                            || start.toLocalTime().isBefore(input.opensAt())
                            || end.toLocalTime().isAfter(input.closesAt()))) {
                        throw new ScheduleConflictException();
                    }
                }, barberId);
        jdbc.update("DELETE FROM working_hours WHERE barber_id=? AND weekday=?", barberId, weekday);
        jdbc.update("INSERT INTO working_hours(barber_id,weekday,enabled,opens_at,closes_at) "
                        + "VALUES(?,?,?,?,?)",
                barberId, weekday, input.enabled(), input.opensAt(), input.closesAt());
        return new WorkingHoursView(weekday, input.enabled(), input.opensAt(), input.closesAt());
    }

    @Transactional(readOnly = true)
    public List<SingleBlockView> blocks(UUID barberId, LocalDate date) {
        ZoneId zone = zone(barberId);
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        return jdbc.query("SELECT id,starts_at,ends_at,reason,notes FROM blocked_periods "
                        + "WHERE barber_id=? AND starts_at<? AND ends_at>? ORDER BY starts_at,id",
                (rs, n) -> new SingleBlockView(rs.getObject(1, UUID.class),
                        rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant(),
                        rs.getString(4), rs.getString(5)), barberId, utc(to), utc(from));
    }

    @Transactional(readOnly = true)
    public SingleBlockView getBlock(UUID barberId, UUID id) {
        List<SingleBlockView> found = jdbc.query(
                "SELECT id,starts_at,ends_at,reason,notes FROM blocked_periods"
                        + " WHERE barber_id=? AND id=?",
                (rs, n) -> new SingleBlockView(rs.getObject(1, UUID.class),
                        rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant(),
                        rs.getString(4), rs.getString(5)), barberId, id);
        if (found.isEmpty()) {
            throw new ScheduleNotFoundException();
        }
        return found.getFirst();
    }

    @Transactional
    public SingleBlockView createBlock(UUID barberId, SingleBlockInput input) {
        if (!input.endsAt().isAfter(input.startsAt())
                || Duration.between(input.startsAt(), input.endsAt()).toDays() > 31) {
            throw new InvalidScheduleException();
        }
        ZoneId zone = lockBarber(barberId);
        rejectOccupied(barberId, input.startsAt(), input.endsAt());
        if (recurringOverlaps(barberId, input.startsAt(), input.endsAt(), zone)) {
            throw new ScheduleConflictException();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO blocked_periods"
                + "(id,barber_id,starts_at,ends_at,reason,notes) VALUES(?,?,?,?,?,?)",
                id, barberId, utc(input.startsAt()), utc(input.endsAt()),
                input.reason(), input.notes());
        return new SingleBlockView(id, input.startsAt(), input.endsAt(),
                input.reason(), input.notes());
    }

    @Transactional
    public void deleteBlock(UUID barberId, UUID id) {
        lockBarber(barberId);
        if (jdbc.update("DELETE FROM blocked_periods WHERE barber_id=? AND id=?", barberId, id) != 1) {
            throw new ScheduleNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public List<WeeklyBlockView> recurring(UUID barberId) {
        return jdbc.query("SELECT id,weekday,starts_at,ends_at,valid_from,valid_until,"
                        + "reason,notes FROM recurring_blocks WHERE barber_id=? "
                        + "ORDER BY weekday,starts_at,id",
                (rs, n) -> new WeeklyBlockView(rs.getObject(1, UUID.class), rs.getInt(2),
                        rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class),
                        rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class),
                        rs.getString(7), rs.getString(8)), barberId);
    }

    @Transactional(readOnly = true)
    public WeeklyBlockView getRecurring(UUID barberId, UUID id) {
        List<WeeklyBlockView> found = jdbc.query(
                "SELECT id,weekday,starts_at,ends_at,valid_from,valid_until,reason,notes"
                        + " FROM recurring_blocks WHERE barber_id=? AND id=?",
                (rs, n) -> new WeeklyBlockView(rs.getObject(1, UUID.class), rs.getInt(2),
                        rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class),
                        rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class),
                        rs.getString(7), rs.getString(8)), barberId, id);
        if (found.isEmpty()) {
            throw new ScheduleNotFoundException();
        }
        return found.getFirst();
    }

    @Transactional
    public WeeklyBlockView createRecurring(UUID barberId, WeeklyBlockInput input) {
        if (!input.startsAt().isBefore(input.endsAt())
                || input.validFrom().isAfter(input.validUntil())
                || input.validUntil().isAfter(input.validFrom().plusDays(180))) {
            throw new InvalidScheduleException();
        }
        ZoneId zone = lockBarber(barberId);
        Boolean overlap = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM recurring_blocks "
                        + "WHERE barber_id=? AND weekday=? AND valid_from<=? AND valid_until>=? "
                        + "AND starts_at<? AND ends_at>?)",
                Boolean.class, barberId, input.weekday(), input.validUntil(), input.validFrom(),
                input.endsAt(), input.startsAt());
        if (Boolean.TRUE.equals(overlap)) {
            throw new ScheduleConflictException();
        }
        for (LocalDate day = input.validFrom(); !day.isAfter(input.validUntil()); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() == input.weekday()) {
                Instant start = resolved(day, input.startsAt(), zone);
                Instant end = resolved(day, input.endsAt(), zone);
                if (start == null || end == null) {
                    throw new InvalidScheduleException();
                }
                rejectOccupied(barberId, start, end);
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO recurring_blocks "
                        + "(id,barber_id,weekday,starts_at,ends_at,valid_from,valid_until,"
                        + "reason,notes) VALUES(?,?,?,?,?,?,?,?,?)",
                id, barberId, input.weekday(), input.startsAt(), input.endsAt(),
                input.validFrom(), input.validUntil(), input.reason(), input.notes());
        return new WeeklyBlockView(id, input.weekday(), input.startsAt(), input.endsAt(),
                input.validFrom(), input.validUntil(), input.reason(), input.notes());
    }

    @Transactional
    public void deleteRecurring(UUID barberId, UUID id) {
        lockBarber(barberId);
        if (jdbc.update("DELETE FROM recurring_blocks WHERE barber_id=? AND id=?", barberId, id) != 1) {
            throw new ScheduleNotFoundException();
        }
    }

    /** A read-only hint; booking must repeat the checks and commit under the barber lock. */
    @Transactional(readOnly = true)
    public List<FreeSlot> availableSlots(UUID barberId, LocalDate date, UUID serviceId) {
        ZoneId zone = zone(barberId);
        Integer duration;
        try {
            duration = jdbc.queryForObject("SELECT duration_minutes FROM services "
                            + "WHERE barber_id=? AND id=? AND active=true",
                    Integer.class, barberId, serviceId);
        } catch (EmptyResultDataAccessException ignored) {
            throw new ScheduleNotFoundException();
        }
        List<WorkingHoursView> schedule = workingHours(barberId);
        List<FreeSlot> result = new ArrayList<>();
        for (WorkingHoursView hours : schedule) {
            if (!hours.enabled() || hours.weekday() != date.getDayOfWeek().getValue()) {
                continue;
            }
            for (LocalTime time = hours.opensAt(); Duration.between(time, hours.closesAt()).toMinutes() >= duration;
                    time = time.plusMinutes(15)) {
                Instant from = resolved(date, time, zone);
                Instant to = resolved(date, time.plusMinutes(duration), zone);
                if (from != null && to != null && to.isAfter(from)
                        && !occupied(barberId, from, to)
                        && !recurringOverlaps(barberId, from, to, zone)) {
                    result.add(new FreeSlot(from, to, time));
                }
                if (time.isAfter(LocalTime.of(23, 30))) {
                    break;
                }
            }
        }
        return result;
    }

    private ZoneId lockBarber(UUID barberId) {
        String timezone = jdbc.queryForObject(
                "SELECT timezone FROM barbers WHERE id=? FOR UPDATE", String.class, barberId);
        return ZoneId.of(timezone);
    }

    private ZoneId zone(UUID barberId) {
        String timezone = jdbc.queryForObject(
                "SELECT timezone FROM barbers WHERE id=?", String.class, barberId);
        return ZoneId.of(timezone);
    }

    private boolean occupied(UUID barberId, Instant start, Instant end) {
        Boolean appointment = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments "
                        + "WHERE barber_id=? AND status IN ('SCHEDULED','IN_PROGRESS') "
                        + "AND starts_at<? AND ends_at>?)",
                Boolean.class, barberId, utc(end), utc(start));
        Boolean blocked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM blocked_periods "
                        + "WHERE barber_id=? AND starts_at<? AND ends_at>?)",
                Boolean.class, barberId, utc(end), utc(start));
        return Boolean.TRUE.equals(appointment) || Boolean.TRUE.equals(blocked);
    }

    private void rejectOccupied(UUID barberId, Instant start, Instant end) {
        if (occupied(barberId, start, end)) {
            throw new ScheduleConflictException();
        }
    }

    private boolean recurringOverlaps(UUID barberId, Instant start, Instant end, ZoneId zone) {
        LocalDate first = start.atZone(zone).toLocalDate();
        LocalDate last = end.atZone(zone).toLocalDate();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            List<WeeklyBlockView> matches = jdbc.query(
                    "SELECT id,weekday,starts_at,ends_at,valid_from,valid_until,reason,notes "
                            + "FROM recurring_blocks WHERE barber_id=? AND weekday=? "
                            + "AND valid_from<=? AND valid_until>=?",
                    (rs, n) -> new WeeklyBlockView(rs.getObject(1, UUID.class), rs.getInt(2),
                            rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class),
                            rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class),
                            rs.getString(7), rs.getString(8)),
                    barberId, day.getDayOfWeek().getValue(), day, day);
            for (WeeklyBlockView block : matches) {
                Instant from = resolved(day, block.startsAt(), zone);
                Instant to = resolved(day, block.endsAt(), zone);
                if (from != null && to != null && start.isBefore(to) && end.isAfter(from)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Instant resolved(LocalDate day, LocalTime time, ZoneId zone) {
        LocalDateTime local = day.atTime(time);
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) {
            return null;
        }
        return local.atOffset(offsets.get(0)).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public static final class InvalidScheduleException extends RuntimeException {
        public InvalidScheduleException() {
            super("Invalid scheduling settings");
        }
    }

    public static final class ScheduleNotFoundException extends RuntimeException {
        public ScheduleNotFoundException() {
            super("Scheduling item not found");
        }
    }

    public static final class ScheduleConflictException extends RuntimeException {
        public ScheduleConflictException() {
            super("Requested schedule conflicts with existing appointments or blocks");
        }
    }
}
