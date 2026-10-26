package com.agendabarber.app.servicecatalog

import java.math.BigDecimal
import java.util.Locale

private val brazilianLocale = Locale.forLanguageTag("pt-BR")

/**
 * Presentation-only money formatting; use BigDecimal end-to-end for prices.
 * The API may encode a value as 45.5 or 45.50: both display as R$ 45,50.
 */
fun formatBrl(price: BigDecimal): String = String.format(brazilianLocale, "R$ %,.2f", price)
