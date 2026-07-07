package com.example.somnixapp

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.models.request.RutaRequest
import com.example.somnixapp.models.rutas.PuntoRuta
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.SessionManager
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.coroutines.launch

class AgregarRutaActivity : AppCompatActivity() {

    private val rutaRepository = RutaRepository()

    private lateinit var sessionManager: SessionManager

    private lateinit var edtNombreRuta: EditText
    private lateinit var txtOrigen: TextView
    private lateinit var txtDestino: TextView
    private lateinit var txtResumenRuta: TextView
    private lateinit var btnGuardarRuta: Button

    private var modoEditar = false
    private var rutaId: String? = null

    private var origenSeleccionado: PuntoRuta? = null
    private var destinoSeleccionado: PuntoRuta? = null
    private var seleccionandoOrigen = true

    private val autocompleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val place = Autocomplete.getPlaceFromIntent(result.data!!)

                val punto = PuntoRuta(
                    nombre = place.name ?: "",
                    direccion = place.address ?: "",
                    placeId = place.id ?: "",
                    lat = place.latLng?.latitude ?: 0.0,
                    lng = place.latLng?.longitude ?: 0.0
                )

                if (seleccionandoOrigen) {
                    origenSeleccionado = punto
                    txtOrigen.text = punto.nombre.ifEmpty { punto.direccion }
                } else {
                    destinoSeleccionado = punto
                    txtDestino.text = punto.nombre.ifEmpty { punto.direccion }
                }

                actualizarResumen()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_agregar_ruta)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        sessionManager = SessionManager(this)

        inicializarPlaces()
        inicializarVistas()
        configurarEventos()
        cargarDatosSiEsEdicion()
    }

    private fun inicializarPlaces() {
        if (!Places.isInitialized()) {
            Places.initialize(
                applicationContext,
                getString(R.string.google_maps_key)
            )
        }
    }

    private fun inicializarVistas() {
        edtNombreRuta = findViewById(R.id.edtNombreRuta)
        txtOrigen = findViewById(R.id.txtOrigen)
        txtDestino = findViewById(R.id.txtDestino)
        txtResumenRuta = findViewById(R.id.txtResumenRuta)
        btnGuardarRuta = findViewById(R.id.btnGuardarRuta)
    }

    private fun configurarEventos() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.btnSeleccionarOrigen).setOnClickListener {
            seleccionandoOrigen = true
            abrirBuscadorGooglePlaces()
        }

        findViewById<LinearLayout>(R.id.btnSeleccionarDestino).setOnClickListener {
            seleccionandoOrigen = false
            abrirBuscadorGooglePlaces()
        }

        btnGuardarRuta.setOnClickListener {
            if (modoEditar) {
                actualizarRuta()
            } else {
                guardarRuta()
            }
        }
    }

    private fun abrirBuscadorGooglePlaces() {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )

        val intent = Autocomplete.IntentBuilder(
            AutocompleteActivityMode.OVERLAY,
            fields
        ).build(this)

        autocompleteLauncher.launch(intent)
    }

    private fun actualizarResumen() {
        val origen = origenSeleccionado
        val destino = destinoSeleccionado

        txtResumenRuta.text = if (origen != null && destino != null) {
            "Ruta lista para calcularse con Google Maps.\n\nOrigen: ${origen.nombre}\nDestino: ${destino.nombre}"
        } else {
            "Selecciona origen y destino para preparar la ruta."
        }
    }

    private fun guardarRuta() {
        val nombre = edtNombreRuta.text.toString().trim()
        val origen = origenSeleccionado
        val destino = destinoSeleccionado

        if (nombre.isEmpty()) {
            Toast.makeText(this, "Ingresa el nombre de la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        if (origen == null) {
            Toast.makeText(this, "Selecciona el origen en el mapa", Toast.LENGTH_SHORT).show()
            return
        }

        if (destino == null) {
            Toast.makeText(this, "Selecciona el destino en el mapa", Toast.LENGTH_SHORT).show()
            return
        }

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

    private fun actualizarRuta() {
        val id = rutaId

        if (id == null) {
            Toast.makeText(this, "No se encontró la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val nombre = edtNombreRuta.text.toString().trim()
        val origen = origenSeleccionado
        val destino = destinoSeleccionado

        if (nombre.isEmpty()) {
            Toast.makeText(this, "Ingresa el nombre de la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        if (origen == null) {
            Toast.makeText(this, "Selecciona el origen", Toast.LENGTH_SHORT).show()
            return
        }

        if (destino == null) {
            Toast.makeText(this, "Selecciona el destino", Toast.LENGTH_SHORT).show()
            return
        }

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

    private fun cargarDatosSiEsEdicion() {
        val modo = intent.getStringExtra("MODO")

        if (modo == "EDITAR") {
            modoEditar = true
            rutaId = intent.getStringExtra("RUTA_ID")

            findViewById<TextView>(R.id.txtTitulo).text = "Editar ruta"
            btnGuardarRuta.text = "Actualizar ruta"

            edtNombreRuta.setText(intent.getStringExtra("NOMBRE"))

            val origenNombre = intent.getStringExtra("ORIGEN_NOMBRE") ?: ""
            val origenDireccion = intent.getStringExtra("ORIGEN_DIRECCION") ?: ""
            val origenPlaceId = intent.getStringExtra("ORIGEN_PLACE_ID") ?: ""
            val origenLat = intent.getDoubleExtra("ORIGEN_LAT", 0.0)
            val origenLng = intent.getDoubleExtra("ORIGEN_LNG", 0.0)

            val destinoNombre = intent.getStringExtra("DESTINO_NOMBRE") ?: ""
            val destinoDireccion = intent.getStringExtra("DESTINO_DIRECCION") ?: ""
            val destinoPlaceId = intent.getStringExtra("DESTINO_PLACE_ID") ?: ""
            val destinoLat = intent.getDoubleExtra("DESTINO_LAT", 0.0)
            val destinoLng = intent.getDoubleExtra("DESTINO_LNG", 0.0)

            origenSeleccionado = PuntoRuta(
                nombre = origenNombre,
                direccion = origenDireccion,
                placeId = origenPlaceId,
                lat = origenLat,
                lng = origenLng
            )

            destinoSeleccionado = PuntoRuta(
                nombre = destinoNombre,
                direccion = destinoDireccion,
                placeId = destinoPlaceId,
                lat = destinoLat,
                lng = destinoLng
            )

            txtOrigen.text = origenNombre.ifEmpty { origenDireccion }
            txtDestino.text = destinoNombre.ifEmpty { destinoDireccion }

            actualizarResumen()
        }
    }
}