package com.example.somnixapp.repository

import com.example.somnixapp.network.PythonApiProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class PythonFrameRepository {

    private val api =
        PythonApiProvider.frameApi

    suspend fun analizarFrame(
        usuarioId: String,
        rutaId: String,
        imageFile: File
    ) = api.analizarFrame(
        usuarioId = usuarioId,
        rutaId = rutaId,
        file =
            MultipartBody.Part.createFormData(
                name = "file",
                filename = imageFile.name,
                body = imageFile.asRequestBody(
                    "image/jpeg".toMediaType()
                )
            )
    )
}