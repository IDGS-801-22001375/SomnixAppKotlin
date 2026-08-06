package com.example.somnixapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.models.response.RutaResponse
import com.example.somnixapp.models.rutas.PuntoRuta
import com.example.somnixapp.repository.RutaRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.round

class VerRutaMapaActivity : AppCompatActivity() {

    private lateinit var map: GoogleMap

    private lateinit var contenedorCargandoMapa: LinearLayout
    private lateinit var cardInformacionRuta: LinearLayout

    private lateinit var txtCargandoMapa: TextView
    private lateinit var txtNombreRutaMapa: TextView
    private lateinit var txtEstadoRutaMapa: TextView
    private lateinit var txtOrigenMapa: TextView
    private lateinit var txtDestinoMapa: TextView
    private lateinit var txtDistanciaMapa: TextView
    private lateinit var txtDuracionMapa: TextView

    private val rutaRepository = RutaRepository()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var rutaId: String? = null
    private var mapaInicializado = false
    private var rutaCargada: RutaResponse? = null
    private var rutaMostrada = false

    private var marcadorOrigen: Marker? = null
    private var marcadorDestino: Marker? = null
    private var rutaPolyline: Polyline? = null
    private var bordePolyline: Polyline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_ver_ruta_mapa)

        rutaId = intent.getStringExtra("RUTA_ID")

        configurarInsets()
        inicializarVistas()
        configurarEventos()
        inicializarMapa()
        validarYCargarRuta()
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

    private fun inicializarVistas() {
        contenedorCargandoMapa =
            findViewById(R.id.contenedorCargandoMapa)

        cardInformacionRuta =
            findViewById(R.id.cardInformacionRuta)

        txtCargandoMapa =
            findViewById(R.id.txtCargandoMapa)

        txtNombreRutaMapa =
            findViewById(R.id.txtNombreRutaMapa)

        txtEstadoRutaMapa =
            findViewById(R.id.txtEstadoRutaMapa)

        txtOrigenMapa =
            findViewById(R.id.txtOrigenMapa)

        txtDestinoMapa =
            findViewById(R.id.txtDestinoMapa)

        txtDistanciaMapa =
            findViewById(R.id.txtDistanciaMapa)

        txtDuracionMapa =
            findViewById(R.id.txtDuracionMapa)

        cardInformacionRuta.visibility = View.GONE
    }

    private fun configurarEventos() {
        findViewById<ImageView>(
            R.id.btnCerrarMapa
        ).setOnClickListener {
            finish()
        }
    }

    private fun inicializarMapa() {
        val fragment = supportFragmentManager
            .findFragmentById(
                R.id.mapFragment
            ) as? SupportMapFragment

        if (fragment == null) {
            mostrarError(
                "No se pudo inicializar el mapa"
            )
            return
        }

        fragment.getMapAsync { googleMap ->
            map = googleMap
            mapaInicializado = true

            configurarMapa()
            intentarMostrarRuta()
        }
    }

    private fun configurarMapa() {
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        map.setPadding(
            0,
            100,
            0,
            480
        )

        val leon = LatLng(
            21.1223,
            -101.6810
        )

        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                leon,
                12f
            )
        )
    }

    private fun validarYCargarRuta() {
        val id = rutaId

        if (id.isNullOrBlank()) {
            mostrarError(
                "No se recibió el identificador de la ruta"
            )
            return
        }

        obtenerRuta(id)
    }

    private fun obtenerRuta(id: String) {
        mostrarCargando(
            mostrar = true,
            mensaje = "Cargando información de la ruta..."
        )

        lifecycleScope.launch {
            try {
                val response =
                    rutaRepository.obtenerRutaPorId(id)

                if (!response.isSuccessful) {
                    mostrarError(
                        "No se pudo cargar la ruta. Código ${response.code()}"
                    )
                    return@launch
                }

                val ruta = response.body()

                if (ruta == null) {
                    mostrarError(
                        "El servidor respondió sin información de la ruta"
                    )
                    return@launch
                }

                rutaCargada = ruta
                llenarInformacionRuta(ruta)
                intentarMostrarRuta()

            } catch (e: SocketTimeoutException) {
                mostrarError(
                    "El servidor tardó demasiado en responder"
                )

            } catch (e: Exception) {
                Log.e(
                    "VER_RUTA_MAPA",
                    "Error al obtener la ruta",
                    e
                )

                mostrarError(
                    "Error de conexión: ${
                        e.localizedMessage ?: "desconocido"
                    }"
                )
            }
        }
    }

    private fun intentarMostrarRuta() {
        val ruta = rutaCargada

        if (
            ruta == null ||
            !mapaInicializado ||
            rutaMostrada
        ) {
            return
        }

        rutaMostrada = true
        mostrarRutaEnMapa(ruta)
    }

    private fun llenarInformacionRuta(
        ruta: RutaResponse
    ) {
        val origenTexto = ruta.origenTexto
            .takeIf { it.isNotBlank() }
            ?: obtenerNombrePunto(ruta.origen)

        val destinoTexto = ruta.destinoTexto
            .takeIf { it.isNotBlank() }
            ?: obtenerNombrePunto(ruta.destino)

        val nombreRuta = ruta.nombre
            .takeIf { it.isNotBlank() }
            ?: "$origenTexto - $destinoTexto"

        txtNombreRutaMapa.text = nombreRuta
        txtOrigenMapa.text = origenTexto
        txtDestinoMapa.text = destinoTexto

        txtDistanciaMapa.text =
            "${formatearDistancia(ruta.distanciaKm)} km"

        txtDuracionMapa.text =
            "${ruta.duracionMinutos} min"

        configurarEstado(ruta.estado)

        cardInformacionRuta.visibility = View.VISIBLE
    }

    private fun obtenerNombrePunto(
        punto: PuntoRuta?
    ): String {
        if (punto == null) {
            return "Ubicación no disponible"
        }

        return punto.nombre
            .takeIf { it.isNotBlank() }
            ?: punto.direccion
                .takeIf { it.isNotBlank() }
            ?: "Ubicación no disponible"
    }

    private fun configurarEstado(
        estado: String
    ) {
        when {
            estado.equals(
                "TERMINADA",
                ignoreCase = true
            ) -> {
                txtEstadoRutaMapa.text = "Terminada"

                txtEstadoRutaMapa.setTextColor(
                    Color.parseColor("#166534")
                )

                txtEstadoRutaMapa.setBackgroundResource(
                    R.drawable.bg_badge_terminada
                )
            }

            estado.equals(
                "ASIGNADA",
                ignoreCase = true
            ) -> {
                txtEstadoRutaMapa.text = "Asignada"

                txtEstadoRutaMapa.setTextColor(
                    Color.parseColor("#7A4D00")
                )

                txtEstadoRutaMapa.setBackgroundResource(
                    R.drawable.bg_badge_pendiente
                )
            }

            else -> {
                txtEstadoRutaMapa.text =
                    estado.ifBlank {
                        "Pendiente"
                    }

                txtEstadoRutaMapa.setTextColor(
                    Color.parseColor("#7A4D00")
                )

                txtEstadoRutaMapa.setBackgroundResource(
                    R.drawable.bg_badge_pendiente
                )
            }
        }
    }

    private fun mostrarRutaEnMapa(
        ruta: RutaResponse
    ) {
        lifecycleScope.launch {
            try {
                mostrarCargando(
                    mostrar = true,
                    mensaje = "Preparando recorrido..."
                )

                val origen = obtenerPuntoRuta(
                    puntoGuardado = ruta.origen,
                    direccion = ruta.origenTexto,
                    nombrePredeterminado = "Origen"
                )

                val destino = obtenerPuntoRuta(
                    puntoGuardado = ruta.destino,
                    direccion = ruta.destinoTexto,
                    nombrePredeterminado = "Destino"
                )

                if (origen == null) {
                    mostrarError(
                        "No se pudo localizar el origen: ${ruta.origenTexto}"
                    )
                    return@launch
                }

                if (destino == null) {
                    mostrarError(
                        "No se pudo localizar el destino: ${ruta.destinoTexto}"
                    )
                    return@launch
                }

                pintarMarcadores(
                    origen = origen,
                    destino = destino
                )

                val polylineGuardada =
                    ruta.mapa?.polyline.orEmpty()

                if (polylineGuardada.isNotBlank()) {
                    pintarPolyline(
                        polylineGuardada
                    )

                    mostrarContenido()
                } else {
                    calcularYMostrarRuta(
                        origen = origen,
                        destino = destino
                    )
                }

            } catch (e: Exception) {
                Log.e(
                    "VER_RUTA_MAPA",
                    "Error preparando la ruta",
                    e
                )

                mostrarError(
                    "No se pudo preparar el mapa: ${
                        e.localizedMessage
                            ?: "error desconocido"
                    }"
                )
            }
        }
    }

    private suspend fun obtenerPuntoRuta(
        puntoGuardado: PuntoRuta?,
        direccion: String,
        nombrePredeterminado: String
    ): PuntoRuta? {
        if (
            puntoGuardado != null &&
            coordenadasValidas(puntoGuardado)
        ) {
            return puntoGuardado
        }

        if (direccion.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            geocodificarDireccion(
                direccion = direccion,
                nombrePredeterminado =
                    nombrePredeterminado
            )
        }
    }

    private fun geocodificarDireccion(
        direccion: String,
        nombrePredeterminado: String
    ): PuntoRuta? {
        val apiKey = getString(
            R.string.google_maps_key
        )

        if (apiKey.isBlank()) {
            throw Exception(
                "No se encontró la clave de Google Maps"
            )
        }

        val direccionCodificada =
            URLEncoder.encode(
                direccion,
                Charsets.UTF_8.name()
            )

        val url =
            "https://maps.googleapis.com/maps/api/geocode/json" +
                    "?address=$direccionCodificada" +
                    "&region=mx" +
                    "&language=es" +
                    "&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request)
            .execute()
            .use { response ->

                val body =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw Exception(
                        "Geocoding API ${response.code}: $body"
                    )
                }

                val root = JSONObject(body)
                val status =
                    root.optString("status")

                val resultados =
                    root.optJSONArray("results")

                if (
                    status != "OK" ||
                    resultados == null ||
                    resultados.length() == 0
                ) {
                    Log.e(
                        "GEOCODING",
                        "No se encontró '$direccion'. Estado: $status"
                    )

                    return null
                }

                val resultado =
                    resultados.getJSONObject(0)

                val ubicacion = resultado
                    .getJSONObject("geometry")
                    .getJSONObject("location")

                return PuntoRuta(
                    nombre = nombrePredeterminado,
                    direccion =
                        resultado.optString(
                            "formatted_address",
                            direccion
                        ),
                    placeId =
                        resultado.optString(
                            "place_id"
                        ),
                    lat =
                        ubicacion.getDouble("lat"),
                    lng =
                        ubicacion.getDouble("lng")
                )
            }
    }

    private fun coordenadasValidas(
        punto: PuntoRuta
    ): Boolean {
        return punto.lat in -90.0..90.0 &&
                punto.lng in -180.0..180.0 &&
                !(punto.lat == 0.0 && punto.lng == 0.0)
    }

    private fun pintarMarcadores(
        origen: PuntoRuta,
        destino: PuntoRuta
    ) {
        marcadorOrigen?.remove()
        marcadorDestino?.remove()

        val posicionOrigen = LatLng(
            origen.lat,
            origen.lng
        )

        val posicionDestino = LatLng(
            destino.lat,
            destino.lng
        )

        marcadorOrigen = map.addMarker(
            MarkerOptions()
                .position(posicionOrigen)
                .title("Origen")
                .snippet(
                    obtenerNombrePunto(origen)
                )
                .icon(
                    BitmapDescriptorFactory
                        .defaultMarker(
                            BitmapDescriptorFactory.HUE_AZURE
                        )
                )
        )

        marcadorDestino = map.addMarker(
            MarkerOptions()
                .position(posicionDestino)
                .title("Destino")
                .snippet(
                    obtenerNombrePunto(destino)
                )
                .icon(
                    BitmapDescriptorFactory
                        .defaultMarker(
                            BitmapDescriptorFactory.HUE_GREEN
                        )
                )
        )
    }

    private fun calcularYMostrarRuta(
        origen: PuntoRuta,
        destino: PuntoRuta
    ) {
        mostrarCargando(
            mostrar = true,
            mensaje = "Calculando recorrido..."
        )

        lifecycleScope.launch {
            try {
                val resultado = withContext(
                    Dispatchers.IO
                ) {
                    calcularRutaGoogle(
                        origen = origen,
                        destino = destino
                    )
                }

                pintarPolyline(
                    resultado.polyline
                )

                txtDistanciaMapa.text =
                    "${formatearDistancia(resultado.distanciaKm)} km"

                txtDuracionMapa.text =
                    "${resultado.duracionMinutos} min"

                mostrarContenido()

            } catch (e: Exception) {
                Log.e(
                    "GOOGLE_ROUTES",
                    "Error calculando recorrido",
                    e
                )

                ajustarCamaraEntrePuntos(
                    origen = LatLng(
                        origen.lat,
                        origen.lng
                    ),
                    destino = LatLng(
                        destino.lat,
                        destino.lng
                    )
                )

                mostrarContenido()

                Toast.makeText(
                    this@VerRutaMapaActivity,
                    "No se pudo dibujar el recorrido completo",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun calcularRutaGoogle(
        origen: PuntoRuta,
        destino: PuntoRuta
    ): ResultadoRutaGoogle {
        val apiKey = getString(
            R.string.google_maps_key
        )

        if (apiKey.isBlank()) {
            throw Exception(
                "No se encontró la clave de Google Maps"
            )
        }

        val jsonBody = JSONObject().apply {
            put(
                "origin",
                JSONObject().apply {
                    put(
                        "location",
                        JSONObject().apply {
                            put(
                                "latLng",
                                JSONObject().apply {
                                    put(
                                        "latitude",
                                        origen.lat
                                    )

                                    put(
                                        "longitude",
                                        origen.lng
                                    )
                                }
                            )
                        }
                    )
                }
            )

            put(
                "destination",
                JSONObject().apply {
                    put(
                        "location",
                        JSONObject().apply {
                            put(
                                "latLng",
                                JSONObject().apply {
                                    put(
                                        "latitude",
                                        destino.lat
                                    )

                                    put(
                                        "longitude",
                                        destino.lng
                                    )
                                }
                            )
                        }
                    )
                }
            )

            put("travelMode", "DRIVE")
            put(
                "routingPreference",
                "TRAFFIC_AWARE"
            )
            put(
                "computeAlternativeRoutes",
                false
            )
            put("languageCode", "es-MX")
            put("units", "METRIC")
        }

        val request = Request.Builder()
            .url(
                "https://routes.googleapis.com/directions/v2:computeRoutes"
            )
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .addHeader(
                "X-Goog-Api-Key",
                apiKey
            )
            .addHeader(
                "X-Goog-FieldMask",
                "routes.duration," +
                        "routes.distanceMeters," +
                        "routes.polyline.encodedPolyline"
            )
            .post(
                jsonBody
                    .toString()
                    .toRequestBody(
                        "application/json"
                            .toMediaType()
                    )
            )
            .build()

        httpClient.newCall(request)
            .execute()
            .use { response ->

                val body =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw Exception(
                        "Routes API ${response.code}: $body"
                    )
                }

                val root = JSONObject(body)

                val routes =
                    root.optJSONArray("routes")

                if (
                    routes == null ||
                    routes.length() == 0
                ) {
                    throw Exception(
                        "Google Maps no encontró una ruta"
                    )
                }

                val route =
                    routes.getJSONObject(0)

                val distanciaMetros =
                    route.getDouble(
                        "distanceMeters"
                    )

                val duracionSegundos =
                    route.getString("duration")
                        .removeSuffix("s")
                        .toDouble()

                val encodedPolyline =
                    route.getJSONObject(
                        "polyline"
                    ).getString(
                        "encodedPolyline"
                    )

                return ResultadoRutaGoogle(
                    distanciaKm =
                        round(
                            distanciaMetros / 10.0
                        ) / 100.0,

                    duracionMinutos =
                        ceil(
                            duracionSegundos / 60.0
                        ).toInt(),

                    polyline = encodedPolyline
                )
            }
    }

    private fun pintarPolyline(
        encodedPolyline: String
    ) {
        val puntos =
            decodePolyline(encodedPolyline)

        if (puntos.isEmpty()) {
            mostrarError(
                "La ruta no contiene puntos para mostrar"
            )
            return
        }

        rutaPolyline?.remove()
        bordePolyline?.remove()

        bordePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(puntos)
                .width(18f)
                .color(Color.WHITE)
                .geodesic(true)
                .zIndex(1f)
        )

        rutaPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(puntos)
                .width(11f)
                .color(
                    Color.parseColor("#071F3D")
                )
                .geodesic(true)
                .zIndex(2f)
        )

        ajustarCamaraAPolyline(puntos)
    }

    private fun ajustarCamaraAPolyline(
        puntos: List<LatLng>
    ) {
        if (puntos.isEmpty()) {
            return
        }

        if (puntos.size == 1) {
            map.animateCamera(
                CameraUpdateFactory
                    .newLatLngZoom(
                        puntos.first(),
                        16f
                    )
            )

            return
        }

        try {
            val boundsBuilder =
                LatLngBounds.Builder()

            puntos.forEach { punto ->
                boundsBuilder.include(punto)
            }

            findViewById<View>(
                R.id.mapFragment
            ).post {
                try {
                    map.animateCamera(
                        CameraUpdateFactory
                            .newLatLngBounds(
                                boundsBuilder.build(),
                                140
                            )
                    )
                } catch (e: Exception) {
                    Log.e(
                        "CAMERA_MAP",
                        "No se pudo ajustar la cámara",
                        e
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(
                "CAMERA_BOUNDS",
                "Error construyendo límites",
                e
            )
        }
    }

    private fun ajustarCamaraEntrePuntos(
        origen: LatLng,
        destino: LatLng
    ) {
        try {
            val bounds = LatLngBounds.Builder()
                .include(origen)
                .include(destino)
                .build()

            findViewById<View>(
                R.id.mapFragment
            ).post {
                try {
                    map.animateCamera(
                        CameraUpdateFactory
                            .newLatLngBounds(
                                bounds,
                                160
                            )
                    )
                } catch (e: Exception) {
                    Log.e(
                        "CAMERA_POINTS",
                        "No se pudo ajustar la cámara",
                        e
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                "CAMERA_POINTS",
                "Error calculando límites",
                e
            )
        }
    }

    private fun decodePolyline(
        encoded: String
    ): List<LatLng> {
        val polyline =
            ArrayList<LatLng>()

        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            var byteValue: Int
            var shift = 0
            var result = 0

            do {
                if (index >= encoded.length) {
                    return polyline
                }

                byteValue =
                    encoded[index++].code - 63

                result = result or (
                        (byteValue and 0x1F) shl shift
                        )

                shift += 5

            } while (byteValue >= 0x20)

            val latitudeDifference =
                if ((result and 1) != 0) {
                    (result shr 1).inv()
                } else {
                    result shr 1
                }

            latitude += latitudeDifference

            shift = 0
            result = 0

            do {
                if (index >= encoded.length) {
                    return polyline
                }

                byteValue =
                    encoded[index++].code - 63

                result = result or (
                        (byteValue and 0x1F) shl shift
                        )

                shift += 5

            } while (byteValue >= 0x20)

            val longitudeDifference =
                if ((result and 1) != 0) {
                    (result shr 1).inv()
                } else {
                    result shr 1
                }

            longitude += longitudeDifference

            polyline.add(
                LatLng(
                    latitude / 1E5,
                    longitude / 1E5
                )
            )
        }

        return polyline
    }

    private fun mostrarCargando(
        mostrar: Boolean,
        mensaje: String = "Cargando..."
    ) {
        txtCargandoMapa.text = mensaje

        contenedorCargandoMapa.visibility =
            if (mostrar) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun mostrarContenido() {
        mostrarCargando(false)
        cardInformacionRuta.visibility =
            View.VISIBLE
    }

    private fun mostrarError(
        mensaje: String
    ) {
        mostrarCargando(false)

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun formatearDistancia(
        distancia: Double
    ): String {
        return if (
            distancia % 1.0 == 0.0
        ) {
            distancia.toInt().toString()
        } else {
            String.format(
                Locale.US,
                "%.1f",
                distancia
            )
        }
    }

    data class ResultadoRutaGoogle(
        val distanciaKm: Double,
        val duracionMinutos: Int,
        val polyline: String
    )
}