package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockTimeValidationTest {
    @Test
    fun singleBlockConvertsBrazilLocalTimeToUtc() {
        val payload = oneTimeBlockPayload(
            date = "2026-10-29",
            start = "12:00",
            end = "13:00",
            reason = "Almoço",
            notes = "Retorno às 13h"
        )
        assertEquals("2026-10-29T15:00:00Z", payload.startsAt)
        assertEquals("2026-10-29T16:00:00Z", payload.endsAt)
        assertEquals("Almoço", payload.reason)
    }

    @Test
    fun weeklyBlockUsesDateWeekdayAndBounds() {
        val payload = recurringBlockPayload(
            date = "2026-10-30",
            start = "12:00",
            end = "13:00",
            validUntil = "2026-11-27",
            reason = "Folga",
            notes = ""
        )
        assertEquals(5, payload.weekday)
        assertEquals("2026-10-30", payload.validFrom)
        assertEquals("2026-11-27", payload.validUntil)
        assertNull(payload.notes)
    }

    @Test
    fun rejectsInvalidHoursAndIntervals() {
        val invalidIntervals = listOf(
            "13:00" to "12:00",
            "12:00" to "12:00",
            "25:00" to "26:00"
        )
        invalidIntervals.forEach { (start, end) ->
            assertNotNull(
                validateBlockFields(
                    mode = BlockMode.SINGLE,
                    date = "2026-10-29",
                    start = start,
                    end = end,
                    validUntil = ""
                )
            )
        }
        assertNotNull(
            validateBlockFields(
                mode = BlockMode.SINGLE,
                date = "2026-02-30",
                start = "12:00",
                end = "13:00",
                validUntil = ""
            )
        )
    }

    @Test
    fun checksRecurringPeriodAndAcceptsValidDateRange() {
        val before = validateBlockFields(
            mode = BlockMode.RECURRING,
            date = "2026-10-29",
            start = "12:00",
            end = "13:00",
            validUntil = "2026-10-28"
        )
        val tooLong = validateBlockFields(
            mode = BlockMode.RECURRING,
            date = "2026-10-29",
            start = "12:00",
            end = "13:00",
            validUntil = "2027-05-01"
        )
        val valid = validateBlockFields(
            mode = BlockMode.RECURRING,
            date = "2026-10-29",
            start = "12:00",
            end = "13:00",
            validUntil = "2026-11-29"
        )
        assertNotNull(before)
        assertNotNull(tooLong)
        assertNull(valid)
    }
}
