package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.somnixapp.adapter.RutasAdapter
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch
import android.view.View
import android.widget.TextView


class ListaRutasActivity : AppCompatActivity() {

    private lateinit var rutasAdapter: RutasAdapter
    private val rutaRepository = RutaRepository()
    private lateinit var sessionManager: SessionManager
    private var modoSeleccionRuta = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_lista_rutas)

        modoSeleccionRuta = intent.getStringExtra("MODO") == "SELECCIONAR_RUTA"

        configurarTextosPantalla()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        sessionManager = SessionManager(this)
        configurarRecyclerView()
        configurarBotones()
        obtenerRutas()
    }

    override fun onResume() {
        super.onResume()
        obtenerRutas()
    }

    private fun configurarTextosPantalla() {
        val txtTitulo = findViewById<TextView>(R.id.txtTitulo)
        val txtSubtitulo = findViewById<TextView>(R.id.txtSubtitulo)
        val txtTituloInfo = findViewById<TextView>(R.id.txtTituloInfo)
        val txtDescripcionInfo = findViewById<TextView>(R.id.txtDescripcionInfo)
        val txtSinRutas = findViewById<TextView>(R.id.txtSinRutas)

        if (modoSeleccionRuta) {
            txtTitulo.text = "Rutas pendientes"

            txtSubtitulo.text =
                "Selecciona una ruta pendiente para comenzar el monitoreo."

            txtTituloInfo.text = "Rutas por realizar"

            txtDescripcionInfo.text =
                "Aquí aparecen únicamente las rutas que todavía no han sido terminadas."

            txtSinRutas.text =
                "No tienes rutas pendientes por realizar."
        } else {
            txtTitulo.text = "Rutas realizadas"

            txtSubtitulo.text =
                "Consulta las rutas que ya fueron completadas."

            txtTituloInfo.text = "Historial de rutas"

            txtDescripcionInfo.text =
                "Aquí aparecen únicamente las rutas que ya fueron terminadas."

            txtSinRutas.text =
                "Todavía no tienes rutas realizadas."
        }
    }

    private fun configurarRecyclerView() {
        rutasAdapter = RutasAdapter(
            rutas = emptyList(),
            modoSeleccionRuta = modoSeleccionRuta,

            onGuardarRutaClick = { ruta ->
                sessionManager.guardarRutaSeleccionada(
                    ruta.id,
                    ruta.nombre
                )

                Toast.makeText(
                    this,
                    "Ruta guardada: ${ruta.nombre}",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(
                    this,
                    MonitoreoActivity::class.java
                )

                startActivity(intent)
            },

            onVerMapaClick = { ruta ->
                val intent = Intent(
                    this,
                    VerRutaMapaActivity::class.java
                )

                intent.putExtra(
                    "RUTA_ID",
                    ruta.id
                )

                startActivity(intent)
            }
        )

        val rvRutas =
            findViewById<androidx.recyclerview.widget.RecyclerView>(
                R.id.rvRutas
            )

        rvRutas.layoutManager = LinearLayoutManager(this)
        rvRutas.adapter = rutasAdapter
    }

    private fun configurarBotones() {
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        /*findViewById<android.widget.Button>(R.id.btnNuevaRuta).setOnClickListener {
            startActivity(Intent(this, SeleccionarRutaMapaActivity::class.java))
        }*/
    }

    private fun obtenerRutas() {
        lifecycleScope.launch {
            try {
                val response = rutaRepository.obtenerRutas()

                if (response.isSuccessful && response.body() != null) {
                    val todasLasRutas = response.body()!!

                    val rutasFiltradas = if (modoSeleccionRuta) {
                        todasLasRutas.filter { ruta ->
                            ruta.estado.equals(
                                "PENDIENTE",
                                ignoreCase = true
                            )
                        }
                    } else {
                        todasLasRutas.filter { ruta ->
                            ruta.estado.equals(
                                "TERMINADA",
                                ignoreCase = true
                            )
                        }
                    }

                    rutasAdapter.actualizarLista(rutasFiltradas)

                    val txtSinRutas =
                        findViewById<TextView>(R.id.txtSinRutas)

                    val rvRutas =
                        findViewById<androidx.recyclerview.widget.RecyclerView>(
                            R.id.rvRutas
                        )

                    if (rutasFiltradas.isEmpty()) {
                        txtSinRutas.visibility = View.VISIBLE
                        rvRutas.visibility = View.GONE
                    } else {
                        txtSinRutas.visibility = View.GONE
                        rvRutas.visibility = View.VISIBLE
                    }

                } else {
                    Toast.makeText(
                        this@ListaRutasActivity,
                        "No se pudieron cargar las rutas",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ListaRutasActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun eliminarRuta(id: String) {
        lifecycleScope.launch {
            try {
                val response = rutaRepository.eliminarRuta(id)

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@ListaRutasActivity,
                        "Ruta eliminada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    obtenerRutas()
                } else {
                    Toast.makeText(
                        this@ListaRutasActivity,
                        "No se pudo eliminar la ruta",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ListaRutasActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}