package com.example.somnixapp.repository

import com.example.somnixapp.network.ApagarAlarmaRequest
import com.example.somnixapp.network.IniciarViajeRequest
import com.example.somnixapp.network.NecesidadConductorRequest
import com.example.somnixapp.network.PythonApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class PythonRepository {

    private val api: PythonApiService

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(
                "https://monitoreosomnixpython.onrender.com/"
            )
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()

        api = retrofit.create(
            PythonApiService::class.java
        )
    }

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

    suspend fun analizarFrame(
        usuarioId: String,
        rutaId: String,
        imageFile: File
    ) = api.analizarFrame(
        usuarioId = usuarioId,
        rutaId = rutaId,
        file = MultipartBody.Part.createFormData(
            "file",
            imageFile.name,
            imageFile.asRequestBody(
                "image/jpeg".toMediaTypeOrNull()
            )
        )
    )

    suspend fun obtenerEstadisticas(
        usuarioId: String
    ) = api.obtenerEstadisticas(usuarioId)
}