package com.agendabarber.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import retrofit2.HttpException

private val brazilian = Locale.forLanguageTag("pt-BR")
private val localTimeFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm", brazilian)

fun displayTime(instant: String): String = runCatching {
    Instant.parse(instant).atZone(ZoneId.systemDefault()).format(localTimeFormat)
}.getOrElse { instant }

fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

fun apiMessage(error: Throwable): String = when ((error as? HttpException)?.code()) {
    400 -> "Verifique os dados e tente novamente."
    401 -> "Sua sessão expirou. Entre novamente."
    404 -> "Cadastro não encontrado ou indisponível."
    409 -> "Esse horário está ocupado ou o atendimento mudou. Atualize a agenda."
    503 -> "Servidor indisponível. Confira a conexão e tente novamente."
    else -> "Não foi possível conectar à API. Confira o endereço e sua conexão."
}

fun statusText(status: String): String = when (status) {
    "SCHEDULED" -> "Agendado"
    "IN_PROGRESS" -> "Em andamento"
    "COMPLETED" -> "Concluído"
    "CANCELLED" -> "Cancelado"
    "NO_SHOW" -> "Não compareceu"
    else -> status
}
