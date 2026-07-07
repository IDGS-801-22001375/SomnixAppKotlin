package com.example.somnixapp.models.request

import com.example.somnixapp.models.rutas.PuntoRuta

data class RutaRequest(
    val usuarioId: String,
    val nombre: String,
    val origen: PuntoRuta,
    val destino: PuntoRuta,
    val estado: String = "Pendiente"
)