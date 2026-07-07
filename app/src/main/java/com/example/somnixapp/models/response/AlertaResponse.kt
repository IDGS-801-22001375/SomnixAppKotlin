package com.example.somnixapp.models.response
import com.google.gson.annotations.SerializedName

data class AlertaResponse(
    @SerializedName(value = "id", alternate = ["Id"])
    val id: String = "",

    @SerializedName(value = "usuarioId", alternate = ["UsuarioId"])
    val usuarioId: String = "",

    @SerializedName(value = "rutaId", alternate = ["RutaId"])
    val rutaId: String = "",

    @SerializedName(value = "tipo", alternate = ["Tipo"])
    val tipo: String = "",

    @SerializedName(value = "nivel", alternate = ["Nivel"])
    val nivel: String = "",

    @SerializedName(value = "mensaje", alternate = ["Mensaje"])
    val mensaje: String = "",

    @SerializedName(value = "atendida", alternate = ["Atendida"])
    val atendida: Boolean = false,

    @SerializedName(value = "fechaRegistro", alternate = ["FechaRegistro"])
    val fechaRegistro: String = ""
)