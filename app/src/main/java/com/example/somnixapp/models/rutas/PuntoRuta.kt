package com.example.somnixapp.models.rutas

data class PuntoRuta(
    val nombre: String = "",
    val direccion: String = "",
    val placeId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)