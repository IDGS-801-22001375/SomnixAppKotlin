package com.example.somnixapp.api

import com.example.somnixapp.models.request.GoogleLoginRequest
import com.example.somnixapp.models.request.LoginRequest
import com.example.somnixapp.models.request.RegisterRequest
import com.example.somnixapp.models.request.RutaRequest
import com.example.somnixapp.models.response.AlertaResponse
import com.example.somnixapp.models.response.AuthResponse
import com.example.somnixapp.models.response.UsuarioResponse
import com.example.somnixapp.models.response.RutaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.*

interface ApiService {

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

    // Rutas
    @GET("api/rutas")
    suspend fun obtenerRutas(): Response<List<RutaResponse>>

    @GET("api/rutas/{id}")
    suspend fun obtenerRutaPorId(@Path("id") id: String): Response<RutaResponse>

    @POST("api/rutas")
    suspend fun crearRuta(@Body request: RutaRequest): Response<RutaResponse>

    @PUT("api/rutas/{id}")
    suspend fun actualizarRuta(
        @Path("id") id: String,
        @Body request: RutaRequest
    ): Response<RutaResponse>

    @DELETE("api/rutas/{id}")
    suspend fun eliminarRuta(@Path("id") id: String): Response<Unit>

    @GET("api/alertas/ruta/{rutaId}")
    suspend fun obtenerAlertasPorRuta(
        @Path("rutaId") rutaId: String
    ): Response<List<AlertaResponse>>
}