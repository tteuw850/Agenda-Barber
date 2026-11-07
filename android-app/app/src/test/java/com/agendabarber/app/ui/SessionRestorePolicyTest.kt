package com.agendabarber.app.ui

import java.io.IOException
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SessionRestorePolicyTest {
    @Test
    fun invalidAndRevokedSessionsAreForgotten() {
        assertTrue(shouldForgetRefreshSession(httpError(400)))
        assertTrue(shouldForgetRefreshSession(httpError(401)))
        assertTrue(shouldForgetRefreshSession(httpError(403)))
    }

    @Test
    fun networkOutagesAndServerErrorsKeepSessionForRetry() {
        assertFalse(shouldForgetRefreshSession(IOException("offline")))
        assertFalse(shouldForgetRefreshSession(httpError(429)))
        assertFalse(shouldForgetRefreshSession(httpError(503)))
    }

    @Test
    fun offlineAndServerFailuresAreDistinguished() {
        assertEquals(
            "Sem conexão. Verifique sua internet para entrar automaticamente.",
            sessionRestoreErrorMessage(IOException("offline"))
        )
        assertEquals(
            "Servidor indisponível. Tente entrar novamente.",
            sessionRestoreErrorMessage(httpError(503))
        )
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, ResponseBody.create(null, "error")))
}
