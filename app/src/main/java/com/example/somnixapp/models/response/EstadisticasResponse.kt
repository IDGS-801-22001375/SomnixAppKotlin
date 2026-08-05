package com.example.somnixapp.models.response

data class EstadisticasResponse(
    val ok: Boolean? = false,
    val mensaje: String? = null,
    val detalle: String? = null,
    val usuarioId: String? = null,
    val totalRutas: Int? = 0,
    val totalViajes: Int? = 0,
    val totalAlertas: Int? = 0,
    val fatigaMaxima: Int? = 0,
    val fatigaPromedio: Double? = 0.0,
    val rutaMayorRiesgo: String? = "Sin datos",
    val nivelMasFrecuente: String? = "Sin datos",
    val necesidadMasSolicitada: String? = "Sin datos",
    val bostezosTotales: Int? = 0,
    val ojosCerradosTotales: Int? = 0,
    val riesgoGeneral: String? = "Bajo",
    val conocimientoExtraido: String? = "Sin datos"
)