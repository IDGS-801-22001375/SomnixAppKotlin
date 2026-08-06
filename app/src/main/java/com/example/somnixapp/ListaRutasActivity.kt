package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.somnixapp.adapter.RutasAdapter
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.launch

class ListaRutasActivity : AppCompatActivity() {

    private lateinit var rutasAdapter: RutasAdapter
    private lateinit var sessionManager: SessionManager

    private val rutaRepository = RutaRepository()

    private var modoSeleccionRuta = false
    private var primeraCargaRealizada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_lista_rutas)

        sessionManager = SessionManager(this)

        modoSeleccionRuta =
            intent.getStringExtra("MODO") == "SELECCIONAR_RUTA"

        configurarInsets()
        configurarTextosPantalla()
        configurarRecyclerView()
        configurarBotones()
    }

    override fun onResume() {
        super.onResume()

        /*
         * La carga se hace aquí para actualizar las rutas cuando el usuario
         * regrese desde otra pantalla.
         *
         * No se llama también desde onCreate para evitar una petición doble.
         */
        obtenerRutas()
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }
    }

    private fun configurarTextosPantalla() {
        val txtTitulo = findViewById<TextView>(R.id.txtTitulo)
        val txtSubtitulo = findViewById<TextView>(R.id.txtSubtitulo)
        val txtTituloInfo = findViewById<TextView>(R.id.txtTituloInfo)
        val txtDescripcionInfo =
            findViewById<TextView>(R.id.txtDescripcionInfo)
        val txtSinRutas = findViewById<TextView>(R.id.txtSinRutas)

        if (modoSeleccionRuta) {
            txtTitulo.text = "Rutas asignadas"

            txtSubtitulo.text =
                "Selecciona una ruta asignada para comenzar el monitoreo."

            txtTituloInfo.text = "Rutas por realizar"

            txtDescripcionInfo.text =
                "Aquí aparecen las rutas que tienes asignadas y todavía no han sido terminadas."

            txtSinRutas.text =
                "No tienes rutas asignadas por realizar."
        } else {
            txtTitulo.text = "Rutas realizadas"

            txtSubtitulo.text =
                "Consulta las rutas que ya fueron completadas."

            txtTituloInfo.text = "Historial de rutas"

            txtDescripcionInfo.text =
                "Aquí aparecen las rutas asignadas que ya fueron terminadas."

            txtSinRutas.text =
                "Todavía no tienes rutas realizadas."
        }
    }

    private fun configurarRecyclerView() {
        rutasAdapter = RutasAdapter(
            rutas = emptyList(),
            modoSeleccionRuta = modoSeleccionRuta,

            onGuardarRutaClick = { ruta ->

                /*
                 * Las nuevas rutas no siempre contienen "nombre".
                 * Se genera uno usando origen y destino.
                 */
                val nombreRuta = ruta.nombre.ifBlank {
                    "${ruta.origenTexto} - ${ruta.destinoTexto}"
                }

                sessionManager.guardarRutaSeleccionada(
                    id = ruta.id,
                    nombre = nombreRuta
                )

                Toast.makeText(
                    this,
                    "Ruta seleccionada: $nombreRuta",
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
                ).apply {
                    putExtra("RUTA_ID", ruta.id)
                }

                startActivity(intent)
            }
        )

        val rvRutas = findViewById<RecyclerView>(R.id.rvRutas)

        rvRutas.layoutManager = LinearLayoutManager(this)
        rvRutas.adapter = rutasAdapter
    }

    private fun configurarBotones() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun obtenerRutas() {
        lifecycleScope.launch {
            try {
                val conductorId = sessionManager.obtenerUsuarioId()

                if (conductorId.isNullOrBlank()) {
                    mostrarListaVacia()

                    Toast.makeText(
                        this@ListaRutasActivity,
                        "No se encontró el ID del usuario en la sesión",
                        Toast.LENGTH_LONG
                    ).show()

                    return@launch
                }

                /*
                 * Se consulta por ConductorAsignadoId.
                 *
                 * Ejemplo para David:
                 * -OzJxN2RivVRGdmDrQhO
                 */
                val response =
                    rutaRepository.obtenerRutasPorConductor(
                        conductorId = conductorId
                    )

                if (response.isSuccessful) {
                    val todasLasRutas = response.body().orEmpty()

                    val rutasFiltradas = if (modoSeleccionRuta) {
                        todasLasRutas.filter { ruta ->
                            ruta.estado.equals(
                                "ASIGNADA",
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
                    actualizarVisibilidad(rutasFiltradas.isEmpty())

                    primeraCargaRealizada = true
                } else {
                    mostrarListaVacia()

                    val mensajeError = when (response.code()) {
                        401 -> "Tu sesión ha expirado"
                        403 -> "No tienes permiso para consultar las rutas"
                        404 -> "No se encontró el servicio de rutas"
                        else -> {
                            "No se pudieron cargar las rutas. Error ${response.code()}"
                        }
                    }

                    Toast.makeText(
                        this@ListaRutasActivity,
                        mensajeError,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                mostrarListaVacia()

                Toast.makeText(
                    this@ListaRutasActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun actualizarVisibilidad(listaVacia: Boolean) {
        val txtSinRutas = findViewById<TextView>(R.id.txtSinRutas)
        val rvRutas = findViewById<RecyclerView>(R.id.rvRutas)

        if (listaVacia) {
            txtSinRutas.visibility = View.VISIBLE
            rvRutas.visibility = View.GONE
        } else {
            txtSinRutas.visibility = View.GONE
            rvRutas.visibility = View.VISIBLE
        }
    }

    private fun mostrarListaVacia() {
        rutasAdapter.actualizarLista(emptyList())
        actualizarVisibilidad(listaVacia = true)
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
                        "No se pudo eliminar la ruta. Error ${response.code()}",
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