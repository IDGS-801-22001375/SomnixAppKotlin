package com.example.somnixapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.somnixapp.ble.BleConnectionState
import com.example.somnixapp.ble.SomnixBleService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigurarGorra : AppCompatActivity() {

    private lateinit var tvGlobalStatus: TextView
    private lateinit var tabMonitor: Button
    private lateinit var tabConfig: Button
    private lateinit var tabData: Button
    private lateinit var tabTerminal: Button

    /*
     * Estos campos siguen siendo públicos porque los fragments
     * de la aplicación de prueba los consultan directamente.
     */
    var shouldBeConnected = false

    var lastJsonTelemetria = "{}"
    var lastJsonConfig = "{}"

    val logHistory =
        java.lang.StringBuilder()

    private val monitorFrag =
        MonitorFragment()

    private val configFrag =
        ConfigFragment()

    private val dataFrag =
        DataFragment()

    private val terminalFrag =
        TerminalFragment()

    private var activeFragment: Fragment =
        monitorFrag

    private var bleService:
            SomnixBleService? = null

    private var servicioVinculado = false

    private var observadoresJob: Job? = null

    private var ultimoCodigoHttp = 0

    private val permisosLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) { resultado ->
            val concedidos =
                resultado.values.all { it }

            if (concedidos) {
                iniciarEscaneoBLE()
            } else {
                shouldBeConnected = false

                actualizarEstadoSuperior(
                    texto = "Permisos Bluetooth rechazados",
                    color = "#EF4444"
                )

                Toast.makeText(
                    this,
                    "Se necesitan permisos Bluetooth para conectar la gorra.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val serviceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?
            ) {
                val localBinder =
                    binder as?
                            SomnixBleService.LocalBinder

                bleService =
                    localBinder?.obtenerServicio()

                servicioVinculado =
                    bleService != null

                agregarLog(
                    "Interfaz vinculada al servicio BLE"
                )

                observarServicioBle()

                /*
                 * Si el usuario ya había pulsado conectar
                 * mientras el servicio se vinculaba, ejecutamos
                 * la orden ahora.
                 */
                if (shouldBeConnected) {
                    bleService?.iniciarConexion()
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {
                servicioVinculado = false
                bleService = null

                observadoresJob?.cancel()
                observadoresJob = null

                actualizarEstadoSuperior(
                    texto = "Servicio BLE desconectado",
                    color = "#EF4444"
                )

                monitorFrag.onBleDisconnected()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_gorra_test
        )

        inicializarVistas()
        inicializarFragments()
        configurarTabs()
    }

    override fun onStart() {
        super.onStart()

        /*
         * Android 14/15 no permite iniciar un Foreground Service
         * connectedDevice antes de conceder los permisos Bluetooth.
         */
        if (tienePermisosBle()) {
            prepararServicioBle()
            vincularServicioBle()
        } else {
            actualizarEstadoSuperior(
                texto = "Presiona conectar para autorizar Bluetooth",
                color = "#F59E0B"
            )
        }
    }

    override fun onStop() {
        observadoresJob?.cancel()
        observadoresJob = null

        if (servicioVinculado) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
            }

            servicioVinculado = false
            bleService = null
        }

        /*
         * No desconectamos la gorra aquí.
         *
         * El servicio sigue funcionando aunque se cierre o cambie
         * la Activity. Solamente el botón Desconectar corta BLE.
         */
        super.onStop()
    }

    private fun inicializarVistas() {
        tvGlobalStatus =
            findViewById(R.id.tvGlobalStatus)

        tabMonitor =
            findViewById(R.id.tabMonitor)

        tabConfig =
            findViewById(R.id.tabConfig)

        tabData =
            findViewById(R.id.tabData)

        tabTerminal =
            findViewById(R.id.tabTerminal)
    }

    private fun inicializarFragments() {
        if (supportFragmentManager.fragments.isNotEmpty()) {
            return
        }

        supportFragmentManager
            .beginTransaction()
            .add(
                R.id.fragmentContainer,
                terminalFrag
            )
            .hide(terminalFrag)
            .add(
                R.id.fragmentContainer,
                dataFrag
            )
            .hide(dataFrag)
            .add(
                R.id.fragmentContainer,
                configFrag
            )
            .hide(configFrag)
            .add(
                R.id.fragmentContainer,
                monitorFrag
            )
            .commit()
    }

    private fun configurarTabs() {
        setTabActive(tabMonitor)

        tabMonitor.setOnClickListener {
            switchFragment(monitorFrag)
            setTabActive(tabMonitor)
        }

        tabConfig.setOnClickListener {
            switchFragment(configFrag)
            setTabActive(tabConfig)
        }

        tabData.setOnClickListener {
            switchFragment(dataFrag)
            setTabActive(tabData)
        }

        tabTerminal.setOnClickListener {
            switchFragment(terminalFrag)
            setTabActive(tabTerminal)
        }
    }

    private fun switchFragment(
        fragment: Fragment
    ) {
        if (fragment == activeFragment) {
            return
        }

        supportFragmentManager
            .beginTransaction()
            .hide(activeFragment)
            .show(fragment)
            .commit()

        activeFragment = fragment
    }

    private fun setTabActive(
        activeTab: Button
    ) {
        val inactiveColor =
            Color.parseColor("#64748B")

        tabMonitor.setTextColor(inactiveColor)
        tabConfig.setTextColor(inactiveColor)
        tabData.setTextColor(inactiveColor)
        tabTerminal.setTextColor(inactiveColor)

        activeTab.setTextColor(
            Color.parseColor("#2563EB")
        )
    }

    /**
     * Mantiene el mismo método utilizado por MonitorFragment.
     */
    fun iniciarEscaneoBLE() {
        if (!tienePermisosBle()) {
            solicitarPermisosBle()
            return
        }

        shouldBeConnected = true

        actualizarEstadoSuperior(
            texto = "Buscando gorra...",
            color = "#F59E0B"
        )

        val servicio = bleService

        if (servicio != null) {
            servicio.iniciarConexion()
            return
        }

        /*
         * Los permisos ya están concedidos, por lo que ahora sí
         * podemos crear el Foreground Service connectedDevice.
         */
        prepararServicioBle()
        vincularServicioBle()

        /*
         * Inicia el servicio con la orden de conexión.
         * Cuando termine el bind, la Activity comenzará a observarlo.
         */
        SomnixBleService.iniciar(this)
    }

    /**
     * Mantiene el mismo método utilizado por MonitorFragment.
     */
    fun desconectarBLE() {
        shouldBeConnected = false

        bleService?.detenerConexion()

        actualizarEstadoSuperior(
            texto = "Desconectado",
            color = "#EF4444"
        )

        monitorFrag.onBleDisconnected()

        agregarLog(
            "Desconexión solicitada por el usuario"
        )
    }

    /**
     * Mantiene el mismo método utilizado por todos los fragments.
     */
    fun enviarComandoBLE(
        comando: String
    ) {
        val servicio = bleService

        if (servicio == null) {
            Toast.makeText(
                this,
                "El servicio Bluetooth todavía no está disponible.",
                Toast.LENGTH_SHORT
            ).show()

            agregarLog(
                "No se pudo aceptar TX: $comando"
            )
            return
        }

        val aceptado =
            servicio.enviarComando(comando)

        if (!aceptado) {
            Toast.makeText(
                this,
                "No se pudo enviar el comando.",
                Toast.LENGTH_SHORT
            ).show()

            agregarLog(
                "Comando rechazado: $comando"
            )
        }
    }

    /**
     * Sigue siendo público para TerminalFragment.
     */
    fun agregarLog(
        mensaje: String
    ) {
        runOnUiThread {
            val hora =
                SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

            logHistory.append(
                "[$hora] $mensaje\n"
            )

            /*
             * Evita que una sesión extremadamente larga mantenga
             * miles de líneas innecesariamente.
             */
            if (logHistory.length > 50_000) {
                logHistory.delete(
                    0,
                    10_000
                )
            }

            terminalFrag.actualizarLogs(
                logHistory.toString()
            )
        }
    }

    private fun prepararServicioBle() {
        val intent =
            Intent(
                this,
                SomnixBleService::class.java
            )

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun vincularServicioBle() {
        if (servicioVinculado) {
            return
        }

        val intent =
            Intent(
                this,
                SomnixBleService::class.java
            )

        try {
            bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } catch (error: Exception) {
            agregarLog(
                "No se pudo vincular el servicio: " +
                        error.message
            )
        }
    }

    private fun observarServicioBle() {
        observadoresJob?.cancel()

        val servicio =
            bleService ?: return

        observadoresJob =
            lifecycleScope.launch {
                repeatOnLifecycle(
                    Lifecycle.State.STARTED
                ) {
                    launch {
                        servicio.estado.collect {
                            procesarEstadoBle(it)
                        }
                    }

                    launch {
                        servicio.mensajes.collect {
                            procesarMensajeBle(it)
                        }
                    }

                    launch {
                        servicio.logs.collect {
                            agregarLog(it)
                        }
                    }
                }
            }
    }

    private fun procesarEstadoBle(
        estado: BleConnectionState
    ) {
        when (estado) {
            BleConnectionState.Desconectado -> {
                actualizarEstadoSuperior(
                    texto =
                        if (shouldBeConnected) {
                            "Gorra perdida. Reconectando..."
                        } else {
                            "Desconectado"
                        },
                    color = "#EF4444"
                )

                monitorFrag.onBleDisconnected()
            }

            BleConnectionState.Buscando -> {
                actualizarEstadoSuperior(
                    texto = "Buscando gorra...",
                    color = "#F59E0B"
                )
            }

            BleConnectionState.Conectando -> {
                actualizarEstadoSuperior(
                    texto = "Conectando...",
                    color = "#F59E0B"
                )
            }

            BleConnectionState.Configurando -> {
                actualizarEstadoSuperior(
                    texto = "Configurando BLE...",
                    color = "#F59E0B"
                )
            }

            BleConnectionState.Listo -> {
                shouldBeConnected = true

                actualizarEstadoSuperior(
                    texto = "CONEXIÓN ACTIVA",
                    color = "#10B981"
                )

                monitorFrag.onBleConnected()
            }

            is BleConnectionState.Error -> {
                actualizarEstadoSuperior(
                    texto = estado.mensaje,
                    color = "#EF4444"
                )
            }
        }
    }

    private fun procesarMensajeBle(
        mensaje: String
    ) {
        try {
            val data =
                JSONObject(mensaje)

            if (
                data.optString("tipo")
                    .equals(
                        "sync",
                        ignoreCase = true
                    )
            ) {
                lastJsonConfig = mensaje

                configFrag.actualizarConfig(data)
                terminalFrag.actualizarRaw(mensaje)

                return
            }

            if (data.has("p")) {
                lastJsonTelemetria = mensaje

                monitorFrag.actualizarTelemetria(data)
                terminalFrag.actualizarRaw(mensaje)

                if (data.has("hc")) {
                    val codigoHttp =
                        data.optInt("hc", 0)

                    if (
                        codigoHttp != 0 &&
                        codigoHttp != ultimoCodigoHttp
                    ) {
                        ultimoCodigoHttp =
                            codigoHttp

                        configFrag.actualizarApiLog(
                            codigoHttp
                        )
                    }
                }
            }

        } catch (error: Exception) {
            agregarLog(
                "Mensaje no JSON: $mensaje"
            )
        }
    }

    private fun actualizarEstadoSuperior(
        texto: String,
        color: String
    ) {
        runOnUiThread {
            tvGlobalStatus.text = texto

            tvGlobalStatus.setTextColor(
                Color.parseColor(color)
            )
        }
    }

    private fun solicitarPermisosBle() {
        val permisos =
            mutableListOf<String>()

        /*
         * Se solicita siempre porque algunos equipos OPPO/Oplus
         * no entregan resultados BLE si falta ubicación.
         */
        permisos.add(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            permisos.add(
                Manifest.permission.BLUETOOTH_SCAN
            )

            permisos.add(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }

        val faltantes =
            permisos.filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (faltantes.isEmpty()) {
            iniciarEscaneoBLE()
        } else {
            permisosLauncher.launch(
                faltantes.toTypedArray()
            )
        }
    }

    private fun tienePermisosBle(): Boolean {
        val permisoUbicacion =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!permisoUbicacion) {
            return false
        }

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            val permisoScan =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

            val permisoConnect =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            permisoScan && permisoConnect
        } else {
            true
        }
    }
}