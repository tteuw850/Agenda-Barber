package com.agendabarber.app.cache

import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.AppointmentView
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AgendaRepositoryOfflineTest {
    @Test
    fun usesRoomSnapshotWhenApiIsTemporarilyUnavailable() = runBlocking {
        val dao = MemoryCacheDao()
        var serverStatus = 200
        val repo = AgendaRepository(fakeApi {
            if (serverStatus == 503) throw httpError(503)
            emptyList()
        }, dao)

        val initial = repo.appointments("barber-token", "2026-11-06", "day", "ALL")
        assertFalse(initial.fromCache)
        assertEquals(1, dao.count())
        assertEquals(initial.synchronizedAt, repo.lastCacheWriteAt.value)

        serverStatus = 503
        val offline = repo.appointments("barber-token", "2026-11-06", "day", "ALL")
        assertTrue(offline.fromCache)
        assertEquals(initial.synchronizedAt, offline.synchronizedAt)
        assertEquals(emptyList<AppointmentView>(), offline.appointments)

        serverStatus = 200
        assertFalse(repo.appointments("barber-token", "2026-11-06", "day", "ALL").fromCache)
    }

    @Test
    fun neverSharesCachedAppointmentsBetweenTokensOrDates() = runBlocking {
        val dao = MemoryCacheDao()
        var fail = false
        val repo = AgendaRepository(fakeApi {
            if (fail) throw httpError(503)
            emptyList()
        }, dao)
        repo.appointments("barber-A", "2026-11-06", "day", "ALL")
        fail = true

        assertTrue(repo.appointments("barber-A", "2026-11-06", "day", "ALL").fromCache)
        val otherBarber = runCatching {
            repo.appointments("barber-B", "2026-11-06", "day", "ALL")
        }.exceptionOrNull()
        assertTrue(otherBarber is HttpException)

        val otherDate = runCatching {
            repo.appointments("barber-A", "2026-11-07", "day", "ALL")
        }.exceptionOrNull()
        assertTrue(otherDate is HttpException)
    }

    @Test
    fun neverUsesOfflineCacheAfterAuthenticationFailure() = runBlocking {
        val dao = MemoryCacheDao()
        var serverStatus = 200
        val repo = AgendaRepository(fakeApi {
            if (serverStatus != 200) throw httpError(serverStatus)
            emptyList()
        }, dao)
        repo.appointments("barber-token", "2026-11-06", "day", "ALL")
        serverStatus = 401

        val failure = runCatching {
            repo.appointments("barber-token", "2026-11-06", "day", "ALL")
        }.exceptionOrNull()
        assertTrue(failure is HttpException)
        assertEquals(401, (failure as HttpException).code())
    }

    @Test
    fun logoutClearsSnapshotsAndLastSynchronization() = runBlocking {
        val dao = MemoryCacheDao()
        var fail = false
        val repo = AgendaRepository(fakeApi {
            if (fail) throw httpError(503)
            emptyList()
        }, dao)
        repo.appointments("barber-token", "2026-11-06", "day", "ALL")
        assertEquals(1, dao.count())

        repo.clear()
        assertEquals(0, dao.count())
        assertNull(repo.lastCacheWriteAt.value)

        fail = true
        val failure = runCatching {
            repo.appointments("barber-token", "2026-11-06", "day", "ALL")
        }.exceptionOrNull()
        assertTrue(failure is HttpException)
    }

    private fun fakeApi(response: () -> List<AppointmentView>): AgendaApi =
        Proxy.newProxyInstance(
            AgendaApi::class.java.classLoader,
            arrayOf(AgendaApi::class.java)
        ) { _, method, _ ->
            if (method.name == "appointments") response() else error("Unexpected API method: ${method.name}")
        } as AgendaApi

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "Failure".toResponseBody(null)))

    private class MemoryCacheDao : AgendaCacheDao {
        private val cache = mutableMapOf<String, AgendaSnapshot>()

        override fun find(key: String): AgendaSnapshot? = cache[key]

        override fun upsert(snapshot: AgendaSnapshot) {
            cache[snapshot.cacheKey] = snapshot
        }

        override fun clearAll() {
            cache.clear()
        }

        fun count(): Int = cache.size
    }
}
