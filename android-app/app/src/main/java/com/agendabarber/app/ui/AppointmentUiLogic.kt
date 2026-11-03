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
    403 -> "Sem permissão para consultar esse horário."
    404 -> "Cadastro não encontrado ou indisponível."
    409 -> "Esse horário está ocupado ou o atendimento mudou. Atualize a agenda."
    429 -> "Muitas consultas em sequência. Tente novamente."
    500, 502, 503, 504 -> "O servidor não conseguiu consultar a agenda. Tente novamente."
    else -> if (error is java.io.IOException) {
        "A consulta demorou ou a conexão falhou. Tente novamente."
    } else {
        "Não foi possível concluir a consulta. Tente novamente."
    }
}

fun statusText(status: String): String = when (status) {
    "SCHEDULED" -> "Agendado"
    "IN_PROGRESS" -> "Em andamento"
    "COMPLETED" -> "Concluído"
    "CANCELLED" -> "Cancelado"
    "NO_SHOW" -> "Não compareceu"
    else -> status
}
