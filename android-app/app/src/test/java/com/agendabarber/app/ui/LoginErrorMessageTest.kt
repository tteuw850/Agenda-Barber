package com.agendabarber.app.ui

import java.io.IOException
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LoginErrorMessageTest {
    @Test
    fun invalidCredentialsHaveTheCorrectMessage() {
        assertEquals("Credenciais inválidas.", loginErrorMessage(httpError(401)))
        assertEquals("Credenciais inválidas.", loginErrorMessage(httpError(400)))
    }

    @Test
    fun expiredSessionsOutsideLoginAreNotRelabeled() {
        assertEquals("Sua sessão expirou. Entre novamente.", apiMessage(httpError(401)))
    }

    @Test
    fun connectionAndServerErrorsGiveUsefulFeedback() {
        assertEquals(
            "Sem conexão com o servidor. Confira sua internet.",
            loginErrorMessage(IOException("offline"))
        )
        assertEquals(
            "Servidor indisponível. Tente novamente em instantes.",
            loginErrorMessage(httpError(503))
        )
        assertEquals(
            "Muitas tentativas de login. Aguarde e tente novamente.",
            loginErrorMessage(httpError(429))
        )
    }

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, ResponseBody.create(null, "failure")))
}
