package com.example.somnixapp.ble

import java.util.UUID

object BleConstants {

    const val NOMBRE_GORRA = "SOMNIX_IDGS901"

    val SERVICE_UUID: UUID =
        UUID.fromString(
            "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
        )

    val TX_UUID: UUID =
        UUID.fromString(
            "beb5483e-36e1-4688-b7f5-ea07361b26a8"
        )

    val RX_UUID: UUID =
        UUID.fromString(
            "8a531e21-0a4a-4467-9bb3-392da798a7eb"
        )

    val CCCD_UUID: UUID =
        UUID.fromString(
            "00002902-0000-1000-8000-00805f9b34fb"
        )

    const val CANAL_NOTIFICACION_ID =
        "somnix_ble_connection"

    const val NOTIFICACION_ID = 901

    const val TIEMPO_ESCANEO_MS = 15_000L
    const val TIEMPO_CONEXION_MS = 15_000L
}