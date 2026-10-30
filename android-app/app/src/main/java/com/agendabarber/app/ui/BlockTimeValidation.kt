package com.agendabarber.app.ui

import com.agendabarber.app.network.RecurringBlockInput
import com.agendabarber.app.network.SingleBlockInput
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** F02-03 documents America/Sao_Paulo as the default barber timezone. */
internal val barberScheduleZone: ZoneId = ZoneId.of("America/Sao_Paulo")

internal enum class BlockMode(val label: String) {
    SINGLE("Único"),
    RECURRING("Recorrente")
}

private val timeFormat = Regex("""(?:[01][0-9]|2[0-3]):[0-5][0-9]""")

internal fun validateBlockFields(
    mode: BlockMode,
    date: String,
    start: String,
    end: String,
    validUntil: String
): String? {
    val day = runCatching { LocalDate.parse(date) }.getOrNull()
        ?: return "Selecione uma data válida."
    if (!timeFormat.matches(start) || !timeFormat.matches(end)) {
        return "Informe horários no formato HH:mm."
    }
    val beginning = LocalTime.parse(start)
    val ending = LocalTime.parse(end)
    if (!ending.isAfter(beginning)) {
        return "O horário final deve ser posterior ao inicial."
    }
    if (mode == BlockMode.RECURRING) {
        val until = runCatching { LocalDate.parse(validUntil) }.getOrNull()
            ?: return "Selecione a data final da recorrência."
        if (until.isBefore(day)) return "A vigência final não pode ser anterior à inicial."
        if (ChronoUnit.DAYS.between(day, until) >= 180L) {
            return "A recorrência deve durar no máximo 180 dias."
        }
    }
    return null
}

internal fun oneTimeBlockPayload(
    date: String,
    start: String,
    end: String,
    reason: String,
    notes: String
): SingleBlockInput {
    val day = LocalDate.parse(date)
    val from = LocalDateTime.of(day, LocalTime.parse(start)).atZone(barberScheduleZone).toInstant()
    val to = LocalDateTime.of(day, LocalTime.parse(end)).atZone(barberScheduleZone).toInstant()
    return SingleBlockInput(
        startsAt = from.toString(),
        endsAt = to.toString(),
        reason = reason.trim().takeIf { it.isNotEmpty() },
        notes = notes.trim().takeIf { it.isNotEmpty() }
    )
}

internal fun recurringBlockPayload(
    date: String,
    start: String,
    end: String,
    validUntil: String,
    reason: String,
    notes: String
): RecurringBlockInput {
    val day = LocalDate.parse(date)
    return RecurringBlockInput(
        weekday = day.dayOfWeek.value,
        startsAt = start,
        endsAt = end,
        validFrom = date,
        validUntil = validUntil,
        reason = reason.trim().takeIf { it.isNotEmpty() },
        notes = notes.trim().takeIf { it.isNotEmpty() }
    )
}
