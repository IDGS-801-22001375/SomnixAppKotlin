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

    private var marcadorOrigen: Marker? = null
    private var marcadorDestino: Marker? = null
    private var rutaPolyline: Polyline? = null

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
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment

        if (fragment == null) {
            Log.e("MAP_DEBUG", "SupportMapFragment es null")
            mostrarError("No se pudo inicializar el mapa")
            return
        }

        Log.d("MAP_DEBUG", "Fragment encontrado")

        fragment.getMapAsync { googleMap ->
            Log.d("MAP_DEBUG", "GoogleMap inicializado")

            map = googleMap
            mapaInicializado = true

            configurarMapa()

            map.setOnMapLoadedCallback {
                Log.d("MAP_DEBUG", "Mosaicos del mapa cargados")
            }

            rutaCargada?.let { ruta ->
                mostrarRutaEnMapa(ruta)
            }
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
            mostrarError("No se recibió el identificador de la ruta")
            return
        }

        obtenerRuta(id)
    }

    private fun obtenerRuta(id: String) {
        mostrarCargando(
            true,
            "Cargando información de la ruta..."
        )

        lifecycleScope.launch {
            try {
                val response = rutaRepository.obtenerRutaPorId(id)

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

                if (mapaInicializado) {
                    mostrarRutaEnMapa(ruta)
                }

            } catch (e: SocketTimeoutException) {
                mostrarError(
                    "El servidor tardó demasiado en responder"
                )

            } catch (e: Exception) {
                Log.e(
                    "VER_RUTA_MAPA",
                    "Error al obtener ruta",
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

    private fun llenarInformacionRuta(ruta: RutaResponse) {
        val origenTexto = obtenerNombrePunto(ruta.origen)
        val destinoTexto = obtenerNombrePunto(ruta.destino)

        txtNombreRutaMapa.text = ruta.nombre
        txtOrigenMapa.text = origenTexto
        txtDestinoMapa.text = destinoTexto

        txtDistanciaMapa.text =
            "${formatearDistancia(ruta.distanciaKm)} km"

        txtDuracionMapa.text =
            "${ruta.duracionMinutos} min"

        configurarEstado(ruta.estado)
    }

    private fun obtenerNombrePunto(
        punto: PuntoRuta
    ): String {
        return punto.nombre
            .takeIf { it.isNotBlank() }
            ?: punto.direccion
                .takeIf { it.isNotBlank() }
            ?: "Ubicación no disponible"
    }

    private fun configurarEstado(estado: String) {
        val terminada = estado.equals(
            "TERMINADA",
            ignoreCase = true
        )

        if (terminada) {
            txtEstadoRutaMapa.text = "Terminada"

            txtEstadoRutaMapa.setTextColor(
                Color.parseColor("#166534")
            )

            txtEstadoRutaMapa.setBackgroundResource(
                R.drawable.bg_badge_terminada
            )
        } else {
            txtEstadoRutaMapa.text = "Pendiente"

            txtEstadoRutaMapa.setTextColor(
                Color.parseColor("#7A4D00")
            )

            txtEstadoRutaMapa.setBackgroundResource(
                R.drawable.bg_badge_pendiente
            )
        }
    }

    private fun mostrarRutaEnMapa(ruta: RutaResponse) {
        val origen = ruta.origen
        val destino = ruta.destino

        if (!coordenadasValidas(origen)) {
            mostrarError(
                "La ruta no contiene coordenadas válidas de origen"
            )
            return
        }

        if (!coordenadasValidas(destino)) {
            mostrarError(
                "La ruta no contiene coordenadas válidas de destino"
            )
            return
        }

        pintarMarcadores(
            origen,
            destino
        )

        /*
         * Si tu RutaResponse incluye:
         *
         * ruta.mapa.polyline
         *
         * se utiliza la línea guardada.
         *
         * Si está vacía, se vuelve a calcular con Routes API.
         */
        val polylineGuardada = ruta.mapa?.polyline.orEmpty()

        if (polylineGuardada.isNotBlank()) {
            pintarPolyline(polylineGuardada)
            mostrarContenido()
        } else {
            calcularYMostrarRuta(
                origen,
                destino
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
                .snippet(obtenerNombrePunto(origen))
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_AZURE
                    )
                )
        )

        marcadorDestino = map.addMarker(
            MarkerOptions()
                .position(posicionDestino)
                .title("Destino")
                .snippet(obtenerNombrePunto(destino))
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
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
            true,
            "Calculando recorrido..."
        )

        lifecycleScope.launch {
            try {
                val resultado = withContext(
                    Dispatchers.IO
                ) {
                    calcularRutaGoogle(
                        origen,
                        destino
                    )
                }

                pintarPolyline(resultado.polyline)

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

                /*
                 * Aunque falle el recorrido, mostramos
                 * origen y destino en el mapa.
                 */
                ajustarCamaraEntrePuntos(
                    LatLng(origen.lat, origen.lng),
                    LatLng(destino.lat, destino.lng)
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
            put("routingPreference", "TRAFFIC_AWARE")
            put("computeAlternativeRoutes", false)
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
                        "application/json".toMediaType()
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
                val routes = root.optJSONArray("routes")

                if (routes == null || routes.length() == 0) {
                    throw Exception(
                        "Google Maps no encontró una ruta"
                    )
                }

                val route = routes.getJSONObject(0)

                val distanciaMetros =
                    route.getDouble("distanceMeters")

                val duracionSegundos =
                    route.getString("duration")
                        .removeSuffix("s")
                        .toDouble()

                val encodedPolyline =
                    route.getJSONObject("polyline")
                        .getString("encodedPolyline")

                return ResultadoRutaGoogle(
                    distanciaKm = round(
                        distanciaMetros / 10.0
                    ) / 100.0,

                    duracionMinutos = ceil(
                        duracionSegundos / 60.0
                    ).toInt(),

                    polyline = encodedPolyline
                )
            }
    }

    private fun pintarPolyline(
        encodedPolyline: String
    ) {
        val puntos = decodePolyline(
            encodedPolyline
        )

        if (puntos.isEmpty()) {
            mostrarError(
                "La ruta no contiene puntos para mostrar"
            )
            return
        }

        rutaPolyline?.remove()

        /*
         * Línea exterior clara para dar efecto tipo Uber.
         */
        map.addPolyline(
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
                CameraUpdateFactory.newLatLngZoom(
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

            /*
             * Esperamos a que el mapa tenga dimensiones
             * antes de calcular los límites.
             */
            findViewById<View>(
                R.id.mapFragment
            ).post {
                try {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            boundsBuilder.build(),
                            140
                        )
                    )
                } catch (e: Exception) {
                    Log.e(
                        "CAMERA_MAP",
                        "No se pudo ajustar cámara",
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
        val bounds = LatLngBounds.Builder()
            .include(origen)
            .include(destino)
            .build()

        findViewById<View>(
            R.id.mapFragment
        ).post {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    160
                )
            )
        }
    }

    private fun decodePolyline(
        encoded: String
    ): List<LatLng> {
        val polyline = ArrayList<LatLng>()

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
        cardInformacionRuta.visibility = View.VISIBLE
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
        return if (distancia % 1.0 == 0.0) {
            distancia.toInt().toString()
        } else {
            String.format(
                java.util.Locale.US,
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