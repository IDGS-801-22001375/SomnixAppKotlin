package com.example.somnixapp.models.response

import com.example.somnixapp.models.rutas.Coordenada
import com.example.somnixapp.models.rutas.MapaRuta
import com.example.somnixapp.models.rutas.PuntoRuta

data class RutaResponse(
    val id: String,
    val usuarioId: String,
    val nombre: String,
    val origen: PuntoRuta,
    val destino: PuntoRuta,
    val distanciaKm: Double,
    val duracionMinutos: Int,
    val mapa: MapaRuta? = null,
    val ubicacionActual: Coordenada? = null,
    val estado: String,
    val fechaCreacion: String
)