package com.example.somnixapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.somnixapp.R

class NotificationHelper(
    private val context: Context
) {

    private val channelId =
        "somnix_alertas_channel_v2"

    private val ultimaNotificacion =
        mutableMapOf<String, Long>()

    init {
        crearCanal()
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                channelId,
                "Alertas SOMNIX",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Alertas importantes de fatiga y viaje"

                enableVibration(true)
            }

            val manager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(canal)
        }
    }

    fun mostrarNotificacion(
        titulo: String,
        mensaje: String,
        cooldownMs: Long = 3_000L
    ) {
        val ahora = System.currentTimeMillis()
        val clave = titulo.trim().uppercase()
        val ultimoTiempo =
            ultimaNotificacion[clave] ?: 0L

        /*
         * No muestra otra notificación del mismo tipo
         * dentro del periodo configurado.
         */
        if (ahora - ultimoTiempo < cooldownMs) {
            return
        }

        ultimaNotificacion[clave] = ahora

        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(mensaje)
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        /*
         * El mismo título utiliza el mismo ID.
         * En vez de crear 20 notificaciones BLE,
         * actualiza una sola.
         */
        manager.notify(
            clave.hashCode(),
            notification
        )
    }

    fun cancelarNotificacion(
        titulo: String
    ) {
        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.cancel(
            titulo.trim().uppercase().hashCode()
        )
    }

    fun cancelarTodas() {
        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.cancelAll()
    }
}