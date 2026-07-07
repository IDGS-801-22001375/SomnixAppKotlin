package com.example.somnixapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.somnixapp.adapter.AlertasAdapter
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.NotificationHelper
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch

class AlertasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alertasAdapter: AlertasAdapter

    private val rutaRepository = RutaRepository()

    private lateinit var rvAlertas: RecyclerView
    private lateinit var txtSinAlertas: TextView

    private var rutaId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_alertas)

        sessionManager = SessionManager(this)
        notificationHelper = NotificationHelper(this)

        rutaId = sessionManager.obtenerRutaId() ?: ""

        rvAlertas = findViewById(R.id.rvAlertas)
        txtSinAlertas = findViewById(R.id.txtSinAlertas)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        configurarRecycler()
        obtenerAlertas()
    }

    override fun onResume() {
        super.onResume()
        obtenerAlertas()
    }

    private fun configurarRecycler() {
        alertasAdapter = AlertasAdapter(
            alertas = emptyList(),
            onLeerClick = { alerta ->
                marcarComoLeida(alerta.id)
            }
        )

        rvAlertas.layoutManager = LinearLayoutManager(this)
        rvAlertas.adapter = alertasAdapter
    }

    private fun obtenerAlertas() {
        if (rutaId.isEmpty()) {
            txtSinAlertas.visibility = View.VISIBLE
            rvAlertas.visibility = View.GONE
            txtSinAlertas.text = "No hay una ruta activa seleccionada"
            return
        }

        lifecycleScope.launch {
            try {
                val response = rutaRepository.obtenerAlertasPorRuta(rutaId)

                if (response.isSuccessful && response.body() != null) {
                    val alertas = response.body()!!

                    alertasAdapter.actualizarLista(alertas)

                    txtSinAlertas.visibility =
                        if (alertas.isEmpty()) View.VISIBLE else View.GONE

                    rvAlertas.visibility =
                        if (alertas.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    val error = response.errorBody()?.string()
                    txtSinAlertas.visibility = View.VISIBLE
                    rvAlertas.visibility = View.GONE
                    txtSinAlertas.text = "No se pudieron cargar las alertas\n$error"
                }

            } catch (e: Exception) {
                txtSinAlertas.visibility = View.VISIBLE
                rvAlertas.visibility = View.GONE
                txtSinAlertas.text = "Error al cargar alertas: ${e.message}"
            }
        }
    }

    private fun marcarComoLeida(id: String) {
        lifecycleScope.launch {
            try {
                val response = rutaRepository.marcarAlertaComoLeida(id)

                if (response.isSuccessful) {
                    notificationHelper.mostrarNotificacion(
                        "Alerta actualizada",
                        "La alerta fue marcada como leída."
                    )
                    obtenerAlertas()
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo marcar la alerta como leída."
                    )
                }

            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "Error al actualizar la alerta."
                )
            }
        }
    }
}