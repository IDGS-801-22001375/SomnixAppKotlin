package com.example.somnixapp.models.response

data class AlertaResponse(
    val id: String,
    val usuarioId: String,
    val rutaId: String,
    val tipo: String,
    val nivel: String,
    val mensaje: String,
    val atendida: Boolean,
    val fechaRegistro: String
)