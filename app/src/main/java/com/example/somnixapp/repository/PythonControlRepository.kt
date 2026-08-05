package com.example.somnixapp.repository

import com.example.somnixapp.network.ApagarAlarmaRequest
import com.example.somnixapp.network.IniciarViajeRequest
import com.example.somnixapp.network.NecesidadConductorRequest
import com.example.somnixapp.network.PythonApiProvider

class PythonControlRepository {

    private val api =
        PythonApiProvider.controlApi

    suspend fun iniciarViaje(
        usuarioId: String,
        rutaId: String,
        nombreRuta: String?
    ) = api.iniciarViaje(
        IniciarViajeRequest(
            usuarioId = usuarioId,
            rutaId = rutaId,
            nombreRuta = nombreRuta
        )
    )

    suspend fun pausarViaje() =
        api.pausarViaje()

    suspend fun reanudarViaje() =
        api.reanudarViaje()

    suspend fun terminarViaje(
        usuarioId: String,
        rutaId: String
    ) = api.terminarViaje(
        ApagarAlarmaRequest(
            usuarioId = usuarioId,
            rutaId = rutaId
        )
    )

    suspend fun apagarAlarma(
        usuarioId: String,
        rutaId: String
    ) = api.apagarAlarma(
        ApagarAlarmaRequest(
            usuarioId = usuarioId,
            rutaId = rutaId
        )
    )

    suspend fun registrarNecesidad(
        usuarioId: String,
        rutaId: String,
        tipo: String,
        mensaje: String
    ) = api.registrarNecesidad(
        NecesidadConductorRequest(
            usuarioId = usuarioId,
            rutaId = rutaId,
            tipo = tipo,
            mensaje = mensaje
        )
    )

    suspend fun obtenerEstado() =
        api.obtenerEstado()
}