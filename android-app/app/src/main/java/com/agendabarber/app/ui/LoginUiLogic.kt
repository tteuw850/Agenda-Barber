package com.agendabarber.app.ui

import java.io.IOException
import retrofit2.HttpException

/** Only login-specific failures should be described as invalid credentials. */
internal fun loginErrorMessage(error: Throwable): String = when ((error as? HttpException)?.code()) {
    400, 401 -> "Credenciais inválidas."
    403 -> "Esta conta não tem permissão para entrar."
    429 -> "Muitas tentativas de login. Aguarde e tente novamente."
    500, 502, 503, 504 -> "Servidor indisponível. Tente novamente em instantes."
    else -> when (error) {
        is IOException -> "Sem conexão com o servidor. Confira sua internet."
        else -> "Não foi possível entrar. Tente novamente."
    }
}
