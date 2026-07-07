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

    private fun configurarRecyclerView() {
        rutasAdapter = RutasAdapter(
            rutas = emptyList(),
            modoSeleccionRuta = modoSeleccionRuta,
            onEditarClick = { ruta ->
                val intent = Intent(this, AgregarRutaActivity::class.java)
                intent.putExtra("MODO", "EDITAR")
                intent.putExtra("RUTA_ID", ruta.id)
                intent.putExtra("ORIGEN_NOMBRE", ruta.origen.nombre)
                intent.putExtra("ORIGEN_DIRECCION", ruta.origen.direccion)
                intent.putExtra("ORIGEN_PLACE_ID", ruta.origen.placeId)
                intent.putExtra("ORIGEN_LAT", ruta.origen.lat)
                intent.putExtra("ORIGEN_LNG", ruta.origen.lng)

                intent.putExtra("DESTINO_NOMBRE", ruta.destino.nombre)
                intent.putExtra("DESTINO_DIRECCION", ruta.destino.direccion)
                intent.putExtra("DESTINO_PLACE_ID", ruta.destino.placeId)
                intent.putExtra("DESTINO_LAT", ruta.destino.lat)
                intent.putExtra("DESTINO_LNG", ruta.destino.lng)
                startActivity(intent)
            },
            onEliminarClick = { ruta ->
                eliminarRuta(ruta.id)
            },
            onSeleccionarClick = { ruta ->
                if (modoSeleccionRuta) {
                    sessionManager.guardarRutaSeleccionada(
                        ruta.id,
                        ruta.nombre
                    )

                    Toast.makeText(
                        this,
                        "Ruta seleccionada: ${ruta.nombre}",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(Intent(this, MonitoreoActivity::class.java))
                }
            }
        )

        val rvRutas = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvRutas)
        rvRutas.layoutManager = LinearLayoutManager(this)
        rvRutas.adapter = rutasAdapter
    }

    private fun configurarBotones() {
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnNuevaRuta).setOnClickListener {
            startActivity(Intent(this, SeleccionarRutaMapaActivity::class.java))
        }
    }

    private fun obtenerRutas() {
        lifecycleScope.launch {
            try {
                val response = rutaRepository.obtenerRutas()

                if (response.isSuccessful && response.body() != null) {
                    val rutas = response.body()!!
                    rutasAdapter.actualizarLista(rutas)
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