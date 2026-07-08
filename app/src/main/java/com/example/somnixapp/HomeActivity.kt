package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast

class HomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var txtRutaActual: TextView
    private lateinit var txtEstado: TextView
    private lateinit var btnEmpezarRuta: Button

    private val rutaRepository = RutaRepository()

    private lateinit var txtUltimaAlerta: TextView

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

        findViewById<LinearLayout>(R.id.cardCamara).setOnClickListener {
            abrirMonitoreo()
        }

        btnEmpezarRuta.setOnClickListener {
            abrirMonitoreo()
        }

        findViewById<LinearLayout>(R.id.cardAlertas).setOnClickListener {
            startActivity(Intent(this, AlertasActivity::class.java))
        }

        findViewById<ImageView>(R.id.btnMenu).setOnClickListener {
            mostrarMenuMovil()
        }
    }

    private fun abrirMonitoreo() {
        val rutaId = sessionManager.obtenerRutaId()

        if (rutaId.isNullOrEmpty()) {
            val intent = Intent(this, ListaRutasActivity::class.java)
            intent.putExtra("MODO", "SELECCIONAR_RUTA")
            startActivity(intent)
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

                if (response.isSuccessful && response.body() != null) {
                    val alertas = response.body()!!

                    txtUltimaAlerta.text =
                        if (alertas.isEmpty()) {
                            "No hay alertas recientes"
                        } else {
                            val alerta = alertas.first()
                            "${alerta.nivel.uppercase()} - ${alerta.mensaje}"
                        }
                } else {
                    txtUltimaAlerta.text = "No se pudieron cargar las alertas"
                }

            } catch (e: Exception) {
                txtUltimaAlerta.text = "Error al cargar alertas"
            }
        }
    }

    private fun mostrarMenuMovil() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu_home)

        // Altura del menú = mitad de la pantalla
        val alturaPantalla = resources.displayMetrics.heightPixels
        val alturaMenu = (alturaPantalla * 0.5).toInt()

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                alturaMenu
            )
            setGravity(Gravity.BOTTOM)
        }

        dialog.findViewById<TextView>(R.id.btnCerrarMenu).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<LinearLayout>(R.id.itemConfiguracion).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Configuración próximamente", Toast.LENGTH_SHORT).show()
        }

        dialog.findViewById<LinearLayout>(R.id.itemAlertasRuta).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AlertasActivity::class.java))
        }

        dialog.findViewById<LinearLayout>(R.id.itemSoporte).setOnClickListener {
            dialog.dismiss()

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:soporte@somnix.com")
                putExtra(Intent.EXTRA_SUBJECT, "Soporte SOMNIX")
            }

            startActivity(intent)
        }

        dialog.findViewById<LinearLayout>(R.id.itemCerrarSesion).setOnClickListener {
            dialog.dismiss()

            sessionManager.cerrarSesion()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        dialog.show()
    }
}