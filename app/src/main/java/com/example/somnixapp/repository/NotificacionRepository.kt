package com.example.somnixapp.repository

import com.example.somnixapp.api.ApiClient

class NotificacionRepository {

    suspend fun obtenerNotificacionesPorUsuario(
        usuarioId: String
    ) = ApiClient.apiService
        .obtenerNotificacionesPorUsuario(
            usuarioId
        )

    suspend fun obtenerUltimaAdministrativa(
        usuarioId: String
    ) = ApiClient.apiService
        .obtenerUltimaNotificacionAdministrativa(
            usuarioId
        )

    suspend fun marcarComoLeida(
        id: String
    ) = ApiClient.apiService
        .marcarNotificacionComoLeida(id)
}