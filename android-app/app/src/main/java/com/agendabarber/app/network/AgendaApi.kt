package com.agendabarber.app.network

import java.math.BigDecimal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
)

data class ClientView(val id: String, val name: String, val phone: String?)
data class ServiceView(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val defaultPrice: BigDecimal,
)
data class SlotView(val startsAt: String, val endsAt: String, val localStart: String)

data class AppointmentView(
    val id: String,
    val clientId: String,
    val clientName: String,
    val clientPhone: String?,
    val serviceId: String,
    val serviceName: String,
    val durationMinutes: Int,
    val price: BigDecimal,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val notes: String?,
    val completedAt: String?,
    val version: Long,
)

data class HistoryItem(
    val eventType: String,
    val previousStatus: String?,
    val newStatus: String,
    val happenedAt: String,
)

data class CreateAppointment(
    val clientId: String,
    val serviceId: String,
    val startsAt: String,
    val price: BigDecimal?,
    val notes: String?,
)

data class CreateClient(val name: String, val phone: String?)

interface AgendaApi {
    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): LoginResponse

    @GET("api/v1/appointments")
    suspend fun appointments(
        @Header("Authorization") bearer: String,
        @Query("date") date: String,
        @Query("view") view: String,
        @Query("status") status: String,
    ): List<AppointmentView>

    @GET("api/v1/appointments/{id}")
    suspend fun appointment(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
    ): AppointmentView

    @GET("api/v1/appointments/{id}/history")
    suspend fun history(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
    ): List<HistoryItem>

    @POST("api/v1/appointments")
    suspend fun book(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") key: String,
        @Body request: CreateAppointment,
    ): AppointmentView

    @PATCH("api/v1/appointments/{id}")
    suspend fun edit(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any?>,
    ): AppointmentView

    @POST("api/v1/appointments/{id}/start")
    suspend fun start(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
    ): AppointmentView

    @POST("api/v1/appointments/{id}/complete")
    suspend fun complete(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
    ): AppointmentView

    @POST("api/v1/appointments/{id}/cancel")
    suspend fun cancel(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
    ): AppointmentView

    @GET("api/v1/clients")
    suspend fun clients(
        @Header("Authorization") bearer: String,
        @Query("search") search: String,
        @Query("limit") limit: Int = 100,
    ): List<ClientView>

    @POST("api/v1/clients")
    suspend fun newClient(
        @Header("Authorization") bearer: String,
        @Body request: CreateClient,
    ): ClientView

    @GET("api/v1/services")
    suspend fun services(
        @Header("Authorization") bearer: String,
        @Query("activeOnly") activeOnly: Boolean = true,
    ): List<ServiceView>

    @GET("api/v1/schedule/available-slots")
    suspend fun slots(
        @Header("Authorization") bearer: String,
        @Query("date") date: String,
        @Query("serviceId") serviceId: String,
    ): List<SlotView>
}

/** No URLs, passwords or tokens are logged. Only HTTPS API endpoints are allowed. */
object AgendaApiFactory {
    fun create(serverUrl: String): AgendaApi {
        val base = serverUrl.trim().trimEnd('/') + "/"
        require(base.startsWith("https://", ignoreCase = true)) {
            "Informe a URL HTTPS da API."
        }
        return Retrofit.Builder()
            .baseUrl(base)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgendaApi::class.java)
    }
}
