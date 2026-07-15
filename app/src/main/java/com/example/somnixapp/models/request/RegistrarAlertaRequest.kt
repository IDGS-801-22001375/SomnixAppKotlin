package com.example.somnixapp.models.request

data class RegistrarAlertaRequest(
    val usuarioId: String,
    val rutaId: String,
    val tipo: String,
    val mensaje: String,
    val nivel: String,
    val origen: String
)