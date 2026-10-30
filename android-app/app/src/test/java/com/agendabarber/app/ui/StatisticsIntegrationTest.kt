package com.agendabarber.app.ui

import com.agendabarber.app.cache.AgendaCacheDao
import com.agendabarber.app.cache.AgendaSnapshot
import com.agendabarber.app.cache.StatisticsRepository
import com.agendabarber.app.network.ReportDailyView
import com.agendabarber.app.network.ReportDashboardView
import com.agendabarber.app.network.ReportPopularView
import com.agendabarber.app.network.ReportSummaryView
import java.io.IOException
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsIntegrationTest {
    private fun sample(): ReportDashboardView = ReportDashboardView(
        ReportSummaryView(
            "2030-04-01", "2030-04-30", "America/Sao_Paulo", 3, 2, 2, 1,
            BigDecimal("150.00"), BigDecimal("30.00"), BigDecimal("120.00"),
            BigDecimal("75.00"), 1
        ),
        listOf(ReportDailyView("2030-04-01", BigDecimal("150.00"))),
        listOf(ReportPopularView("service-1", "Corte", 2))
    )

    @Test
    fun mapsActualReceiptsAndKeepsUnknownHistoricalReceiptsVisible() {
        val snapshot = reportToStatistics(sample())
        assertEquals(BigDecimal("150.00"), snapshot.revenue)
        assertEquals(BigDecimal("120.00"), snapshot.estimatedProfit)
        assertEquals(BigDecimal("30.00"), snapshot.expenses)
        assertEquals(BigDecimal("75.00"), snapshot.averageTicket)
        assertEquals(2, snapshot.completedCuts)
        assertEquals(1, snapshot.cancellations)
        assertEquals(1L, snapshot.missingReceipts)
        assertEquals(3L, snapshot.completedAppointments)
        assertEquals(1, snapshot.dailyRevenue.first().day)
        assertEquals("Corte", snapshot.topServices.first().name)
    }

    @Test
    fun cachesByTokenAndPeriodButNeverForDifferentUsersOrMonths() = runBlocking {
        val dao = InMemoryDao()
        val month = YearMonth.of(2030, 4)
        val live = StatisticsRepository({ _, from, to ->
            assertEquals("2030-04-01", from)
            assertEquals("2030-04-30", to)
            sample()
        }, dao)
        val result = live.dashboard("Bearer token-A", month)
        assertFalse(result.fromCache)
        val offline = StatisticsRepository({ _, _, _ -> throw IOException("offline") }, dao)
        assertTrue(offline.dashboard("Bearer token-A", month).fromCache)
        assertEquals(
            result.dashboard.summary.revenue,
            offline.dashboard("Bearer token-A", month).dashboard.summary.revenue
        )
        val other = runCatching { offline.dashboard("Bearer token-B", month) }
        assertTrue(other.exceptionOrNull() is IOException)
        val next = runCatching { offline.dashboard("Bearer token-A", month.plusMonths(1)) }
        assertTrue(next.exceptionOrNull() is IOException)
    }

    @Test
    fun programmingErrorsNeverSilentlyUseStaleFinancialData() = runBlocking {
        val dao = InMemoryDao()
        StatisticsRepository({ _, _, _ -> sample() }, dao)
            .dashboard("Bearer owner", YearMonth.of(2030, 4))
        val unauthorized = StatisticsRepository(
            { _, _, _ -> IllegalStateException("Unauthorized").let { throw it } },
            dao
        )
        val failure = runCatching { unauthorized.dashboard("Bearer owner", YearMonth.of(2030, 4)) }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
    }

    private class InMemoryDao : AgendaCacheDao {
        private val cache = mutableMapOf<String, AgendaSnapshot>()

        override fun find(key: String): AgendaSnapshot? = cache[key]

        override fun upsert(snapshot: AgendaSnapshot) {
            cache[snapshot.cacheKey] = snapshot
        }

        override fun clearAll() {
            cache.clear()
        }
    }
}
