package com.agendabarber.app.ui

import java.io.IOException
import retrofit2.HttpException

internal fun clientRemovalErrorMessage(error: Throwable): String = when ((error as? HttpException)?.code()) {
    401 -> "Sua sessão expirou. Entre novamente."
    403 -> "Sem permissão para remover este cliente."
    404 -> "Cliente não encontrado. Atualize a lista."
    409 -> "Este cliente tem agendamentos em aberto ou uma dívida pendente. Resolva antes de remover."
    429 -> "Muitas tentativas. Tente novamente em instantes."
    500, 502, 503, 504 -> "Servidor indisponível. Tente remover o cliente mais tarde."
    else -> if (error is IOException) {
        "Sem conexão com o servidor. Verifique sua internet."
    } else {
        "Não foi possível remover o cliente. Tente novamente."
    }
}
