package com.example.somnixapp.models.request

data class RutaRequest(
    val usuarioId: String,
    val nombre: String,
    val origen: String,
    val destino: String,
    val distanciaKm: Double,
    val duracionMinutos: Int,
    val estado: String
)