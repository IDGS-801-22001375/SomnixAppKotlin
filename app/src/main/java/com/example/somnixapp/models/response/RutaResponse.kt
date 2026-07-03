package com.example.somnixapp.models.response

data class RutaResponse(
    val id: String,
    val usuarioId: String,
    val nombre: String,
    val origen: String,
    val destino: String,
    val distanciaKm: Double,
    val duracionMinutos: Int,
    val estado: String,
    val fechaCreacion: String
)