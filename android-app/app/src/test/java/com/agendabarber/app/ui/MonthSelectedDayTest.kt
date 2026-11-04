package com.agendabarber.app.ui

import com.agendabarber.app.network.AppointmentView
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthSelectedDayTest {
    private fun appointment(id: String, startsAt: String) = AppointmentView(
        id = id,
        clientId = "client-$id",
        clientName = "Cliente $id",
        clientPhone = null,
        serviceId = "service-$id",
        serviceName = "Corte",
        durationMinutes = 30,
        price = BigDecimal("25.00"),
        startsAt = startsAt,
        endsAt = startsAt,
        status = "SCHEDULED",
        notes = null,
        completedAt = null,
        version = 0L
    )

    @Test
    fun monthCardsIncludeOnlyTheSelectedCalendarDate() {
        val items = listOf(
            appointment("early", "2026-11-04T10:00:00Z"),
            appointment("selected", "2026-11-13T14:00:00Z"),
            appointment("late", "2026-11-15T15:00:00Z")
        )
        assertEquals(
            listOf("selected"),
            appointmentsForSelectedDay(items, LocalDate.of(2026, 11, 13)).map { it.id }
        )
        assertTrue(appointmentsForSelectedDay(items, LocalDate.of(2026, 11, 9)).isEmpty())
    }

    @Test
    fun selectionUsesBarberLocalTimezoneEvenWhenUtcDateDiffers() {
        val items = listOf(
            // Nov 14 at 01:30 UTC is Nov 13 at 22:30 in Sao Paulo.
            appointment("late-thirteen", "2026-11-14T01:30:00Z"),
            appointment("early-fourteen", "2026-11-14T04:00:00Z")
        )
        assertEquals(
            listOf("late-thirteen"),
            appointmentsForSelectedDay(items, LocalDate.of(2026, 11, 13)).map { it.id }
        )
    }

    @Test
    fun previousAppointmentsAreNotDeletedAndRemainAvailableInHistory() {
        val historic = appointment("historic", "2026-10-10T14:00:00Z")
        val current = appointment("today", "2026-11-04T14:00:00Z")
        val source = listOf(historic, current)
        assertEquals(
            listOf("today"),
            appointmentsForSelectedDay(source, LocalDate.of(2026, 11, 4)).map { it.id }
        )
        assertEquals(2, source.size)
        assertEquals(
            listOf("historic"),
            appointmentsForSelectedDay(source, LocalDate.of(2026, 10, 10)).map { it.id }
        )
    }
}
