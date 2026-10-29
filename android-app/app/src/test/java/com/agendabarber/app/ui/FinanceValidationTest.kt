package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceValidationTest {
    @Test
    fun acceptsBrlDecimalWithCommaOrDotWithoutRounding() {
        assertEquals("39.90", parseMoney("39,9")?.toPlainString())
        assertEquals("39.90", parseMoney("39.90")?.toPlainString())
        assertEquals("0.00", parseMoney("0")?.toPlainString())
    }

    @Test
    fun rejectsFractionalCentsNegativeAndOutOfRange() {
        assertNull(parseMoney("10,999"))
        assertNull(parseMoney("-1,00"))
        assertNull(parseMoney("10000000000,00"))
        assertNull(parseMoney("R$ 23"))
        assertNull(parseMoney("3,4,5"))
    }

    @Test
    fun expensesRequireStrictlyPositiveAmount() {
        assertNull(parseMoney("0,00", positiveOnly = true))
        assertEquals("0.01", parseMoney("0,01", positiveOnly = true)?.toPlainString())
    }
}
