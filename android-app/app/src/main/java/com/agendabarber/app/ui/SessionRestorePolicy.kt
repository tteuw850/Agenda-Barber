package com.agendabarber.app.ui

import java.io.IOException
import retrofit2.HttpException

/** Invalid or revoked credentials must be forgotten; transient network errors must not. */
internal fun shouldForgetRefreshSession(failure: Throwable): Boolean =
    (failure as? HttpException)?.code() in setOf(400, 401, 403)

internal fun sessionRestoreErrorMessage(failure: Throwable): String = when {
    failure is IOException -> "Sem conexão. Verifique sua internet para entrar automaticamente."
    (failure as? HttpException)?.code() == 429 ->
        "Muitas tentativas. Tente entrar novamente em instantes."
    (failure as? HttpException)?.code() in setOf(500, 502, 503, 504) ->
        "Servidor indisponível. Tente entrar novamente."
    else -> "Não foi possível recuperar sua sessão. Tente novamente."
}
