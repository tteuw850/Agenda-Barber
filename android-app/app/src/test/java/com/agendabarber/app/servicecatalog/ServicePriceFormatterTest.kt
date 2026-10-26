package com.agendabarber.app.servicecatalog

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePriceFormatterTest {
    @Test
    fun displaysTwoDecimalsEvenWhenJsonHasNoTrailingZero() {
        assertEquals("R$ 45,50", formatBrl(BigDecimal("45.5")))
        assertEquals("R$ 45,50", formatBrl(BigDecimal("45.50")))
        assertEquals("R$ 0,00", formatBrl(BigDecimal.ZERO))
    }

    @Test
    fun displaysBrazilianThousandSeparatorWithoutFloatingPointConversions() {
        assertEquals("R$ 1.234,50", formatBrl(BigDecimal("1234.50")))
        assertEquals("R$ 100.000,99", formatBrl(BigDecimal("100000.99")))
    }
}
