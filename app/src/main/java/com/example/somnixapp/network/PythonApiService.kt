package com.example.somnixapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query

data class IniciarViajeRequest(
    val usuarioId: String,
    val rutaId: String,
    val nombreRuta: String?
)

data class ApagarAlarmaRequest(
    val usuarioId: String,
    val rutaId: String
)

data class NecesidadConductorRequest(
    val usuarioId: String,
    val rutaId: String,
    val tipo: String,
    val mensaje: String
)

data class ApiResponse(
    val ok: Boolean,
    val mensaje: String?,
    val estado: String? = null,
    val fatiga: Int? = null,
    val ojosCerrados: Boolean? = null,
    val bostezos: Int? = null,
    val tipoAlerta: String? = null,
    val nivel: String? = null
)

interface PythonApiService {

    @POST("api/viaje/iniciar")
    suspend fun iniciarViaje(
        @Body request: IniciarViajeRequest
    ): Response<ApiResponse>

    @POST("api/viaje/terminar")
    suspend fun terminarViaje(
        @Body request: ApagarAlarmaRequest
    ): Response<ApiResponse>

    @POST("api/alarma/apagar")
    suspend fun apagarAlarma(
        @Body request: ApagarAlarmaRequest
    ): Response<ApiResponse>

    @POST("api/conductor/necesidad")
    suspend fun registrarNecesidad(
        @Body request: NecesidadConductorRequest
    ): Response<ApiResponse>

    @GET("api/monitoreo/estado")
    suspend fun obtenerEstado(): Response<ApiResponse>

    @Multipart
    @POST("api/monitoreo/frame")
    suspend fun analizarFrame(
        @Query("usuarioId") usuarioId: String,
        @Query("rutaId") rutaId: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse>

    @POST("api/viaje/pausar")
    suspend fun pausarViaje(): Response<ApiResponse>
}