package com.agendabarber.backend.reports;

import com.agendabarber.backend.reports.ReportDtos.DailyMetric;
import com.agendabarber.backend.reports.ReportDtos.Dashboard;
import com.agendabarber.backend.reports.ReportDtos.PopularService;
import com.agendabarber.backend.reports.ReportDtos.Summary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F03-02: read-only owner-scoped summaries using actual receipts and soft-delete-aware expenses. */
@Service
@Profile("neon")
public class ReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String COMPLETED = " FROM appointments a"
            + " JOIN services s ON s.id=a.service_id AND s.barber_id=a.barber_id"
            + " WHERE a.barber_id=? AND a.status='COMPLETED'"
            + " AND a.completed_at>=? AND a.completed_at<?";
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(UUID barberId, LocalDate from, LocalDate to) {
        Period period = period(barberId, from, to);
        return new Dashboard(summary(barberId, period), daily(barberId, period),
                popularServices(barberId, period, 10));
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID barberId, LocalDate from, LocalDate to) {
        return summary(barberId, period(barberId, from, to));
    }

    @Transactional(readOnly = true)
    public List<DailyMetric> daily(UUID barberId, LocalDate from, LocalDate to) {
        return daily(barberId, period(barberId, from, to));
    }

    @Transactional(readOnly = true)
    public List<PopularService> popularServices(
            UUID barberId, LocalDate from, LocalDate to, int limit) {
        if (limit < 1 || limit > 50) {
            throw new InvalidReportException();
        }
        return popularServices(barberId, period(barberId, from, to), limit);
    }

    private Summary summary(UUID barberId, Period period) {
        Counts counts = jdbc.queryForObject(
                "SELECT count(*) AS completed,"
                        + " count(*) FILTER (WHERE s.is_haircut) AS haircuts,"
                        + " count(*) FILTER (WHERE a.received_amount IS NOT NULL) AS receipted,"
                        + " count(*) FILTER (WHERE a.received_amount IS NULL) AS missing,"
                        + " COALESCE(sum(a.received_amount),0) AS revenue"
                        + COMPLETED,
                (rs, ignored) -> new Counts(rs.getLong("completed"), rs.getLong("haircuts"),
                        rs.getLong("receipted"), rs.getLong("missing"),
                        rs.getBigDecimal("revenue")),
                barberId, period.start(), period.end());
        BigDecimal expenses = jdbc.queryForObject(
                "SELECT COALESCE(sum(amount),0) FROM expenses"
                        + " WHERE barber_id=? AND deleted_at IS NULL"
                        + " AND occurred_on BETWEEN ? AND ?",
                BigDecimal.class, barberId, period.from(), period.to());
        BigDecimal revenue = money(counts.revenue());
        BigDecimal totalExpenses = money(expenses);
        BigDecimal ticket = counts.receipted() == 0 ? ZERO
                : revenue.divide(BigDecimal.valueOf(counts.receipted()), 2, RoundingMode.HALF_UP);
        return new Summary(period.from(), period.to(), period.zone().getId(),
                counts.completed(), counts.haircuts(), counts.receipted(),
                counts.missing(), revenue, totalExpenses, revenue.subtract(totalExpenses), ticket);
    }

    private List<DailyMetric> daily(UUID barberId, Period period) {
        Map<LocalDate, Counts> appointments = new HashMap<>();
        jdbc.query("SELECT (a.completed_at AT TIME ZONE ?)::date AS local_day,"
                        + " count(*) AS completed,"
                        + " count(*) FILTER (WHERE s.is_haircut) AS haircuts,"
                        + " COALESCE(sum(a.received_amount),0) AS revenue"
                        + COMPLETED + " GROUP BY local_day",
                (RowCallbackHandler) rs -> appointments.put(rs.getObject("local_day", LocalDate.class),
                        new Counts(rs.getLong("completed"), rs.getLong("haircuts"),
                                0, 0, rs.getBigDecimal("revenue"))),
                period.zone().getId(), barberId, period.start(), period.end());
        Map<LocalDate, BigDecimal> expenses = new HashMap<>();
        jdbc.query("SELECT occurred_on, sum(amount) AS total FROM expenses"
                        + " WHERE barber_id=? AND deleted_at IS NULL"
                        + " AND occurred_on BETWEEN ? AND ? GROUP BY occurred_on",
                (RowCallbackHandler) rs -> expenses.put(rs.getObject("occurred_on", LocalDate.class),
                        rs.getBigDecimal("total")),
                barberId, period.from(), period.to());
        List<DailyMetric> result = new ArrayList<>();
        long totalDays = ChronoUnit.DAYS.between(period.from(), period.to());
        for (long offset = 0; offset <= totalDays; offset++) {
            LocalDate date = period.from().plusDays(offset);
            Counts day = appointments.get(date);
            BigDecimal revenue = day == null ? ZERO : money(day.revenue());
            BigDecimal expense = money(expenses.get(date));
            result.add(new DailyMetric(date,
                    day == null ? 0 : day.completed(), day == null ? 0 : day.haircuts(),
                    revenue, expense, revenue.subtract(expense)));
        }
        return List.copyOf(result);
    }

    private List<PopularService> popularServices(UUID barberId, Period period, int limit) {
        return jdbc.query("SELECT a.service_id, a.service_name_snapshot,"
                        + " count(*) AS completed,"
                        + " count(*) FILTER (WHERE s.is_haircut) AS haircuts,"
                        + " COALESCE(sum(a.received_amount),0) AS revenue"
                        + COMPLETED
                        + " GROUP BY a.service_id, a.service_name_snapshot"
                        + " ORDER BY completed DESC,a.service_name_snapshot,a.service_id LIMIT ?",
                (rs, ignored) -> new PopularService(
                        rs.getObject("service_id", UUID.class),
                        rs.getString("service_name_snapshot"), rs.getLong("completed"),
                        rs.getLong("haircuts"), money(rs.getBigDecimal("revenue"))),
                barberId, period.start(), period.end(), limit);
    }

    private Period period(UUID barberId, LocalDate from, LocalDate to) {
        if (barberId == null || from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new InvalidReportException();
        }
        try {
            String timezone = jdbc.queryForObject(
                    "SELECT timezone FROM barbers WHERE id=?", String.class, barberId);
            ZoneId zone = ZoneId.of(timezone);
            return new Period(from, to, zone,
                    from.atStartOfDay(zone).toInstant().atOffset(ZoneOffset.UTC),
                    to.plusDays(1).atStartOfDay(zone).toInstant().atOffset(ZoneOffset.UTC));
        } catch (EmptyResultDataAccessException ignored) {
            throw new ReportNotFoundException();
        } catch (DateTimeException ignored) {
            throw new InvalidReportException();
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private record Period(
            LocalDate from, LocalDate to, ZoneId zone,
            OffsetDateTime start, OffsetDateTime end) {
    }

    private record Counts(
            long completed, long haircuts, long receipted, long missing, BigDecimal revenue) {
    }

    public static class InvalidReportException extends RuntimeException {
    }

    public static class ReportNotFoundException extends RuntimeException {
    }
}
