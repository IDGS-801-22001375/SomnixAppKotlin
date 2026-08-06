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

    // Estructura nueva
    val conductorAsignadoId: String = "",
    val conductorAsignadoNombre: String = "",
    val origenTexto: String = "",
    val destinoTexto: String = "",
    val prioridad: String = "",
    val tipoRuta: String = "",

    val distanciaKm: Double = 0.0,
    val duracionMinutos: Int = 0,
    val estado: String = "",
    val fechaCreacion: String = "",
    val fechaAsignacion: String? = null,
    val fechaTerminada: String? = null,
    val fechaTerminacion: String? = null
)