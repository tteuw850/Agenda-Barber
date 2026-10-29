package com.agendabarber.backend.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Financial reporting values come from stored receipts, never booking price snapshots. */
public final class ReportDtos {
    private ReportDtos() {
    }

    public record Summary(
            LocalDate from, LocalDate to, String timezone,
            long completedAppointments, long haircuts, long receiptedAppointments,
            long missingReceipts, BigDecimal revenue, BigDecimal expenses,
            BigDecimal estimatedProfit, BigDecimal averageTicket) {
    }

    public record DailyMetric(
            LocalDate date, long completedAppointments, long haircuts,
            BigDecimal revenue, BigDecimal expenses, BigDecimal estimatedProfit) {
    }

    public record PopularService(
            UUID serviceId, String serviceName, long completedAppointments,
            long haircuts, BigDecimal revenue) {
    }

    public record Dashboard(Summary summary, List<DailyMetric> daily,
                            List<PopularService> popularServices) {
    }
}