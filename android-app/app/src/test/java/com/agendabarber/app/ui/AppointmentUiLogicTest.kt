package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentUiLogicTest {
    @Test
    fun validatesIsoCalendarDate() {
        assertTrue(validDate("2026-10-27"))
        assertFalse(validDate("2026-02-30"))
        assertFalse(validDate("27/10/2026"))
    }

    @Test
    fun labelsStatesWithoutInventingStatus() {
        assertEquals("Agendado", statusText("SCHEDULED"))
        assertEquals("Em andamento", statusText("IN_PROGRESS"))
        assertEquals("Cancelado", statusText("CANCELLED"))
        assertEquals("CUSTOM", statusText("CUSTOM"))
    }
}
