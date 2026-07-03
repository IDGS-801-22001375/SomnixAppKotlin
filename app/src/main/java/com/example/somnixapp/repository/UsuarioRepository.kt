package com.example.somnixapp.repository

import com.example.somnixapp.api.ApiClient
import com.example.somnixapp.models.request.LoginRequest
import com.example.somnixapp.models.request.RegisterRequest

class UsuarioRepository {

    suspend fun login(email: String, password: String) =
        ApiClient.apiService.login(LoginRequest(email, password))

    suspend fun registrar(nombre: String, email: String, password: String) =
        ApiClient.apiService.registrar(RegisterRequest(nombre, email, password))
}