package com.example.somnixapp

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.models.request.RutaRequest
import com.example.somnixapp.models.rutas.PuntoRuta
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.SessionManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.ceil
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

class SeleccionarRutaMapaActivity : AppCompatActivity() {

    private lateinit var map: GoogleMap
    private lateinit var sessionManager: SessionManager

    private lateinit var edtNombreRuta: EditText
    private lateinit var edtOrigen: EditText
    private lateinit var edtDestino: EditText
    private lateinit var contenedorSugerencias: LinearLayout
    private lateinit var placesClient: PlacesClient
    private lateinit var txtResumenRuta: TextView
    private lateinit var btnGuardarRuta: Button

    private val rutaRepository = RutaRepository()
    private val httpClient = OkHttpClient()

    private var origen: PuntoRuta? = null
    private var destino: PuntoRuta? = null
    private var seleccionandoOrigen = true

    private var origenMarker: Marker? = null
    private var destinoMarker: Marker? = null
    private var rutaPolyline: Polyline? = null

    private var distanciaKm: Double = 0.0
    private var duracionMinutos: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_seleccionar_ruta_mapa)

        sessionManager = SessionManager(this)

        inicializarPlaces()
        inicializarVistas()
        inicializarMapa()
        configurarEventos()
    }

    private fun inicializarPlaces() {
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        placesClient = Places.createClient(this)
    }

    private fun inicializarVistas() {
        edtNombreRuta = findViewById(R.id.edtNombreRuta)
        edtOrigen = findViewById(R.id.edtOrigen)
        edtDestino = findViewById(R.id.edtDestino)
        contenedorSugerencias = findViewById(R.id.contenedorSugerencias)
        txtResumenRuta = findViewById(R.id.txtResumenRuta)
        btnGuardarRuta = findViewById(R.id.btnGuardarRuta)
    }

    private fun inicializarMapa() {
        val fragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment

        fragment.getMapAsync { googleMap ->
            map = googleMap
            val mexico = LatLng(21.1223, -101.6810)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(mexico, 12f))
        }
    }

    private fun configurarEventos() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        edtOrigen.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) seleccionandoOrigen = true
        }

        edtDestino.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) seleccionandoOrigen = false
        }

        edtOrigen.addTextChangedListener(crearTextWatcher(true))
        edtDestino.addTextChangedListener(crearTextWatcher(false))

        btnGuardarRuta.setOnClickListener {
            guardarRuta()
        }
    }

    private fun pintarMarcadorOrigen(punto: PuntoRuta) {
        val latLng = LatLng(punto.lat, punto.lng)

        origenMarker?.remove()
        origenMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Origen")
                .snippet(punto.nombre)
        )

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
    }

    private fun pintarMarcadorDestino(punto: PuntoRuta) {
        val latLng = LatLng(punto.lat, punto.lng)

        destinoMarker?.remove()
        destinoMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Destino")
                .snippet(punto.nombre)
        )

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
    }

    private fun calcularYMostrarRuta() {
        val origenActual = origen
        val destinoActual = destino

        if (origenActual == null || destinoActual == null) {
            txtResumenRuta.text = "Selecciona origen y destino para calcular la ruta."
            return
        }

        lifecycleScope.launch {
            try {
                txtResumenRuta.text = "Calculando ruta con Google Maps..."

                val resultado = withContext(Dispatchers.IO) {
                    calcularRutaGoogle(origenActual, destinoActual)
                }

                distanciaKm = resultado.distanciaKm
                duracionMinutos = resultado.duracionMinutos

                pintarPolyline(resultado.polyline)

                txtResumenRuta.text =
                    "Distancia: $distanciaKm km\nTiempo estimado: $duracionMinutos min\nRuta lista para guardar."

            } catch (e: Exception) {
                txtResumenRuta.text = "No se pudo calcular la ruta."
                Toast.makeText(
                    this@SeleccionarRutaMapaActivity,
                    "Error Maps: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun calcularRutaGoogle(
        origen: PuntoRuta,
        destino: PuntoRuta
    ): ResultadoRutaGoogle {
        val apiKey = getString(R.string.google_maps_key)

        val jsonBody = JSONObject().apply {
            put("origin", JSONObject().apply {
                put("location", JSONObject().apply {
                    put("latLng", JSONObject().apply {
                        put("latitude", origen.lat)
                        put("longitude", origen.lng)
                    })
                })
            })

            put("destination", JSONObject().apply {
                put("location", JSONObject().apply {
                    put("latLng", JSONObject().apply {
                        put("latitude", destino.lat)
                        put("longitude", destino.lng)
                    })
                })
            })

            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE")
            put("computeAlternativeRoutes", false)
            put("languageCode", "es-MX")
            put("units", "METRIC")
        }

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader(
                "X-Goog-FieldMask",
                "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"
            )
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw Exception(body)
        }

        val root = JSONObject(body)
        val route = root.getJSONArray("routes").getJSONObject(0)

        val distanciaMetros = route.getDouble("distanceMeters")
        val duracionTexto = route.getString("duration").replace("s", "")
        val polyline = route
            .getJSONObject("polyline")
            .getString("encodedPolyline")

        return ResultadoRutaGoogle(
            distanciaKm = kotlin.math.round((distanciaMetros / 1000.0) * 100) / 100,
            duracionMinutos = ceil(duracionTexto.toDouble() / 60.0).toInt(),
            polyline = polyline
        )
    }

    private fun pintarPolyline(encodedPolyline: String) {
        val puntos = decodePolyline(encodedPolyline)

        rutaPolyline?.remove()
        rutaPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(puntos)
                .width(10f)
                .color(Color.parseColor("#071F3D"))
                .geodesic(true)
        )

        val bounds = LatLngBounds.Builder()
        puntos.forEach { bounds.include(it) }

        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds.build(), 120)
        )
    }

    private fun guardarRuta() {
        val nombre = edtNombreRuta.text.toString().trim()
        val origenActual = origen
        val destinoActual = destino

        if (nombre.isEmpty()) {
            Toast.makeText(this, "Ingresa el nombre de la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        if (origenActual == null) {
            Toast.makeText(this, "Selecciona el origen", Toast.LENGTH_SHORT).show()
            return
        }

        if (destinoActual == null) {
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
            origen = origenActual,
            destino = destinoActual,
            estado = "Pendiente"
        )

        lifecycleScope.launch {
            try {
                val response = rutaRepository.crearRuta(request)

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(
                        this@SeleccionarRutaMapaActivity,
                        "Ruta guardada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@SeleccionarRutaMapaActivity,
                        "No se pudo guardar la ruta",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SeleccionarRutaMapaActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0

            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(
                LatLng(
                    lat / 1E5,
                    lng / 1E5
                )
            )
        }

        return poly
    }

    private fun crearTextWatcher(esOrigen: Boolean): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""

                if (query.length < 3) {
                    contenedorSugerencias.visibility = View.GONE
                    return
                }

                seleccionandoOrigen = esOrigen
                buscarPredicciones(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        }
    }

    private fun buscarPredicciones(query: String) {
        txtResumenRuta.text = "Buscando ubicaciones: $query"

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCountries("MX")
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                contenedorSugerencias.removeAllViews()

                if (response.autocompletePredictions.isEmpty()) {
                    txtResumenRuta.text = "No se encontraron sugerencias para: $query"
                    contenedorSugerencias.visibility = View.GONE
                    return@addOnSuccessListener
                }

                response.autocompletePredictions.take(5).forEach { prediction ->
                    val item = TextView(this)
                    item.text = prediction.getFullText(null).toString()
                    item.setTextColor(Color.parseColor("#071F3D"))
                    item.textSize = 14f
                    item.setPadding(18, 18, 18, 18)

                    item.setOnClickListener {
                        obtenerDetalleLugar(
                            prediction.placeId,
                            prediction.getPrimaryText(null).toString(),
                            prediction.getFullText(null).toString()
                        )
                    }

                    contenedorSugerencias.addView(item)
                }

                txtResumenRuta.text = "Selecciona una sugerencia."
                contenedorSugerencias.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                contenedorSugerencias.visibility = View.GONE
                txtResumenRuta.text = "Error Places: ${e.message}"
                Toast.makeText(this, "Error Places: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun obtenerDetalleLugar(
        placeId: String,
        nombre: String,
        direccion: String
    ) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )

        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place

                val punto = PuntoRuta(
                    nombre = place.name ?: nombre,
                    direccion = place.address ?: direccion,
                    placeId = place.id ?: placeId,
                    lat = place.latLng?.latitude ?: 0.0,
                    lng = place.latLng?.longitude ?: 0.0
                )

                if (seleccionandoOrigen) {
                    origen = punto
                    edtOrigen.setText(punto.nombre.ifEmpty { punto.direccion })
                    edtOrigen.clearFocus()
                    pintarMarcadorOrigen(punto)
                } else {
                    destino = punto
                    edtDestino.setText(punto.nombre.ifEmpty { punto.direccion })
                    edtDestino.clearFocus()
                    pintarMarcadorDestino(punto)
                }

                contenedorSugerencias.visibility = View.GONE
                calcularYMostrarRuta()
            }
            .addOnFailureListener {
                Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
            }
    }

    data class ResultadoRutaGoogle(
        val distanciaKm: Double,
        val duracionMinutos: Int,
        val polyline: String
    )
}