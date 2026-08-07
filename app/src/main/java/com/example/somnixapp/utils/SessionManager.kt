package com.example.somnixapp.utils

import android.content.Context
import com.example.somnixapp.models.response.AuthResponse

class SessionManager(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(
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
        return sharedPreferences.getString(
            "TOKEN",
            null
        )
    }

    fun obtenerUsuarioId(): String? {
        return sharedPreferences.getString(
            "ID",
            null
        )
    }

    fun obtenerNombre(): String? {
        return sharedPreferences.getString(
            "NOMBRE",
            null
        )
    }

    fun obtenerEmail(): String? {
        return sharedPreferences.getString(
            "EMAIL",
            null
        )
    }

    fun obtenerRol(): String? {
        return sharedPreferences.getString(
            "ROL",
            null
        )
    }

    fun haySesion(): Boolean {
        return !obtenerToken().isNullOrBlank()
    }

    fun cerrarSesion() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }

    fun guardarRutaSeleccionada(
        id: String,
        nombre: String
    ) {
        sharedPreferences.edit()
            .putString("RUTA_ID", id.trim())
            .putString("RUTA_NOMBRE", nombre.trim())
            .putInt("FATIGA_ACTUAL", 0)
            .putString("ESTADO_VIAJE", "INACTIVO")
            .apply()
    }

    fun obtenerRutaId(): String? {
        return sharedPreferences
            .getString("RUTA_ID", null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun obtenerNombreRuta(): String? {
        return sharedPreferences
            .getString("RUTA_NOMBRE", null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun limpiarRutaSeleccionada() {
        sharedPreferences.edit()
            .remove("RUTA_ID")
            .remove("RUTA_NOMBRE")
            .remove("FATIGA_ACTUAL")
            .putString("ESTADO_VIAJE", "INACTIVO")
            .apply()
    }

    fun guardarEstadoViaje(estado: String) {
        sharedPreferences.edit()
            .putString(
                "ESTADO_VIAJE",
                estado.trim().uppercase()
            )
            .apply()
    }

    fun obtenerEstadoViaje(): String {
        return sharedPreferences.getString(
            "ESTADO_VIAJE",
            "INACTIVO"
        ) ?: "INACTIVO"
    }

    fun guardarFatigaActual(porcentaje: Int) {
        sharedPreferences.edit()
            .putInt(
                "FATIGA_ACTUAL",
                porcentaje.coerceIn(0, 100)
            )
            .apply()
    }

    fun obtenerFatigaActual(): Int {
        return sharedPreferences.getInt(
            "FATIGA_ACTUAL",
            0
        ).coerceIn(0, 100)
    }

    fun limpiarFatigaActual() {
        sharedPreferences.edit()
            .remove("FATIGA_ACTUAL")
            .apply()
    }

    fun limpiarViajeActivo() {
        sharedPreferences.edit()
            .remove("RUTA_ID")
            .remove("RUTA_NOMBRE")
            .remove("FATIGA_ACTUAL")
            .putString("ESTADO_VIAJE", "INACTIVO")
            .apply()
    }
}