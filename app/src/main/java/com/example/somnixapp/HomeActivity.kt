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
        findViewById<LinearLayout>(R.id.cardAgregarRuta).setOnClickListener {
            startActivity(Intent(this, SeleccionarRutaMapaActivity::class.java))
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
}