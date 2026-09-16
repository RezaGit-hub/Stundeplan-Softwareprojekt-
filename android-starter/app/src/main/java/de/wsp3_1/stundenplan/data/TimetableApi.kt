package de.wsp3_1.stundenplan.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit-Schnittstelle zum Vertrag (api-contract/openapi.yaml). Das Token wird je Aufruf als
 * Header uebergeben ("Bearer <token>") - so bleibt sichtbar, welche Aufrufe ein Token brauchen,
 * und die Tests kommen ohne Interceptor aus.
 */
interface TimetableApi {

    @GET("api/health")
    suspend fun health(): Response<HealthDto>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @DELETE("api/auth/token")
    suspend fun logout(@Header("Authorization") authorization: String): Response<Unit>

    @GET("api/me")
    suspend fun me(@Header("Authorization") authorization: String): Response<MeDto>

    @GET("api/me/timetable/week")
    suspend fun myWeek(
        @Header("Authorization") authorization: String,
        @Query("date") date: String,
    ): Response<WeekDto>

    @GET("api/timegrid")
    suspend fun timegrid(
        @Header("Authorization") authorization: String
    ): Response<TimegridDto>

    @GET("api/me/class-week")
    suspend fun myClassWeek(
        @Header("Authorization") authorization: String,
        @Query("date") date: String,
    ): Response<WeekDto>
}
