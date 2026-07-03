package com.example.somnixapp.api

import com.example.somnixapp.models.request.LoginRequest
import com.example.somnixapp.models.request.RegisterRequest
import com.example.somnixapp.models.response.AuthResponse
import com.example.somnixapp.models.response.UsuarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/registro")
    suspend fun registrar(
        @Body request: RegisterRequest
    ): Response<UsuarioResponse>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>
}