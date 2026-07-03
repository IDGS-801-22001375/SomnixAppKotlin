package com.example.somnixapp.repository

import com.example.somnixapp.api.ApiClient
import com.example.somnixapp.models.request.GoogleLoginRequest
import com.example.somnixapp.models.request.LoginRequest
import com.example.somnixapp.models.request.RegisterRequest
import com.example.somnixapp.models.response.ErrorResponse
import com.google.gson.Gson
import retrofit2.Response

class UsuarioRepository {

    suspend fun login(email: String, password: String) =
        ApiClient.apiService.login(LoginRequest(email, password))

    suspend fun registrar(nombre: String, email: String, password: String) =
        ApiClient.apiService.registrar(RegisterRequest(nombre, email, password))

    suspend fun loginGoogle(idToken: String) =
        ApiClient.apiService.loginGoogle(GoogleLoginRequest(idToken))

    fun obtenerMensajeError(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()

            if (errorBody.isNullOrEmpty()) {
                "Ocurrió un error inesperado."
            } else {
                Gson().fromJson(errorBody, ErrorResponse::class.java).mensaje
            }
        } catch (e: Exception) {
            "Ocurrió un error al leer la respuesta del servidor."
        }
    }
}