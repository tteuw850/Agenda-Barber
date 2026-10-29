package com.agendabarber.app.ui

import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsModelsTest {
    @Test
    fun monthCaptionUsesBrazilianLocale() {
        assertEquals("Outubro 2026", statisticsMonthCaption(YearMonth.of(2026, 10)))
        assertEquals("Janeiro 2027", statisticsMonthCaption(YearMonth.of(2027, 1)))
    }

    @Test
    fun revenueBarsNeverDivideByZeroOrExceedFullScale() {
        val maximum = BigDecimal("500.00")
        assertEquals(0f, statisticsBarFraction(BigDecimal.ZERO, maximum))
        assertEquals(0.5f, statisticsBarFraction(BigDecimal("250"), maximum))
        assertEquals(1f, statisticsBarFraction(BigDecimal("800"), maximum))
        assertEquals(0f, statisticsBarFraction(BigDecimal.ONE, BigDecimal.ZERO))
    }

    @Test
    fun rankingBarsHandleEmptyAndOutOfRangeValues() {
        assertEquals(0f, statisticsRankFraction(0, 0))
        assertEquals(0.5f, statisticsRankFraction(5, 10))
        assertEquals(1f, statisticsRankFraction(20, 10))
    }

    @Test
    fun demonstrationIsConsistentAndExplicitlySeparateFromLiveState() {
        val sample = exampleStatistics(YearMonth.of(2026, 10))
        val total = sample.dailyRevenue.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
        assertEquals(sample.revenue, total)
        assertEquals(46, sample.topServices.sumOf { it.completed })
        assertEquals(StatisticsState.Empty, StatisticsState.Empty)
        assertTrue(sample.dailyRevenue.all { it.day in 1..31 })
    }
}
