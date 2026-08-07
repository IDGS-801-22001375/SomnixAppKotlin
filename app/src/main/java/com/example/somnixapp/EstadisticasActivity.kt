package com.example.somnixapp

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.PythonRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch
import java.util.Locale

class EstadisticasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val pythonRepository = PythonRepository()

    private lateinit var txtInsightPrincipal: TextView
    private lateinit var txtRiesgoPorcentaje: TextView
    private lateinit var txtDetalleInsight: TextView
    private lateinit var txtTotalRutas: TextView
    private lateinit var txtTotalMuestras: TextView
    private lateinit var txtTotalAlertas: TextView
    private lateinit var txtRutaRiesgo: TextView
    private lateinit var txtNivelFrecuente: TextView
    private lateinit var txtConocimiento: TextView
    private lateinit var txtFatigaMaxima: TextView
    private lateinit var txtFatigaPromedio: TextView
    private lateinit var txtBostezos: TextView
    private lateinit var txtOjosCerrados: TextView
    private lateinit var txtPatron: TextView

    private lateinit var progressRiesgoGeneral: ProgressBar
    private lateinit var progressFatigaMaxima: ProgressBar
    private lateinit var progressFatigaPromedio: ProgressBar
    private lateinit var progressBostezos: ProgressBar
    private lateinit var progressOjosCerrados: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_estadisticas)

        sessionManager = SessionManager(this)
        inicializarVistas()
        configurarClicks()
        cargarEstadisticas()
    }

    private fun inicializarVistas() {
        txtInsightPrincipal = findViewById(R.id.txtInsightPrincipal)
        txtRiesgoPorcentaje = findViewById(R.id.txtRiesgoPorcentaje)
        txtDetalleInsight = findViewById(R.id.txtDetalleInsight)
        txtTotalRutas = findViewById(R.id.txtTotalRutas)
        txtTotalMuestras = findViewById(R.id.txtTotalMuestras)
        txtTotalAlertas = findViewById(R.id.txtTotalAlertas)
        txtRutaRiesgo = findViewById(R.id.txtRutaRiesgo)
        txtNivelFrecuente = findViewById(R.id.txtNivelFrecuente)
        txtConocimiento = findViewById(R.id.txtConocimiento)
        txtFatigaMaxima = findViewById(R.id.txtFatigaMaxima)
        txtFatigaPromedio = findViewById(R.id.txtFatigaPromedio)
        txtBostezos = findViewById(R.id.txtBostezos)
        txtOjosCerrados = findViewById(R.id.txtOjosCerrados)
        txtPatron = findViewById(R.id.txtPatron)

        progressRiesgoGeneral = findViewById(R.id.progressRiesgoGeneral)
        progressFatigaMaxima = findViewById(R.id.progressFatigaMaxima)
        progressFatigaPromedio = findViewById(R.id.progressFatigaPromedio)
        progressBostezos = findViewById(R.id.progressBostezos)
        progressOjosCerrados = findViewById(R.id.progressOjosCerrados)
    }

    private fun configurarClicks() {
        findViewById<ImageView>(R.id.btnVolver).setOnClickListener {
            finish()
        }
    }

    private fun cargarEstadisticas() {
        val usuarioId = sessionManager.obtenerUsuarioId()

        if (usuarioId.isNullOrBlank()) {
            mostrarErrorUsuario()
            return
        }

        lifecycleScope.launch {
            mostrarCargando()

            try {
                val response = pythonRepository.obtenerEstadisticas(usuarioId)
                val data = response.body()

                Log.d(TAG, "Usuario=$usuarioId HTTP=${response.code()} data=$data")

                if (response.isSuccessful && data?.ok == true) {
                    val totalRutas = (data.totalRutas ?: 0).coerceAtLeast(0)
                    val totalMuestras = (data.totalMuestras ?: 0).coerceAtLeast(0)
                    val totalAlertas = (data.totalAlertas ?: 0).coerceAtLeast(0)
                    val fatigaMaxima = (data.fatigaMaxima ?: 0).coerceIn(0, 100)
                    val fatigaPromedio = (data.fatigaPromedio ?: 0.0).coerceIn(0.0, 100.0)
                    val bostezos = (data.bostezosTotales ?: 0).coerceAtLeast(0)
                    val ojosCerrados = (data.ojosCerradosTotales ?: 0).coerceAtLeast(0)
                    val rutaRiesgo = textoSeguro(data.rutaMayorRiesgo, "Sin datos")
                    val nivelFrecuente = textoSeguro(data.nivelMasFrecuente, "Sin datos")
                    val necesidad = textoSeguro(data.necesidadMasSolicitada, "Sin datos")
                    val riesgo = textoSeguro(data.riesgoGeneral, "Bajo")
                    val conocimiento = textoSeguro(
                        data.conocimientoExtraido,
                        "Todavía no existen datos suficientes para generar una recomendación."
                    )

                    mostrarEstadisticas(
                        totalRutas,
                        totalMuestras,
                        totalAlertas,
                        fatigaMaxima,
                        fatigaPromedio,
                        bostezos,
                        ojosCerrados,
                        rutaRiesgo,
                        nivelFrecuente,
                        necesidad,
                        riesgo,
                        conocimiento
                    )
                } else {
                    val errorBody = response.errorBody()?.string()
                    mostrarErrorServidor(
                        data?.detalle
                            ?: data?.mensaje
                            ?: errorBody
                            ?: "El servidor no devolvió información."
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Error cargando estadísticas", error)
                mostrarErrorServidor(
                    error.message ?: "No fue posible conectar con el análisis."
                )
            }
        }
    }

    private fun mostrarEstadisticas(
        totalRutas: Int,
        totalMuestras: Int,
        totalAlertas: Int,
        fatigaMaxima: Int,
        fatigaPromedio: Double,
        bostezos: Int,
        ojosCerrados: Int,
        rutaRiesgo: String,
        nivelFrecuente: String,
        necesidad: String,
        riesgo: String,
        conocimiento: String
    ) {
        val riesgoProgreso = calcularProgresoRiesgo(riesgo)

        txtInsightPrincipal.text = riesgo.uppercase(Locale.getDefault())
        txtRiesgoPorcentaje.text = "$riesgoProgreso%"
        txtDetalleInsight.text = "Necesidad frecuente: $necesidad"

        txtTotalRutas.text = totalRutas.toString()
        txtTotalMuestras.text = totalMuestras.toString()
        txtTotalAlertas.text = totalAlertas.toString()
        txtRutaRiesgo.text = rutaRiesgo
        txtNivelFrecuente.text = nivelFrecuente

        txtFatigaMaxima.text = "$fatigaMaxima%"
        txtFatigaPromedio.text = "${formatearDecimal(fatigaPromedio)}%"
        txtBostezos.text = bostezos.toString()
        txtOjosCerrados.text = ojosCerrados.toString()

        animarProgreso(progressRiesgoGeneral, riesgoProgreso)
        animarProgreso(progressFatigaMaxima, fatigaMaxima)
        animarProgreso(progressFatigaPromedio, fatigaPromedio.toInt())
        animarProgreso(progressBostezos, bostezos.coerceIn(0, progressBostezos.max))
        animarProgreso(
            progressOjosCerrados,
            ojosCerrados.coerceIn(0, progressOjosCerrados.max)
        )

        txtPatron.text = if (totalMuestras == 0) {
            "Aún no existen muestras suficientes para encontrar un patrón."
        } else {
            "La mayor concentración de riesgo está en $rutaRiesgo. " +
                    "El nivel más frecuente fue $nivelFrecuente; se detectaron " +
                    "$bostezos bostezos y $ojosCerrados eventos de ojos cerrados."
        }

        txtConocimiento.text = conocimiento
    }

    private fun mostrarCargando() {
        txtInsightPrincipal.text = "ANALIZANDO"
        txtRiesgoPorcentaje.text = "--"
        txtDetalleInsight.text = "Procesando señales del conductor..."
        limpiarValores()
        txtPatron.text = "Buscando patrones de comportamiento..."
        txtConocimiento.text = "Generando recomendación preventiva..."
    }

    private fun mostrarErrorUsuario() {
        limpiarValores()
        txtInsightPrincipal.text = "SIN USUARIO"
        txtRiesgoPorcentaje.text = "--"
        txtDetalleInsight.text = "Inicia sesión nuevamente."
        txtPatron.text = "No fue posible analizar los datos."
        txtConocimiento.text = "No se identificó al usuario conectado."
    }

    private fun mostrarErrorServidor(mensaje: String) {
        limpiarValores()
        txtInsightPrincipal.text = "SIN DATOS"
        txtRiesgoPorcentaje.text = "--"
        txtDetalleInsight.text = mensaje
        txtPatron.text = "No se pudo completar el análisis."
        txtConocimiento.text = "Comprueba que el servicio Python esté disponible."
    }

    private fun limpiarValores() {
        txtTotalRutas.text = "0"
        txtTotalMuestras.text = "0"
        txtTotalAlertas.text = "0"
        txtRutaRiesgo.text = "-"
        txtNivelFrecuente.text = "-"
        txtFatigaMaxima.text = "0%"
        txtFatigaPromedio.text = "0%"
        txtBostezos.text = "0"
        txtOjosCerrados.text = "0"
        progressRiesgoGeneral.progress = 0
        progressFatigaMaxima.progress = 0
        progressFatigaPromedio.progress = 0
        progressBostezos.progress = 0
        progressOjosCerrados.progress = 0
    }

    private fun animarProgreso(barra: ProgressBar, valor: Int) {
        ObjectAnimator.ofInt(barra, "progress", barra.progress, valor)
            .setDuration(650L)
            .start()
    }

    private fun textoSeguro(valor: String?, predeterminado: String): String {
        return valor?.trim()?.takeIf { it.isNotEmpty() } ?: predeterminado
    }

    private fun formatearDecimal(valor: Double): String {
        return String.format(Locale.getDefault(), "%.1f", valor)
    }

    private fun calcularProgresoRiesgo(riesgo: String): Int {
        return when (riesgo.trim().lowercase()) {
            "alto" -> 85
            "medio" -> 55
            "bajo" -> 25
            else -> 0
        }
    }

    companion object {
        private const val TAG = "ESTADISTICAS"
    }
}