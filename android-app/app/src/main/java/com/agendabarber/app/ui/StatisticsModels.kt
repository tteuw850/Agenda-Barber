package com.agendabarber.app.ui

import com.agendabarber.app.network.ReportDashboardView
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** UI models derived from F03-02 reporting, without simulated production values. */
internal data class DailyRevenue(val day: Int, val amount: BigDecimal)

internal data class RankedService(val name: String, val completed: Int)

internal data class StatisticsSnapshot(
    val revenue: BigDecimal,
    val completedCuts: Int,
    val averageTicket: BigDecimal,
    val cancellations: Int,
    val dailyRevenue: List<DailyRevenue>,
    val topServices: List<RankedService>,
    val expenses: BigDecimal = BigDecimal.ZERO,
    val estimatedProfit: BigDecimal = BigDecimal.ZERO,
    val missingReceipts: Long = 0,
    val completedAppointments: Long = 0,
    val timezone: String = "America/Sao_Paulo"
)

internal sealed interface StatisticsState {
    data object Empty : StatisticsState
    data object Loading : StatisticsState
    data class Failed(val message: String) : StatisticsState
    data class Available(
        val snapshot: StatisticsSnapshot,
        val fromCache: Boolean = false,
        val synchronizedAt: Long? = null
    ) : StatisticsState
}

/** Only actual receipts from the API are shown; a zero total does not imply historical receipts were known. */
internal fun reportToStatistics(report: ReportDashboardView): StatisticsSnapshot {
    val summary = report.summary
    return StatisticsSnapshot(
        revenue = summary.revenue,
        completedCuts = Math.toIntExact(summary.haircuts),
        averageTicket = summary.averageTicket,
        cancellations = Math.toIntExact(summary.cancellations),
        dailyRevenue = report.daily.map {
            DailyRevenue(LocalDate.parse(it.date).dayOfMonth, it.revenue)
        },
        topServices = report.popularServices.map {
            RankedService(it.serviceName, Math.toIntExact(it.completedAppointments))
        },
        expenses = summary.expenses,
        estimatedProfit = summary.estimatedProfit,
        missingReceipts = summary.missingReceipts,
        completedAppointments = summary.completedAppointments,
        timezone = summary.timezone
    )
}

internal fun statisticsMonthCaption(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR")))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }

internal fun statisticsBarFraction(amount: BigDecimal, maximum: BigDecimal): Float {
    if (maximum.signum() <= 0 || amount.signum() <= 0) return 0f
    val fraction = amount.divide(maximum, 4, java.math.RoundingMode.HALF_UP).toFloat()
    return fraction.coerceIn(0f, 1f)
}

internal fun statisticsRankFraction(count: Int, maximum: Int): Float =
    if (count <= 0 || maximum <= 0) 0f else (count.toFloat() / maximum).coerceIn(0f, 1f)

/** Explicitly fictitious, shown only when the user enables the demonstration. */
internal fun exampleStatistics(month: YearMonth): StatisticsSnapshot {
    val entries = listOf(
        2 to "240.00",
        5 to "390.00",
        9 to "150.00",
        13 to "480.00",
        18 to "310.00",
        22 to "540.00",
        27 to "420.00"
    )
    return StatisticsSnapshot(
        revenue = BigDecimal("2530.00"),
        completedCuts = 46,
        averageTicket = BigDecimal("55.00"),
        cancellations = 3,
        dailyRevenue = entries.filter { it.first <= month.lengthOfMonth() }
            .map { (day, amount) -> DailyRevenue(day, BigDecimal(amount)) },
        topServices = listOf(
            RankedService("Corte masculino", 24),
            RankedService("Corte e barba", 15),
            RankedService("Barba", 7)
        )
    )
}
