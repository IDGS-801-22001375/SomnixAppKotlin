package com.example.somnixapp.models.rutas

data class MapaRuta(
    // Estructura actual
    val polyline: String = "",

    // Estructura anterior
    val polylineCodificada: String = "",

    val modoViaje: String = "DRIVE",
    val proveedor: String = "Google Maps"
) {
    fun obtenerPolyline(): String {
        return polyline
            .takeIf { it.isNotBlank() }
            ?: polylineCodificada
    }
}