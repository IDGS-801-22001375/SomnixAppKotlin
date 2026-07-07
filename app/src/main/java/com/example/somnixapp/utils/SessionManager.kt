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

    fun guardarRutaSeleccionada(id: String, nombre: String) {
        sharedPreferences.edit()
            .putString("RUTA_ID", id)
            .putString("RUTA_NOMBRE", nombre)
            .apply()
    }

    fun obtenerRutaId(): String? {
        return sharedPreferences.getString("RUTA_ID", null)
    }

    fun obtenerNombreRuta(): String? {
        return sharedPreferences.getString("RUTA_NOMBRE", null)
    }

    fun limpiarRutaSeleccionada() {
        sharedPreferences.edit()
            .remove("RUTA_ID")
            .remove("RUTA_NOMBRE")
            .apply()
    }

    fun guardarEstadoViaje(estado: String) {
        sharedPreferences.edit()
            .putString("ESTADO_VIAJE", estado)
            .apply()
    }

    fun obtenerEstadoViaje(): String {
        return sharedPreferences.getString("ESTADO_VIAJE", "INACTIVO") ?: "INACTIVO"
    }

    fun limpiarViajeActivo() {
        sharedPreferences.edit()
            .remove("RUTA_ID")
            .remove("RUTA_NOMBRE")
            .putString("ESTADO_VIAJE", "INACTIVO")
            .apply()
    }
}