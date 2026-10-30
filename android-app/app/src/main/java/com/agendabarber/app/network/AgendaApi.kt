package com.agendabarber.app.network

import java.math.BigDecimal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long
)

data class ClientView(
    val id: String,
    val name: String,
    val phone: String?,
    val favorite: Boolean = false,
    val completedAppointments: Long = 0,
    val lastAppointmentAt: String? = null,
    val photoUrl: String? = null
)
data class ServiceView(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val defaultPrice: BigDecimal,
    val isHaircut: Boolean = false,
    val active: Boolean = true,
    val iconKey: String = "SCISSORS"
)

data class ServiceWriteRequest(
    val name: String,
    val durationMinutes: Int,
    val defaultPrice: BigDecimal,
    val isHaircut: Boolean,
    val active: Boolean,
    val iconKey: String
)
data class WorkingHoursInput(val enabled: Boolean, val opensAt: String, val closesAt: String)
data class WorkingHoursView(val weekday: Int, val enabled: Boolean, val opensAt: String, val closesAt: String)

data class SingleBlockInput(val startsAt: String, val endsAt: String, val reason: String?, val notes: String?)
data class SingleBlockView(
    val id: String,
    val startsAt: String,
    val endsAt: String,
    val reason: String?,
    val notes: String?
)
data class RecurringBlockInput(
    val weekday: Int,
    val startsAt: String,
    val endsAt: String,
    val validFrom: String,
    val validUntil: String,
    val reason: String?,
    val notes: String?
)
data class RecurringBlockView(
    val id: String,
    val weekday: Int,
    val startsAt: String,
    val endsAt: String,
    val validFrom: String,
    val validUntil: String,
    val reason: String?,
    val notes: String?
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
    val receivedAmount: BigDecimal? = null
)

data class HistoryItem(
    val eventType: String,
    val previousStatus: String?,
    val newStatus: String,
    val happenedAt: String
)

data class CreateAppointment(
    val clientId: String,
    val serviceId: String,
    val startsAt: String,
    val price: BigDecimal?,
    val notes: String?
)

data class CompleteAppointment(val receivedAmount: BigDecimal)

data class ExpenseInput(val occurredOn: String, val description: String, val amount: BigDecimal, val category: String)

data class ExpenseView(
    val id: String,
    val occurredOn: String,
    val description: String,
    val amount: BigDecimal,
    val category: String
)

/** F03-02 reporting endpoint: all money values are decimal, not floating-point. */
data class ReportSummaryView(
    val from: String,
    val to: String,
    val timezone: String,
    val completedAppointments: Long,
    val haircuts: Long,
    val receiptedAppointments: Long,
    val missingReceipts: Long,
    val revenue: BigDecimal,
    val expenses: BigDecimal,
    val estimatedProfit: BigDecimal,
    val averageTicket: BigDecimal,
    val cancellations: Long
)

data class ReportDailyView(val date: String, val revenue: BigDecimal)

data class ReportPopularView(
    val serviceId: String,
    val serviceName: String,
    val completedAppointments: Long
)

data class ReportDashboardView(
    val summary: ReportSummaryView,
    val daily: List<ReportDailyView>,
    val popularServices: List<ReportPopularView>
)

data class CreateClient(
    val name: String,
    val phone: String?,
    val email: String? = null,
    val birthDate: String? = null,
    val notes: String? = null
)

interface AgendaApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/v1/reports/dashboard")
    suspend fun reportingDashboard(
        @Header("Authorization") bearer: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): ReportDashboardView

    @GET("api/v1/appointments")
    suspend fun appointments(
        @Header("Authorization") bearer: String,
        @Query("date") date: String,
        @Query("view") view: String,
        @Query("status") status: String
    ): List<AppointmentView>

    @GET("api/v1/appointments/{id}")
    suspend fun appointment(@Header("Authorization") bearer: String, @Path("id") id: String): AppointmentView

    @GET("api/v1/appointments/{id}/history")
    suspend fun history(@Header("Authorization") bearer: String, @Path("id") id: String): List<HistoryItem>

    @POST("api/v1/appointments")
    suspend fun book(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") key: String,
        @Body request: CreateAppointment
    ): AppointmentView

    @PATCH("api/v1/appointments/{id}")
    suspend fun edit(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any?>
    ): AppointmentView

    @POST("api/v1/appointments/{id}/start")
    suspend fun start(@Header("Authorization") bearer: String, @Path("id") id: String): AppointmentView

    @POST("api/v1/appointments/{id}/complete")
    suspend fun complete(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body receipt: CompleteAppointment
    ): AppointmentView

    @POST("api/v1/appointments/{id}/cancel")
    suspend fun cancel(@Header("Authorization") bearer: String, @Path("id") id: String): AppointmentView

    @GET("api/v1/expenses")
    suspend fun expenses(
        @Header("Authorization") bearer: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): List<ExpenseView>

    @POST("api/v1/expenses")
    suspend fun createExpense(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") key: String,
        @Body body: ExpenseInput
    ): ExpenseView

    @PUT("api/v1/expenses/{id}")
    suspend fun updateExpense(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body body: ExpenseInput
    ): ExpenseView

    @DELETE("api/v1/expenses/{id}")
    suspend fun deleteExpense(@Header("Authorization") bearer: String, @Path("id") id: String)

    @GET("api/v1/clients")
    suspend fun clients(
        @Header("Authorization") bearer: String,
        @Query("search") search: String,
        @Query("limit") limit: Int = 100,
        @Query("filter") filter: String = "all"
    ): List<ClientView>

    @PATCH("api/v1/clients/{id}")
    suspend fun favoriteClient(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body patch: Map<String, Boolean>
    ): ClientView

    @POST("api/v1/clients")
    suspend fun newClient(@Header("Authorization") bearer: String, @Body request: CreateClient): ClientView

    @GET("api/v1/services")
    suspend fun services(
        @Header("Authorization") bearer: String,
        @Query("activeOnly") activeOnly: Boolean = true
    ): List<ServiceView>

    @POST("api/v1/services")
    suspend fun createService(@Header("Authorization") bearer: String, @Body request: ServiceWriteRequest): ServiceView

    @PATCH("api/v1/services/{id}")
    suspend fun updateService(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body request: ServiceWriteRequest
    ): ServiceView

    @PATCH("api/v1/services/{id}")
    suspend fun setServiceActive(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body patch: Map<String, Boolean>
    ): ServiceView

    @DELETE("api/v1/services/{id}")
    suspend fun deactivateService(@Header("Authorization") bearer: String, @Path("id") id: String)

    @GET("api/v1/schedule/working-hours")
    suspend fun workingHours(@Header("Authorization") bearer: String): List<WorkingHoursView>

    @PUT("api/v1/schedule/working-hours/{weekday}")
    suspend fun saveWorkingHours(
        @Header("Authorization") bearer: String,
        @Path("weekday") weekday: Int,
        @Body request: WorkingHoursInput
    ): WorkingHoursView

    @GET("api/v1/schedule/blocks")
    suspend fun blocks(@Header("Authorization") bearer: String, @Query("date") date: String): List<SingleBlockView>

    @POST("api/v1/schedule/blocks")
    suspend fun createBlock(@Header("Authorization") bearer: String, @Body request: SingleBlockInput): SingleBlockView

    @DELETE("api/v1/schedule/blocks/{id}")
    suspend fun deleteBlock(@Header("Authorization") bearer: String, @Path("id") id: String)

    @GET("api/v1/schedule/recurring-blocks")
    suspend fun recurringBlocks(@Header("Authorization") bearer: String): List<RecurringBlockView>

    @POST("api/v1/schedule/recurring-blocks")
    suspend fun addWeekly(@Header("Authorization") token: String, @Body data: RecurringBlockInput): RecurringBlockView

    @DELETE("api/v1/schedule/recurring-blocks/{id}")
    suspend fun deleteRecurringBlock(@Header("Authorization") bearer: String, @Path("id") id: String)

    @GET("api/v1/schedule/available-slots")
    suspend fun slots(
        @Header("Authorization") bearer: String,
        @Query("date") date: String,
        @Query("serviceId") serviceId: String
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
