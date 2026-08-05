package com.example.somnixapp.ble

import org.json.JSONObject

data class GorraTelemetry(
    val pitch: Double,
    val roll: Double,
    val nivelAlerta: Int,
    val modoTest: Boolean,
    val estadoViaje: Int,
    val codigoHttp: Int,
    val tiempoReaccionMs: Long,
    val jsonOriginal: String
) {

    companion object {

        fun desdeJson(
            contenido: String
        ): GorraTelemetry? {
            return try {
                val json = JSONObject(contenido)

                /*
                 * Los mensajes de sincronización no son telemetría.
                 */
                if (
                    json.optString("tipo")
                        .equals("sync", ignoreCase = true)
                ) {
                    return null
                }

                if (!json.has("p") || !json.has("n")) {
                    return null
                }

                GorraTelemetry(
                    pitch = json.optDouble("p", 0.0),
                    roll = json.optDouble("r", 0.0),
                    nivelAlerta = json.optInt("n", 0),
                    modoTest = json.optInt("t", 0) == 1,
                    estadoViaje = json.optInt("v", 0),
                    codigoHttp = json.optInt("hc", 0),
                    tiempoReaccionMs = json.optLong("tr", 0L),
                    jsonOriginal = contenido
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}