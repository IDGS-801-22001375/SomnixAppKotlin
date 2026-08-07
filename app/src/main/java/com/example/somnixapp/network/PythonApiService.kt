package com.example.somnixapp.network

import com.example.somnixapp.models.response.EstadisticasResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
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
    val ok: Boolean = false,
    val mensaje: String? = null,
    val estado: String? = null,
    val fatiga: Int? = null,

    // Campo actual enviado por Python.
    val ojosCerrados: Boolean? = null,

    // Contadores compatibles con la respuesta actual.
    val bostezos: Int? = null,
    val parpadeos: Int? = null,
    val cabeceos: Int? = null,

    val tipoAlerta: String? = null,
    val nivel: String? = null,

    // Datos de diagnóstico del detector actual.
    val ear: Double? = null,
    val mar: Double? = null,
    val perclos: Double? = null,
    val rostroDetectado: Boolean? = null,
    val tiempoOjosCerrados: Double? = null,
    val procesamientoMs: Double? = null,

    // Campos presentes cuando Python ignora un frame.
    val ignorado: Boolean? = null,

    // Compatibilidad con respuestas anteriores.
    val ojos_cerrados: Boolean? = null,
    val tipo_alerta: String? = null,
    val rostro_detectado: Boolean? = null,
    val tiempo_ojos_cerrados: Double? = null
)

interface PythonApiService {

    @POST("api/viaje/iniciar")
    suspend fun iniciarViaje(
        @Body request: IniciarViajeRequest
    ): Response<ApiResponse>

    @POST("api/viaje/pausar")
    suspend fun pausarViaje(): Response<ApiResponse>

    @POST("api/viaje/reanudar")
    suspend fun reanudarViaje(): Response<ApiResponse>

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

    @GET("api/estadisticas/usuario/{usuarioId}")
    suspend fun obtenerEstadisticas(
        @Path("usuarioId") usuarioId: String
    ): Response<EstadisticasResponse>
}