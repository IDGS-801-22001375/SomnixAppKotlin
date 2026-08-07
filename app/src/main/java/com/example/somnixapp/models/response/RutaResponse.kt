package com.example.somnixapp.models.response

import com.example.somnixapp.models.rutas.Coordenada
import com.example.somnixapp.models.rutas.MapaRuta
import com.example.somnixapp.models.rutas.PuntoRuta

data class RutaResponse(
    val id: String = "",

    // Estructura anterior
    val usuarioId: String = "",
    val nombre: String = "",

    val origen: PuntoRuta? = null,
    val destino: PuntoRuta? = null,

    val mapa: MapaRuta? = null,

    val ubicacionActual: Coordenada? = null,

    val recorrido: Map<String, Coordenada>? = null,

    // Estructura nueva
    val conductorAsignadoId: String = "",
    val conductorAsignadoNombre: String = "",

    val origenTexto: String = "",
    val destinoTexto: String = "",

    val prioridad: String = "",
    val tipoRuta: String = "",

    // Campos compartidos
    val distanciaKm: Double = 0.0,
    val duracionMinutos: Int = 0,

    val estado: String = "",

    val fechaCreacion: String = "",
    val fechaAsignacion: String? = null,

    val fechaTerminada: String? = null,
    val fechaTerminacion: String? = null
) {
    fun obtenerOrigenTexto(): String {
        return origenTexto
            .takeIf { it.isNotBlank() }
            ?: origen?.nombre
                ?.takeIf { it.isNotBlank() }
            ?: origen?.direccion
                ?.takeIf { it.isNotBlank() }
            ?: "Origen no disponible"
    }

    fun obtenerDestinoTexto(): String {
        return destinoTexto
            .takeIf { it.isNotBlank() }
            ?: destino?.nombre
                ?.takeIf { it.isNotBlank() }
            ?: destino?.direccion
                ?.takeIf { it.isNotBlank() }
            ?: "Destino no disponible"
    }

    fun obtenerNombreVisible(): String {
        return nombre
            .takeIf { it.isNotBlank() }
            ?: "${obtenerOrigenTexto()} - ${obtenerDestinoTexto()}"
    }

    fun obtenerFechaTerminacion(): String? {
        return fechaTerminada
            ?.takeIf { it.isNotBlank() }
            ?: fechaTerminacion
                ?.takeIf { it.isNotBlank() }
    }
}