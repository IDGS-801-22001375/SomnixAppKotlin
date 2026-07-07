package com.example.somnixapp.repository

import com.example.somnixapp.api.ApiClient
import com.example.somnixapp.models.request.RutaRequest

class RutaRepository {

    suspend fun obtenerRutas() =
        ApiClient.apiService.obtenerRutas()

    suspend fun obtenerRutaPorId(id: String) =
        ApiClient.apiService.obtenerRutaPorId(id)

    suspend fun crearRuta(request: RutaRequest) =
        ApiClient.apiService.crearRuta(request)

    suspend fun actualizarRuta(
        id: String,
        request: RutaRequest
    ) =
        ApiClient.apiService.actualizarRuta(id, request)

    suspend fun eliminarRuta(id: String) =
        ApiClient.apiService.eliminarRuta(id)

    suspend fun obtenerAlertasPorRuta(rutaId: String) =
        ApiClient.apiService.obtenerAlertasPorRuta(rutaId)
}