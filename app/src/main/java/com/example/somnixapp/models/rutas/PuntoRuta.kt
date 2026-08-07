package com.example.somnixapp.models.rutas

data class PuntoRuta(
    val nombre: String = "",
    val direccion: String = "",
    val placeId: String = "",

    // Estructura normalizada actual
    val lat: Double = 0.0,
    val lng: Double = 0.0,

    // Se conservan por compatibilidad
    val latitud: Double? = null,
    val longitud: Double? = null
) {
    fun obtenerLatitud(): Double {
        return if (
            lat in -90.0..90.0 &&
            lat != 0.0
        ) {
            lat
        } else {
            latitud ?: 0.0
        }
    }

    fun obtenerLongitud(): Double {
        return if (
            lng in -180.0..180.0 &&
            lng != 0.0
        ) {
            lng
        } else {
            longitud ?: 0.0
        }
    }

    fun tieneCoordenadasValidas(): Boolean {
        val latitudFinal = obtenerLatitud()
        val longitudFinal = obtenerLongitud()

        return latitudFinal in -90.0..90.0 &&
                longitudFinal in -180.0..180.0 &&
                !(
                        latitudFinal == 0.0 &&
                                longitudFinal == 0.0
                        )
    }

    fun normalizado(): PuntoRuta {
        return copy(
            lat = obtenerLatitud(),
            lng = obtenerLongitud()
        )
    }
}