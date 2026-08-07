package com.example.somnixapp.api

import com.example.somnixapp.models.request.GoogleLoginRequest
import com.example.somnixapp.models.request.LoginRequest
import com.example.somnixapp.models.request.RegisterRequest
import com.example.somnixapp.models.request.RutaRequest
import com.example.somnixapp.models.response.AlertaResponse
import com.example.somnixapp.models.response.AuthResponse
import com.example.somnixapp.models.response.NotificacionResponse
import com.example.somnixapp.models.response.RutaResponse
import com.example.somnixapp.models.response.UsuarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // Autenticación

    @POST("api/auth/registro")
    suspend fun registrar(
        @Body request: RegisterRequest
    ): Response<UsuarioResponse>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("api/auth/google")
    suspend fun loginGoogle(
        @Body request: GoogleLoginRequest
    ): Response<AuthResponse>

    // Rutas asignadas al conductor

    @GET("api/rutas/conductor/{conductorId}")
    suspend fun obtenerRutasPorConductor(
        @Path("conductorId")
        conductorId: String,

        @Query("estado")
        estado: String? = null
    ): Response<List<RutaResponse>>

    // CRUD de rutas

    @GET("api/rutas")
    suspend fun obtenerRutas():
            Response<List<RutaResponse>>

    @GET("api/rutas/{id}")
    suspend fun obtenerRutaPorId(
        @Path("id")
        id: String
    ): Response<RutaResponse>

    @POST("api/rutas")
    suspend fun crearRuta(
        @Body request: RutaRequest
    ): Response<RutaResponse>

    @PUT("api/rutas/{id}")
    suspend fun actualizarRuta(
        @Path("id")
        id: String,

        @Body
        request: RutaRequest
    ): Response<RutaResponse>

    @DELETE("api/rutas/{id}")
    suspend fun eliminarRuta(
        @Path("id")
        id: String
    ): Response<Unit>

    // Alertas

    @GET("api/alertas/ruta/{rutaId}")
    suspend fun obtenerAlertasPorRuta(
        @Path("rutaId")
        rutaId: String
    ): Response<List<AlertaResponse>>

    @PUT("api/alertas/{id}/leer")
    suspend fun marcarAlertaComoLeida(
        @Path("id")
        id: String
    ): Response<Unit>

    // Notificaciones

    @GET("api/notificaciones/usuario/{usuarioId}")
    suspend fun obtenerNotificacionesPorUsuario(
        @Path("usuarioId")
        usuarioId: String
    ): Response<List<NotificacionResponse>>

    @GET(
        "api/notificaciones/usuario/" +
                "{usuarioId}/ultima-administrativa"
    )
    suspend fun obtenerUltimaNotificacionAdministrativa(
        @Path("usuarioId")
        usuarioId: String
    ): Response<NotificacionResponse>

    @PUT("api/notificaciones/{id}/leer")
    suspend fun marcarNotificacionComoLeida(
        @Path("id")
        id: String
    ): Response<Unit>
}