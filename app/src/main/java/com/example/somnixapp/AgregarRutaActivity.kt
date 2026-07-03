package com.example.somnixapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.models.request.RutaRequest
import com.example.somnixapp.repository.RutaRepository
import kotlinx.coroutines.launch

class AgregarRutaActivity : AppCompatActivity() {

    private val rutaRepository = RutaRepository()
    private var modoEditar = false
    private var rutaId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_agregar_ruta)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        cargarDatosSiEsEdicion()

        findViewById<android.widget.Button>(R.id.btnGuardarRuta).setOnClickListener {
            if (modoEditar) {
                actualizarRuta()
            } else {
                guardarRuta()
            }
        }
    }

    private fun guardarRuta() {
        val nombre = findViewById<android.widget.EditText>(R.id.edtNombreRuta).text.toString().trim()
        val origen = findViewById<android.widget.EditText>(R.id.edtOrigen).text.toString().trim()
        val destino = findViewById<android.widget.EditText>(R.id.edtDestino).text.toString().trim()
        val distanciaTexto = findViewById<android.widget.EditText>(R.id.edtDistancia).text.toString().trim()
        val duracionTexto = findViewById<android.widget.EditText>(R.id.edtDuracion).text.toString().trim()

        if (nombre.isEmpty()) {
            Toast.makeText(this, "Ingresa el nombre de la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        if (origen.isEmpty()) {
            Toast.makeText(this, "Ingresa el origen", Toast.LENGTH_SHORT).show()
            return
        }

        if (destino.isEmpty()) {
            Toast.makeText(this, "Ingresa el destino", Toast.LENGTH_SHORT).show()
            return
        }

        if (distanciaTexto.isEmpty()) {
            Toast.makeText(this, "Ingresa la distancia", Toast.LENGTH_SHORT).show()
            return
        }

        if (duracionTexto.isEmpty()) {
            Toast.makeText(this, "Ingresa la duración", Toast.LENGTH_SHORT).show()
            return
        }

        val distancia = distanciaTexto.toDoubleOrNull()
        val duracion = duracionTexto.toIntOrNull()

        if (distancia == null) {
            Toast.makeText(this, "La distancia debe ser un número válido", Toast.LENGTH_SHORT).show()
            return
        }

        if (duracion == null) {
            Toast.makeText(this, "La duración debe ser un número entero", Toast.LENGTH_SHORT).show()
            return
        }

        val sessionManager = com.example.somnixapp.utils.SessionManager(this)

        val usuarioId = sessionManager.obtenerUsuarioId()

        if (usuarioId == null) {
            Toast.makeText(
                this,
                "No se encontró la sesión del usuario",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val request = RutaRequest(
            usuarioId = usuarioId ,
            nombre = nombre,
            origen = origen,
            destino = destino,
            distanciaKm = distancia,
            duracionMinutos = duracion,
            estado = "Pendiente"
        )

        lifecycleScope.launch {
            try {
                val response = rutaRepository.crearRuta(request)

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(
                        this@AgregarRutaActivity,
                        "Ruta guardada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                } else {
                    Toast.makeText(
                        this@AgregarRutaActivity,
                        "No se pudo guardar la ruta",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@AgregarRutaActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun cargarDatosSiEsEdicion() {
        val modo = intent.getStringExtra("MODO")

        if (modo == "EDITAR") {
            modoEditar = true
            rutaId = intent.getStringExtra("RUTA_ID")

            findViewById<android.widget.TextView>(R.id.txtTitulo).text = "Editar ruta"
            findViewById<android.widget.Button>(R.id.btnGuardarRuta).text = "Actualizar ruta"

            findViewById<android.widget.EditText>(R.id.edtNombreRuta)
                .setText(intent.getStringExtra("NOMBRE"))

            findViewById<android.widget.EditText>(R.id.edtOrigen)
                .setText(intent.getStringExtra("ORIGEN"))

            findViewById<android.widget.EditText>(R.id.edtDestino)
                .setText(intent.getStringExtra("DESTINO"))

            findViewById<android.widget.EditText>(R.id.edtDistancia)
                .setText(intent.getDoubleExtra("DISTANCIA", 0.0).toString())

            findViewById<android.widget.EditText>(R.id.edtDuracion)
                .setText(intent.getIntExtra("DURACION", 0).toString())
        }
    }
    private fun actualizarRuta() {
        val id = rutaId

        if (id == null) {
            Toast.makeText(this, "No se encontró la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val nombre = findViewById<android.widget.EditText>(R.id.edtNombreRuta).text.toString().trim()
        val origen = findViewById<android.widget.EditText>(R.id.edtOrigen).text.toString().trim()
        val destino = findViewById<android.widget.EditText>(R.id.edtDestino).text.toString().trim()
        val distanciaTexto = findViewById<android.widget.EditText>(R.id.edtDistancia).text.toString().trim()
        val duracionTexto = findViewById<android.widget.EditText>(R.id.edtDuracion).text.toString().trim()

        if (nombre.isEmpty() || origen.isEmpty() || destino.isEmpty() || distanciaTexto.isEmpty() || duracionTexto.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        val distancia = distanciaTexto.toDoubleOrNull()
        val duracion = duracionTexto.toIntOrNull()

        if (distancia == null || duracion == null) {
            Toast.makeText(this, "Distancia o duración inválida", Toast.LENGTH_SHORT).show()
            return
        }

        val sessionManager = com.example.somnixapp.utils.SessionManager(this)
        val usuarioId = sessionManager.obtenerUsuarioId()

        if (usuarioId == null) {
            Toast.makeText(this, "No se encontró la sesión del usuario", Toast.LENGTH_SHORT).show()
            return
        }

        val request = RutaRequest(
            usuarioId = usuarioId,
            nombre = nombre,
            origen = origen,
            destino = destino,
            distanciaKm = distancia,
            duracionMinutos = duracion,
            estado = "Pendiente"
        )

        lifecycleScope.launch {
            try {
                val response = rutaRepository.actualizarRuta(id, request)

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(
                        this@AgregarRutaActivity,
                        "Ruta actualizada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                } else {
                    Toast.makeText(
                        this@AgregarRutaActivity,
                        "No se pudo actualizar la ruta",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@AgregarRutaActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}