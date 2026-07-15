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
import java.util.Locale

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
        configurarProgressBars()
        cargarEstadisticas()
    }

    /**
     * Enlaza las vistas del XML con la Activity.
     */
    private fun inicializarVistas() {
        txtInsightPrincipal =
            findViewById(R.id.txtInsightPrincipal)

        txtDetalleInsight =
            findViewById(R.id.txtDetalleInsight)

        txtTotalRutas =
            findViewById(R.id.txtTotalRutas)

        txtTotalAlertas =
            findViewById(R.id.txtTotalAlertas)

        txtRutaRiesgo =
            findViewById(R.id.txtRutaRiesgo)

        txtNivelFrecuente =
            findViewById(R.id.txtNivelFrecuente)

        txtConocimiento =
            findViewById(R.id.txtConocimiento)

        txtFatigaMaxima =
            findViewById(R.id.txtFatigaMaxima)

        txtFatigaPromedio =
            findViewById(R.id.txtFatigaPromedio)

        txtBostezos =
            findViewById(R.id.txtBostezos)

        txtOjosCerrados =
            findViewById(R.id.txtOjosCerrados)

        txtPatron =
            findViewById(R.id.txtPatron)

        progressRiesgoGeneral =
            findViewById(R.id.progressRiesgoGeneral)

        progressFatigaMaxima =
            findViewById(R.id.progressFatigaMaxima)

        progressFatigaPromedio =
            findViewById(R.id.progressFatigaPromedio)

        progressBostezos =
            findViewById(R.id.progressBostezos)

        progressOjosCerrados =
            findViewById(R.id.progressOjosCerrados)
    }

    /**
     * Configura el botón para regresar.
     */
    private fun configurarClicks() {
        findViewById<ImageView>(R.id.btnVolver)
            .setOnClickListener {
                finish()
            }
    }

    /**
     * Define el valor máximo de cada barra.
     */
    private fun configurarProgressBars() {
        progressRiesgoGeneral.max = 100
        progressFatigaMaxima.max = 100
        progressFatigaPromedio.max = 100

        // Estas barras representan una escala visual.
        progressBostezos.max = 10
        progressOjosCerrados.max = 10
    }

    /**
     * Solicita las estadísticas al servidor Python.
     */
    private fun cargarEstadisticas() {
        val usuarioId = sessionManager.obtenerUsuarioId()

        if (usuarioId.isNullOrBlank()) {
            mostrarErrorUsuario()
            return
        }

        lifecycleScope.launch {
            mostrarCargando()

            try {
                val response =
                    pythonRepository.obtenerEstadisticas(
                        usuarioId
                    )

                if (!response.isSuccessful) {
                    val codigo = response.code()
                    val mensajeServidor =
                        response.errorBody()?.string()

                    mostrarErrorServidor(
                        "Código HTTP: $codigo",
                        mensajeServidor
                    )

                    return@launch
                }

                val data = response.body()

                if (data == null) {
                    mostrarErrorServidor(
                        "El servidor respondió sin contenido.",
                        null
                    )

                    return@launch
                }

                /*
                 * Gson puede asignar null a propiedades String
                 * aunque en el modelo hayan sido declaradas como
                 * no nulas. Por eso todos los textos se validan.
                 */

                val rutaMayorRiesgo = textoSeguro(
                    data.rutaMayorRiesgo,
                    "Sin datos"
                )

                val nivelMasFrecuente = textoSeguro(
                    data.nivelMasFrecuente,
                    "Sin datos"
                )

                val riesgoGeneral = textoSeguro(
                    data.riesgoGeneral,
                    "Bajo"
                )

                val necesidadMasSolicitada = textoSeguro(
                    data.necesidadMasSolicitada,
                    "Sin datos"
                )

                val conocimientoExtraido = textoSeguro(
                    data.conocimientoExtraido,
                    "Aún no existen suficientes datos " +
                            "para generar conocimiento."
                )

                val totalRutas =
                    data.totalRutas.coerceAtLeast(0)

                val totalViajes =
                    data.totalViajes.coerceAtLeast(0)

                val totalAlertas =
                    data.totalAlertas.coerceAtLeast(0)

                val fatigaMaxima =
                    data.fatigaMaxima.coerceIn(0, 100)

                val fatigaPromedio =
                    data.fatigaPromedio.coerceIn(
                        0.0,
                        100.0
                    )

                val bostezosTotales =
                    data.bostezosTotales.coerceAtLeast(0)

                val ojosCerradosTotales =
                    data.ojosCerradosTotales
                        .coerceAtLeast(0)

                mostrarEstadisticas(
                    totalRutas = totalRutas,
                    totalViajes = totalViajes,
                    totalAlertas = totalAlertas,
                    rutaMayorRiesgo = rutaMayorRiesgo,
                    nivelMasFrecuente = nivelMasFrecuente,
                    riesgoGeneral = riesgoGeneral,
                    necesidadMasSolicitada =
                        necesidadMasSolicitada,
                    fatigaMaxima = fatigaMaxima,
                    fatigaPromedio = fatigaPromedio,
                    bostezosTotales = bostezosTotales,
                    ojosCerradosTotales =
                        ojosCerradosTotales,
                    conocimientoExtraido =
                        conocimientoExtraido
                )

            } catch (e: Exception) {
                mostrarErrorExcepcion(e)
            }
        }
    }

    /**
     * Coloca las estadísticas recibidas en la interfaz.
     */
    private fun mostrarEstadisticas(
        totalRutas: Int,
        totalViajes: Int,
        totalAlertas: Int,
        rutaMayorRiesgo: String,
        nivelMasFrecuente: String,
        riesgoGeneral: String,
        necesidadMasSolicitada: String,
        fatigaMaxima: Int,
        fatigaPromedio: Double,
        bostezosTotales: Int,
        ojosCerradosTotales: Int,
        conocimientoExtraido: String
    ) {
        txtTotalRutas.text =
            totalRutas.toString()

        txtTotalAlertas.text =
            totalAlertas.toString()

        txtRutaRiesgo.text =
            rutaMayorRiesgo

        txtNivelFrecuente.text =
            nivelMasFrecuente

        txtInsightPrincipal.text =
            "Riesgo general: $riesgoGeneral"

        txtDetalleInsight.text =
            "Viajes analizados: $totalViajes\n" +
                    "Necesidad frecuente: " +
                    necesidadMasSolicitada

        txtFatigaMaxima.text =
            "Fatiga máxima: $fatigaMaxima%"

        txtFatigaPromedio.text =
            "Fatiga promedio: " +
                    formatearDecimal(fatigaPromedio) +
                    "%"

        txtBostezos.text =
            "Bostezos detectados: $bostezosTotales"

        txtOjosCerrados.text =
            "Eventos de ojos cerrados: " +
                    ojosCerradosTotales

        progressRiesgoGeneral.progress =
            calcularProgresoRiesgo(
                riesgoGeneral
            )

        progressFatigaMaxima.progress =
            fatigaMaxima.coerceIn(0, 100)

        progressFatigaPromedio.progress =
            fatigaPromedio
                .toInt()
                .coerceIn(0, 100)

        progressBostezos.progress =
            calcularProgresoEventos(
                cantidad = bostezosTotales,
                maximo = progressBostezos.max
            )

        progressOjosCerrados.progress =
            calcularProgresoEventos(
                cantidad = ojosCerradosTotales,
                maximo = progressOjosCerrados.max
            )

        txtPatron.text =
            "La ruta con mayor concentración de riesgo " +
                    "es $rutaMayorRiesgo. " +
                    "El nivel de alerta más frecuente fue " +
                    "$nivelMasFrecuente."

        txtConocimiento.text =
            conocimientoExtraido
    }

    /**
     * Muestra el estado de carga mientras se consulta el servidor.
     */
    private fun mostrarCargando() {
        txtInsightPrincipal.text =
            "Analizando datos..."

        txtDetalleInsight.text =
            "Extrayendo conocimiento desde Firebase " +
                    "con Python..."

        txtTotalRutas.text = "0"
        txtTotalAlertas.text = "0"
        txtRutaRiesgo.text = "-"
        txtNivelFrecuente.text = "-"

        txtFatigaMaxima.text =
            "Fatiga máxima: 0%"

        txtFatigaPromedio.text =
            "Fatiga promedio: 0%"

        txtBostezos.text =
            "Bostezos detectados: 0"

        txtOjosCerrados.text =
            "Eventos de ojos cerrados: 0"

        progressRiesgoGeneral.progress = 0
        progressFatigaMaxima.progress = 0
        progressFatigaPromedio.progress = 0
        progressBostezos.progress = 0
        progressOjosCerrados.progress = 0

        txtPatron.text =
            "Buscando patrones en los datos " +
                    "del conductor..."

        txtConocimiento.text =
            "Procesando información..."
    }

    /**
     * Se muestra cuando no existe un usuario en la sesión.
     */
    private fun mostrarErrorUsuario() {
        limpiarEstadisticas()

        txtInsightPrincipal.text =
            "No se encontró el usuario"

        txtDetalleInsight.text =
            "Inicia sesión nuevamente para consultar " +
                    "tus estadísticas."

        txtConocimiento.text =
            "No fue posible identificar al usuario " +
                    "logueado."
    }

    /**
     * Se muestra cuando FastAPI responde con un error.
     */
    private fun mostrarErrorServidor(
        detalle: String = "Error en el servidor",
        mensajeServidor: String? = null
    ) {
        limpiarEstadisticas()

        txtInsightPrincipal.text =
            "No se pudieron cargar estadísticas"

        txtDetalleInsight.text =
            construirMensajeError(
                detalle,
                mensajeServidor
            )

        txtConocimiento.text =
            "Endpoint esperado: " +
                    "/api/estadisticas/usuario/{usuarioId}"
    }

    /**
     * Se muestra cuando ocurre una excepción de red,
     * conversión JSON o programación.
     */
    private fun mostrarErrorExcepcion(
        exception: Exception
    ) {
        limpiarEstadisticas()

        txtInsightPrincipal.text =
            "Error al cargar estadísticas"

        txtDetalleInsight.text =
            exception.message
                ?.takeIf { it.isNotBlank() }
                ?: "No fue posible obtener la " +
                        "información del usuario."

        txtConocimiento.text =
            "Revisa la URL del servidor Python, " +
                    "que FastAPI esté ejecutándose y " +
                    "que el modelo coincida con el JSON."
    }

    /**
     * Coloca los indicadores en cero después de un error.
     */
    private fun limpiarEstadisticas() {
        txtTotalRutas.text = "0"
        txtTotalAlertas.text = "0"
        txtRutaRiesgo.text = "Sin datos"
        txtNivelFrecuente.text = "Sin datos"

        txtFatigaMaxima.text =
            "Fatiga máxima: 0%"

        txtFatigaPromedio.text =
            "Fatiga promedio: 0%"

        txtBostezos.text =
            "Bostezos detectados: 0"

        txtOjosCerrados.text =
            "Eventos de ojos cerrados: 0"

        txtPatron.text =
            "No fue posible identificar patrones."

        progressRiesgoGeneral.progress = 0
        progressFatigaMaxima.progress = 0
        progressFatigaPromedio.progress = 0
        progressBostezos.progress = 0
        progressOjosCerrados.progress = 0
    }

    /**
     * Convierte el riesgo en un porcentaje visual.
     *
     * El parámetro es nullable para evitar el error:
     * Attempt to invoke String.toLowerCase on a null object.
     */
    private fun calcularProgresoRiesgo(
        riesgo: String?
    ): Int {
        val riesgoNormalizado = riesgo
            ?.trim()
            ?.lowercase(Locale.getDefault())
            .orEmpty()

        return when (riesgoNormalizado) {
            "alto" -> 85
            "medio" -> 55
            "bajo" -> 25
            else -> 0
        }
    }

    /**
     * Ajusta el total de eventos al límite de la barra.
     */
    private fun calcularProgresoEventos(
        cantidad: Int,
        maximo: Int
    ): Int {
        return cantidad.coerceIn(0, maximo)
    }

    /**
     * Evita colocar textos nulos o vacíos en la interfaz.
     */
    private fun textoSeguro(
        valor: String?,
        valorPredeterminado: String
    ): String {
        return valor
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: valorPredeterminado
    }

    /**
     * Muestra un decimal cuando sea necesario.
     *
     * Ejemplos:
     * 20.0 -> 20
     * 20.5 -> 20.5
     */
    private fun formatearDecimal(
        valor: Double
    ): String {
        return if (valor % 1.0 == 0.0) {
            valor.toInt().toString()
        } else {
            String.format(
                Locale.getDefault(),
                "%.1f",
                valor
            )
        }
    }

    /**
     * Une el código HTTP con el mensaje devuelto por FastAPI.
     */
    private fun construirMensajeError(
        detalle: String,
        mensajeServidor: String?
    ): String {
        val servidor = mensajeServidor
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return if (servidor != null) {
            "$detalle\n$servidor"
        } else {
            detalle
        }
    }
}