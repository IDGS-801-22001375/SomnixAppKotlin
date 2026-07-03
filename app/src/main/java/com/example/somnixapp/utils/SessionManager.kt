package com.example.somnixapp.utils

import android.content.Context
import com.example.somnixapp.models.response.AuthResponse

class SessionManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(
        "somnix_session",
        Context.MODE_PRIVATE
    )

    fun guardarSesion(usuario: AuthResponse) {
        sharedPreferences.edit()
            .putString("TOKEN", usuario.token)
            .putString("ID", usuario.id)
            .putString("NOMBRE", usuario.nombre)
            .putString("EMAIL", usuario.email)
            .putString("ROL", usuario.rol)
            .apply()
    }

    fun guardarToken(token: String) {
        sharedPreferences.edit()
            .putString("TOKEN", token)
            .apply()
    }

    fun obtenerToken(): String? {
        return sharedPreferences.getString("TOKEN", null)
    }

    fun obtenerUsuarioId(): String? {
        return sharedPreferences.getString("ID", null)
    }

    fun obtenerNombre(): String? {
        return sharedPreferences.getString("NOMBRE", null)
    }

    fun obtenerEmail(): String? {
        return sharedPreferences.getString("EMAIL", null)
    }

    fun obtenerRol(): String? {
        return sharedPreferences.getString("ROL", null)
    }

    fun haySesion(): Boolean {
        return obtenerToken() != null
    }

    fun cerrarSesion() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }
}