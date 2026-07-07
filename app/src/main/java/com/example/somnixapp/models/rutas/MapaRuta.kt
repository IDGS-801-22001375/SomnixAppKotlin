package com.example.somnixapp.models.rutas

data class MapaRuta(
    val polyline: String = "",
    val modoViaje: String = "DRIVE",
    val proveedor: String = "Google Maps"
)