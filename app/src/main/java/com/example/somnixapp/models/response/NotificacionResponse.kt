package com.example.somnixapp.models.response

data class NotificacionResponse(
    val id: String = "",

    val usuarioId: String = "",
    val rutaId: String = "",

    val titulo: String = "",
    val mensaje: String = "",
    val tipo: String = "",
    val nivel: String = "",
    val origen: String = "",

    val leida: Boolean = false,
    val atendida: Boolean = false,

    val estado: String = "",

    val respuestaConductorId: String = "",

    /*
     * Se conservan ambas fechas porque Firebase
     * contiene registros anteriores y actuales.
     */
    val fechaEnvio: String? = null,
    val fechaRegistro: String? = null
) {
    fun obtenerTituloVisible(): String {
        return titulo
            .takeIf { it.isNotBlank() }
            ?: "Respuesta de SOMNIX"
    }

    fun obtenerMensajeVisible(): String {
        return mensaje
            .takeIf { it.isNotBlank() }
            ?: "Tienes una nueva respuesta de SOMNIX."
    }

    fun esRespuestaAdministrativa(): Boolean {
        return tipo.equals(
            "respuesta_admin",
            ignoreCase = true
        )
    }

    fun obtenerFechaDisponible(): String? {
        return fechaEnvio
            ?.takeIf { it.isNotBlank() }
            ?: fechaRegistro
                ?.takeIf { it.isNotBlank() }
    }
}