package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkingHoursValidationTest {
    @Test
    fun acceptsValidHoursAndClosedDay() {
        assertNull(workingHoursError(true, "08:00", "18:00"))
        assertNull(workingHoursError(false, "09:00", "18:00"))
        assertEquals("08:00", scheduleClock("08:00:00"))
    }

    @Test
    fun rejectsInvalidOrOvernightHours() {
        assertNotNull(workingHoursError(true, "25:00", "18:00"))
        assertNotNull(workingHoursError(true, "13:00", "13:00"))
        assertNotNull(workingHoursError(true, "19:00", "08:00"))
        assertNotNull(workingHoursError(false, "bad", "18:00"))
    }

    @Test
    fun mapsAllSevenWeekdaysAndWritesExactInput() {
        assertEquals(7, scheduleWeekdayNames.size)
        assertEquals("Segunda-feira", scheduleWeekdayNames.first())
        assertEquals("Domingo", scheduleWeekdayNames.last())
        val input = workingHoursPayload(true, "09:00", "17:30")
        assertEquals(true, input.enabled)
        assertEquals("09:00", input.opensAt)
        assertEquals("17:30", input.closesAt)
    }

    @Test
    fun distinguishesUnavailableFeaturesFromWorkingActions() {
        assertEquals("Backup e sincronização", MoreInfo.BACKUP.title)
        assertEquals("Tema", MoreInfo.THEME.title)
    }
}
