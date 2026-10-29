package com.agendabarber.app.ui

import com.agendabarber.app.network.WorkingHoursInput
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val scheduleWeekdayNames = listOf(
    "Segunda-feira",
    "Terça-feira",
    "Quarta-feira",
    "Quinta-feira",
    "Sexta-feira",
    "Sábado",
    "Domingo"
)

private val inputClock = Regex("""(?:[01][0-9]|2[0-3]):[0-5][0-9]""")
private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun scheduleClock(value: String): String = runCatching {
    LocalTime.parse(value).format(clockFormatter)
}.getOrDefault("09:00")

internal fun workingHoursError(enabled: Boolean, opens: String, closes: String): String? {
    if (!inputClock.matches(opens) || !inputClock.matches(closes)) {
        return "Use o formato HH:mm para os horários."
    }
    if (enabled && !LocalTime.parse(closes).isAfter(LocalTime.parse(opens))) {
        return "O fechamento deve ser posterior à abertura."
    }
    return null
}

internal fun workingHoursPayload(enabled: Boolean, opens: String, closes: String): WorkingHoursInput =
    WorkingHoursInput(
        enabled = enabled,
        opensAt = opens,
        closesAt = closes
    )
