package com.agendabarber.app.ui

import java.math.BigDecimal

private val brlInput = Regex("""\d{1,10}([,.]\d{1,2})?""")

/** Strict BRL parser: never silently round a received amount or an expense. */
internal fun parseMoney(value: String, positiveOnly: Boolean = false): BigDecimal? {
    val cleaned = value.trim()
    if (!brlInput.matches(cleaned)) return null
    return runCatching { BigDecimal(cleaned.replace(',', '.')).setScale(2) }
        .getOrNull()
        ?.takeIf { if (positiveOnly) it.signum() > 0 else it.signum() >= 0 }
}
