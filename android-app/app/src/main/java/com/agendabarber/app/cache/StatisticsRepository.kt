package com.agendabarber.app.cache

import com.agendabarber.app.network.ReportDashboardView
import com.google.gson.Gson
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal data class StatisticsLoadResult(
    val dashboard: ReportDashboardView,
    val fromCache: Boolean,
    val synchronizedAt: Long
)

/**
 * Read-through report cache in the existing Room table (no schema migration).
 * Authorization errors MUST NOT fall back to a previous financial snapshot.
 */
internal class StatisticsRepository(
    private val fetchDashboard: suspend (String, String, String) -> ReportDashboardView,
    private val dao: AgendaCacheDao,
    private val gson: Gson = Gson()
) {
    suspend fun dashboard(token: String, month: YearMonth): StatisticsLoadResult {
        val from = month.atDay(1).toString()
        val to = month.atEndOfMonth().toString()
        val key = cacheKey(token, from, to)
        try {
            val live = fetchDashboard(token, from, to)
            val now = System.currentTimeMillis()
            try {
                withContext(Dispatchers.IO) {
                    dao.upsert(AgendaSnapshot(key, gson.toJson(live), now))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                // The server result remains valid even if optional local caching fails.
            }
            return StatisticsLoadResult(live, false, now)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            val temporary = failure is IOException ||
                (failure is HttpException && failure.code() == 503)
            if (!temporary) throw failure
            val snapshot = withContext(Dispatchers.IO) { dao.find(key) } ?: throw failure
            val cached = try {
                gson.fromJson(snapshot.responseJson, ReportDashboardView::class.java)
            } catch (invalidCache: Exception) {
                throw failure
            }
            if (cached == null) throw failure
            return StatisticsLoadResult(cached, true, snapshot.synchronizedAt)
        }
    }

    private fun cacheKey(token: String, from: String, to: String): String {
        val bytes = ("reporting|" + token + "|" + from + "|" + to)
            .toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
