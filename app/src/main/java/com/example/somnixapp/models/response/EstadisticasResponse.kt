package com.example.somnixapp.models.response

data class EstadisticasResponse(
    val ok: Boolean,
    val usuarioId: String,
    val totalRutas: Int,
    val totalViajes: Int,
    val totalAlertas: Int,
    val fatigaMaxima: Int,
    val fatigaPromedio: Double,
    val rutaMayorRiesgo: String,
    val nivelMasFrecuente: String,
    val necesidadMasSolicitada: String,
    val bostezosTotales: Int,
    val ojosCerradosTotales: Int,
    val riesgoGeneral: String,
    val conocimientoExtraido: String
)