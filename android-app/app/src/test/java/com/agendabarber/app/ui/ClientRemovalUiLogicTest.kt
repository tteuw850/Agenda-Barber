package com.agendabarber.app.ui

import java.io.IOException
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ClientRemovalUiLogicTest {
    @Test
    fun explainsProtectionForUnsettledClients() {
        assertEquals(
            "Este cliente tem agendamentos em aberto ou uma dívida pendente. Resolva antes de remover.",
            clientRemovalErrorMessage(httpError(409))
        )
    }

    @Test
    fun distinguishesUnauthorizedMissingAndOffline() {
        assertEquals(
            "Sua sessão expirou. Entre novamente.",
            clientRemovalErrorMessage(httpError(401))
        )
        assertEquals(
            "Cliente não encontrado. Atualize a lista.",
            clientRemovalErrorMessage(httpError(404))
        )
        assertEquals(
            "Sem conexão com o servidor. Verifique sua internet.",
            clientRemovalErrorMessage(IOException("offline"))
        )
    }

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, ResponseBody.create(null, "failed")))
}
