package com.agendabarber.app.cache

import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.AppointmentView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class AgendaLoadResult(val appointments: List<AppointmentView>, val fromCache: Boolean, val synchronizedAt: Long)

/**
 * API is always authoritative. Room is a READ-ONLY fallback, never a queue for writes.
 * Entries are namespaced by a one-way hash of the current token and query parameters.
 */
class AgendaRepository(private val api: AgendaApi, private val dao: AgendaCacheDao, private val gson: Gson = Gson()) {
    private val listType = object : TypeToken<List<AppointmentView>>() {}.type
    private val _lastCacheWriteAt = MutableStateFlow<Long?>(null)
    val lastCacheWriteAt = _lastCacheWriteAt.asStateFlow()

    suspend fun appointments(token: String, date: String, view: String, status: String): AgendaLoadResult {
        val key = cacheKey(token, date, view, status)
        try {
            val live = api.appointments(token, date, view, status)
            val synchronizedAt = System.currentTimeMillis()
            // Cached writes must not prevent showing successfully retrieved server data.
            runCatching {
                withContext(Dispatchers.IO) {
                    dao.upsert(AgendaSnapshot(key, gson.toJson(live), synchronizedAt))
                }
            }.onSuccess {
                _lastCacheWriteAt.value = synchronizedAt
            }
            return AgendaLoadResult(live, false, synchronizedAt)
        } catch (failure: Exception) {
            val recoverable = failure is IOException ||
                (failure is HttpException && failure.code() == 503)
            if (!recoverable) throw failure
            val snapshot = withContext(Dispatchers.IO) { dao.find(key) } ?: throw failure
            val data: List<AppointmentView> = gson.fromJson(snapshot.responseJson, listType)
            return AgendaLoadResult(data, true, snapshot.synchronizedAt)
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { dao.clearAll() }
        _lastCacheWriteAt.value = null
    }

    private fun cacheKey(token: String, date: String, view: String, status: String): String {
        val source = (token + "|" + date + "|" + view + "|" + status)
            .toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(source)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
