package com.example.somnixapp.repository

import com.example.somnixapp.network.ApagarAlarmaRequest
import com.example.somnixapp.network.IniciarViajeRequest
import com.example.somnixapp.network.NecesidadConductorRequest
import com.example.somnixapp.network.PythonApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class PythonRepository {

    private val api: PythonApiService

    init {
        val retrofit = Retrofit.Builder()
            // Emulador Android
            //.baseUrl("http://10.0.2.2:8000/")
            // Celular físico
            .baseUrl("http://192.168.1.72:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(PythonApiService::class.java)
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
            imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        )
    )

    suspend fun pausarViaje() = api.pausarViaje()
}