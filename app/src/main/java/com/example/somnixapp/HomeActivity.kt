package com.example.somnixapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.NotificacionRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var sessionManager:
            SessionManager

    private lateinit var txtRutaActual:
            TextView

    private lateinit var txtEstado:
            TextView

    private lateinit var btnEmpezarRuta:
            Button

    private lateinit var txtFatiga:
            TextView

    private lateinit var progressFatiga:
            ProgressBar

    /*
     * Se conserva el ID actual del XML para no romper
     * activity_home.xml. Su contenido ahora será una
     * notificación administrativa, no una alerta.
     */
    private lateinit var txtNotificacionAdministrativa:
            TextView

    private val notificacionRepository =
        NotificacionRepository()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        sessionManager =
            SessionManager(this)

        inicializarVistas()
        configurarClicks()
    }

    override fun onResume() {
        super.onResume()

        cargarEstadoHome()
        cargarNotificacionAdministrativa()
    }

    private fun inicializarVistas() {
        txtRutaActual =
            findViewById(R.id.txtRutaActual)

        txtEstado =
            findViewById(R.id.txtEstado)

        btnEmpezarRuta =
            findViewById(R.id.btnEmpezarRuta)

        txtFatiga =
            findViewById(R.id.txtFatiga)

        progressFatiga =
            findViewById(R.id.progressFatiga)

        /*
         * Se utiliza el ID existente hasta actualizar
         * el diseño XML.
         */
        txtNotificacionAdministrativa =
            findViewById(R.id.txtUltimaAlerta)
    }

    private fun cargarEstadoHome() {
        val rutaId =
            sessionManager.obtenerRutaId()

        val nombreRuta =
            sessionManager.obtenerNombreRuta()

        val estadoViaje =
            sessionManager.obtenerEstadoViaje()

        /*
         * La existencia de una ruta depende de su ID.
         * Las rutas nuevas pueden no tener el campo Nombre.
         */
        val hayRutaActiva =
            !rutaId.isNullOrBlank()

        val fatigaActual =
            if (hayRutaActiva) {
                sessionManager
                    .obtenerFatigaActual()
                    .coerceIn(0, 100)
            } else {
                0
            }

        txtRutaActual.text =
            if (hayRutaActiva) {
                nombreRuta
                    ?.takeIf { it.isNotBlank() }
                    ?: "Ruta seleccionada"
            } else {
                "Sin ruta activa"
            }

        txtEstado.text =
            estadoViaje
                .lowercase()
                .replaceFirstChar {
                    it.uppercase()
                }

        btnEmpezarRuta.text =
            if (hayRutaActiva) {
                "Ir al monitoreo"
            } else {
                "Empezar ruta"
            }

        txtFatiga.text =
            "Fatiga detectada: $fatigaActual%"

        progressFatiga.progress =
            fatigaActual
    }

    private fun cargarNotificacionAdministrativa() {
        val usuarioId =
            sessionManager.obtenerUsuarioId()

        if (usuarioId.isNullOrBlank()) {
            txtNotificacionAdministrativa.text =
                "Inicia sesión para consultar tus mensajes."
            return
        }

        txtNotificacionAdministrativa.text =
            "Consultando mensajes de SOMNIX..."

        lifecycleScope.launch {
            try {
                val response =
                    notificacionRepository
                        .obtenerUltimaAdministrativa(
                            usuarioId
                        )

                when {
                    response.code() == 204 -> {
                        txtNotificacionAdministrativa.text =
                            "No tienes respuestas nuevas de SOMNIX."
                    }

                    response.isSuccessful -> {
                        val notificacion =
                            response.body()

                        if (notificacion == null) {
                            txtNotificacionAdministrativa.text =
                                "No tienes respuestas nuevas de SOMNIX."
                            return@launch
                        }

                        val titulo =
                            notificacion
                                .obtenerTituloVisible()

                        val mensaje =
                            notificacion
                                .obtenerMensajeVisible()

                        txtNotificacionAdministrativa.text =
                            "$titulo\n$mensaje"
                    }

                    else -> {
                        txtNotificacionAdministrativa.text =
                            "No se pudieron consultar los mensajes."
                    }
                }

            } catch (_: Exception) {
                txtNotificacionAdministrativa.text =
                    "No fue posible conectar con SOMNIX."
            }
        }
    }

    private fun configurarClicks() {
        findViewById<LinearLayout>(
            R.id.cardEstadisticas
        ).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    EstadisticasActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.cardMisRutas
        ).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ListaRutasActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.cardCamara
        ).setOnClickListener {
            abrirMonitoreo()
        }

        btnEmpezarRuta.setOnClickListener {
            abrirMonitoreo()
        }

        /*
         * Este acceso continúa abriendo el historial
         * de alertas. Solo cambió el resumen inferior.
         */
        findViewById<LinearLayout>(
            R.id.cardAlertas
        ).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    AlertasActivity::class.java
                )
            )
        }

        findViewById<ImageView>(
            R.id.btnMenu
        ).setOnClickListener {
            mostrarMenuMovil()
        }
    }

    private fun abrirMonitoreo() {
        val rutaId =
            sessionManager.obtenerRutaId()

        if (rutaId.isNullOrBlank()) {
            startActivity(
                Intent(
                    this,
                    ListaRutasActivity::class.java
                ).apply {
                    putExtra(
                        "MODO",
                        "SELECCIONAR_RUTA"
                    )
                }
            )
        } else {
            startActivity(
                Intent(
                    this,
                    MonitoreoActivity::class.java
                )
            )
        }
    }

    private fun mostrarMenuMovil() {
        val dialog =
            Dialog(this)

        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_menu_home
        )

        val alturaMenu =
            (
                    resources
                        .displayMetrics
                        .heightPixels * 0.5
                    ).toInt()

        dialog.window?.apply {
            setBackgroundDrawable(
                ColorDrawable(
                    Color.TRANSPARENT
                )
            )

            setLayout(
                WindowManager.LayoutParams
                    .MATCH_PARENT,
                alturaMenu
            )

            setGravity(Gravity.BOTTOM)
        }

        dialog.findViewById<TextView>(
            R.id.btnCerrarMenu
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<LinearLayout>(
            R.id.itemConfiguracion
        ).setOnClickListener {
            dialog.dismiss()

            startActivity(
                Intent(
                    this,
                    ConfigurarGorra::class.java
                )
            )
        }

        dialog.findViewById<LinearLayout>(
            R.id.itemAlertasRuta
        ).setOnClickListener {
            dialog.dismiss()

            startActivity(
                Intent(
                    this,
                    AlertasActivity::class.java
                )
            )
        }

        dialog.findViewById<LinearLayout>(
            R.id.itemSoporte
        ).setOnClickListener {
            dialog.dismiss()

            startActivity(
                Intent(
                    Intent.ACTION_SENDTO
                ).apply {
                    data =
                        android.net.Uri.parse(
                            "mailto:soporte@somnix.com"
                        )

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "Soporte SOMNIX"
                    )
                }
            )
        }

        dialog.findViewById<LinearLayout>(
            R.id.itemCerrarSesion
        ).setOnClickListener {
            dialog.dismiss()

            sessionManager.cerrarSesion()

            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                ).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }

        dialog.show()
    }
}