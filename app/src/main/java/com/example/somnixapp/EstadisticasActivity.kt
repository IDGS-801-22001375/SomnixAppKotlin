package com.example.somnixapp

import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.PythonRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch

class EstadisticasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val pythonRepository = PythonRepository()

    private lateinit var txtInsightPrincipal: TextView
    private lateinit var txtDetalleInsight: TextView
    private lateinit var txtTotalRutas: TextView
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
        txtDetalleInsight = findViewById(R.id.txtDetalleInsight)
        txtTotalRutas = findViewById(R.id.txtTotalRutas)
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

        if (usuarioId.isNullOrEmpty()) {
            mostrarErrorUsuario()
            return
        }

        lifecycleScope.launch {
            try {
                mostrarCargando()

                val response = pythonRepository.obtenerEstadisticas(usuarioId)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!

                    txtTotalRutas.text = data.totalRutas.toString()
                    txtTotalAlertas.text = data.totalAlertas.toString()
                    txtRutaRiesgo.text = data.rutaMayorRiesgo
                    txtNivelFrecuente.text = data.nivelMasFrecuente

                    txtInsightPrincipal.text = "Riesgo general: ${data.riesgoGeneral}"

                    txtDetalleInsight.text =
                        "Viajes analizados: ${data.totalViajes}\n" +
                                "Necesidad frecuente: ${data.necesidadMasSolicitada}"

                    txtFatigaMaxima.text = "Fatiga máxima: ${data.fatigaMaxima}%"
                    txtFatigaPromedio.text = "Fatiga promedio: ${data.fatigaPromedio}%"
                    txtBostezos.text = "Bostezos detectados: ${data.bostezosTotales}"
                    txtOjosCerrados.text = "Eventos de ojos cerrados: ${data.ojosCerradosTotales}"

                    progressRiesgoGeneral.progress = calcularProgresoRiesgo(data.riesgoGeneral)
                    progressFatigaMaxima.progress = data.fatigaMaxima.coerceIn(0, 100)
                    progressFatigaPromedio.progress = data.fatigaPromedio.toInt().coerceIn(0, 100)
                    progressBostezos.progress = data.bostezosTotales.coerceIn(0, 10)
                    progressOjosCerrados.progress = data.ojosCerradosTotales.coerceIn(0, 10)

                    txtPatron.text =
                        "La ruta con mayor concentración de riesgo es ${data.rutaMayorRiesgo}. " +
                                "El nivel de alerta más frecuente fue ${data.nivelMasFrecuente}."

                    txtConocimiento.text = data.conocimientoExtraido

                } else {
                    mostrarErrorServidor()
                }

            } catch (e: Exception) {
                txtInsightPrincipal.text = "Error al cargar estadísticas"
                txtDetalleInsight.text = e.message ?: "No fue posible obtener la información del usuario."
                txtConocimiento.text = "Revisa la IP del servidor Python y que FastAPI esté corriendo."
            }
        }
    }

    private fun mostrarCargando() {
        txtInsightPrincipal.text = "Analizando datos..."
        txtDetalleInsight.text = "Extrayendo conocimiento desde Firebase con Python..."

        txtTotalRutas.text = "0"
        txtTotalAlertas.text = "0"
        txtRutaRiesgo.text = "-"
        txtNivelFrecuente.text = "-"

        txtFatigaMaxima.text = "Fatiga máxima: 0%"
        txtFatigaPromedio.text = "Fatiga promedio: 0%"
        txtBostezos.text = "Bostezos detectados: 0"
        txtOjosCerrados.text = "Eventos de ojos cerrados: 0"

        progressRiesgoGeneral.progress = 0
        progressFatigaMaxima.progress = 0
        progressFatigaPromedio.progress = 0
        progressBostezos.progress = 0
        progressOjosCerrados.progress = 0

        txtPatron.text = "Buscando patrones en los datos del conductor..."
        txtConocimiento.text = "Procesando información..."
    }

    private fun mostrarErrorUsuario() {
        txtInsightPrincipal.text = "No se encontró el usuario"
        txtDetalleInsight.text = "Inicia sesión nuevamente para consultar tus estadísticas."
        txtConocimiento.text = "No fue posible identificar al usuario logueado."
    }

    private fun mostrarErrorServidor() {
        txtInsightPrincipal.text = "No se pudieron cargar estadísticas"
        txtDetalleInsight.text = "Error en el servidor"
        txtConocimiento.text = "Endpoint esperado: /api/estadisticas/usuario/{usuarioId}"
    }

    private fun calcularProgresoRiesgo(riesgo: String): Int {
        return when (riesgo.lowercase()) {
            "alto" -> 85
            "medio" -> 55
            "bajo" -> 25
            else -> 0
        }
    }
}