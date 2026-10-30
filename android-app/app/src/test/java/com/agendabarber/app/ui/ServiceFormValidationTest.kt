package com.agendabarber.app.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceFormValidationTest {
    @Test
    fun parsesBrlValuesWithoutFloatingPoint() {
        assertEquals(BigDecimal("45.50"), servicePriceInput("45,50"))
        assertEquals(BigDecimal("0"), servicePriceInput("0"))
        assertNull(servicePriceInput("-1"))
        assertNull(servicePriceInput("10.123"))
        assertNull(servicePriceInput("inválido"))
    }

    @Test
    fun checksRequiredNameDurationAndPrice() {
        assertTrue(serviceFormValid("Corte Degradê", "45", "45,50"))
        assertFalse(serviceFormValid("", "45", "45,50"))
        assertFalse(serviceFormValid("Corte", "0", "45,50"))
        assertFalse(serviceFormValid("Corte", "481", "45,50"))
        assertFalse(serviceFormValid("Corte", "60", "20,999"))
    }

    @Test
    fun iconValuesMatchJavaBackendContract() {
        assertEquals(
            listOf("SCISSORS", "BEARD", "COMBO", "EYEBROW", "CHILD", "OTHER"),
            BarberServiceIcon.entries.map { it.name }
        )
    }
}
