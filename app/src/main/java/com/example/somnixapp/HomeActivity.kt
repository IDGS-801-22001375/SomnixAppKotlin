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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var txtRutaActual: TextView
    private lateinit var txtEstado: TextView
    private lateinit var btnEmpezarRuta: Button
    private lateinit var txtUltimaAlerta: TextView

    private val rutaRepository = RutaRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        sessionManager = SessionManager(this)
        txtRutaActual = findViewById(R.id.txtRutaActual)
        txtEstado = findViewById(R.id.txtEstado)
        btnEmpezarRuta = findViewById(R.id.btnEmpezarRuta)
        txtUltimaAlerta = findViewById(R.id.txtUltimaAlerta)

        configurarClicks()
    }

    override fun onResume() {
        super.onResume()
        cargarEstadoHome()
        cargarUltimaAlerta()
    }

    private fun cargarEstadoHome() {
        val nombreRuta = sessionManager.obtenerNombreRuta()
        val estadoViaje = sessionManager.obtenerEstadoViaje()

        txtRutaActual.text = nombreRuta ?: "Sin ruta activa"
        txtEstado.text = estadoViaje.lowercase().replaceFirstChar { it.uppercase() }
        btnEmpezarRuta.text =
            if (nombreRuta.isNullOrEmpty()) "Empezar ruta" else "Ir al monitoreo"
    }

    private fun configurarClicks() {
        findViewById<LinearLayout>(R.id.cardEstadisticas).setOnClickListener {
            startActivity(Intent(this, EstadisticasActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.cardMisRutas).setOnClickListener {
            startActivity(Intent(this, ListaRutasActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.cardCamara).setOnClickListener { abrirMonitoreo() }
        btnEmpezarRuta.setOnClickListener { abrirMonitoreo() }
        findViewById<LinearLayout>(R.id.cardAlertas).setOnClickListener {
            startActivity(Intent(this, AlertasActivity::class.java))
        }
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener { mostrarMenuMovil() }
    }

    private fun abrirMonitoreo() {
        val rutaId = sessionManager.obtenerRutaId()
        if (rutaId.isNullOrEmpty()) {
            startActivity(Intent(this, ListaRutasActivity::class.java).apply {
                putExtra("MODO", "SELECCIONAR_RUTA")
            })
        } else {
            startActivity(Intent(this, MonitoreoActivity::class.java))
        }
    }

    private fun cargarUltimaAlerta() {
        val rutaId = sessionManager.obtenerRutaId()
        if (rutaId.isNullOrEmpty()) {
            txtUltimaAlerta.text = "No hay ruta activa"
            return
        }

        lifecycleScope.launch {
            try {
                val response = rutaRepository.obtenerAlertasPorRuta(rutaId)
                val alertas = response.body()
                txtUltimaAlerta.text = if (response.isSuccessful && alertas != null) {
                    if (alertas.isEmpty()) "No hay alertas recientes"
                    else "${alertas.first().nivel.uppercase()} - ${alertas.first().mensaje}"
                } else "No se pudieron cargar las alertas"
            } catch (_: Exception) {
                txtUltimaAlerta.text = "Error al cargar alertas"
            }
        }
    }

    private fun mostrarMenuMovil() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu_home)

        val alturaMenu = (resources.displayMetrics.heightPixels * 0.5).toInt()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, alturaMenu)
            setGravity(Gravity.BOTTOM)
        }

        dialog.findViewById<TextView>(R.id.btnCerrarMenu).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.itemConfiguracion).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ConfigurarGorra::class.java))
        }
        dialog.findViewById<LinearLayout>(R.id.itemAlertasRuta).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AlertasActivity::class.java))
        }
        dialog.findViewById<LinearLayout>(R.id.itemSoporte).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:soporte@somnix.com")
                putExtra(Intent.EXTRA_SUBJECT, "Soporte SOMNIX")
            })
        }
        dialog.findViewById<LinearLayout>(R.id.itemCerrarSesion).setOnClickListener {
            dialog.dismiss()
            sessionManager.cerrarSesion()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        dialog.show()
    }
}