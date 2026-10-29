package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockTimeValidationTest {
    @Test
    fun singleBlockConvertsBrazilLocalTimeToUtc() {
        val payload = oneTimeBlockPayload(
            "2026-10-29", "12:00", "13:00", "Almoço", "Retorno às 13h"
        )
        assertEquals("2026-10-29T15:00:00Z", payload.startsAt)
        assertEquals("2026-10-29T16:00:00Z", payload.endsAt)
        assertEquals("Almoço", payload.reason)
    }

    @Test
    fun weeklyBlockUsesDateWeekdayAndBounds() {
        val payload = recurringBlockPayload(
            "2026-10-30", "12:00", "13:00", "2026-11-27", "Folga", ""
        )
        assertEquals(5, payload.weekday)
        assertEquals("2026-10-30", payload.validFrom)
        assertEquals("2026-11-27", payload.validUntil)
        assertNull(payload.notes)
    }

    @Test
    fun rejectsInvalidHoursAndIntervals() {
        assertNotNull(validateBlockFields(BlockMode.SINGLE, "2026-10-29", "13:00", "12:00", ""))
        assertNotNull(validateBlockFields(BlockMode.SINGLE, "2026-10-29", "12:00", "12:00", ""))
        assertNotNull(validateBlockFields(BlockMode.SINGLE, "2026-10-29", "25:00", "26:00", ""))
        assertNotNull(validateBlockFields(BlockMode.SINGLE, "2026-02-30", "12:00", "13:00", ""))
    }

    @Test
    fun checksRecurringPeriodAndAcceptsValidDateRange() {
        assertNotNull(
            validateBlockFields(
                BlockMode.RECURRING, "2026-10-29", "12:00", "13:00", "2026-10-28"
            )
        )
        assertNotNull(
            validateBlockFields(
                BlockMode.RECURRING, "2026-10-29", "12:00", "13:00", "2027-05-01"
            )
        )
        assertNull(
            validateBlockFields(
                BlockMode.RECURRING, "2026-10-29", "12:00", "13:00", "2026-11-29"
            )
        )
    }
}
