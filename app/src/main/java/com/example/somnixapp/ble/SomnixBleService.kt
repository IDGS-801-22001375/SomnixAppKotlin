package com.example.somnixapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque
import com.example.somnixapp.R

class SomnixBleService : Service() {

    inner class LocalBinder : Binder() {
        fun obtenerServicio(): SomnixBleService =
            this@SomnixBleService
    }

    private val binder = LocalBinder()

    private val handler =
        Handler(Looper.getMainLooper())

    private lateinit var bluetoothManager:
            BluetoothManager

    private var bluetoothAdapter:
            BluetoothAdapter? = null

    private lateinit var gattClient:
            SomnixGattClient

    private var escaneando = false
    private var debeEstarConectado = false
    private var intentosReconexion = 0

    private val comandosPendientes =
        ArrayDeque<String>()

    private val _estado =
        MutableStateFlow<BleConnectionState>(
            BleConnectionState.Desconectado
        )

    val estado: StateFlow<BleConnectionState> =
        _estado

    private val _telemetria =
        MutableStateFlow<GorraTelemetry?>(null)

    val telemetria: StateFlow<GorraTelemetry?> =
        _telemetria

    private val _mensajes =
        MutableSharedFlow<String>(
            extraBufferCapacity = 64
        )

    val mensajes: SharedFlow<String> =
        _mensajes

    private val _logs =
        MutableSharedFlow<String>(
            extraBufferCapacity = 128
        )

    val logs: SharedFlow<String> =
        _logs

    private val timeoutEscaneo = Runnable {
        if (!escaneando) {
            return@Runnable
        }

        agregarLog(
            "Tiempo de búsqueda BLE agotado"
        )

        detenerEscaneo()

        if (debeEstarConectado) {
            programarReconexion()
        }
    }

    private val ejecutarReconexion = Runnable {
        if (
            debeEstarConectado &&
            !gattClient.estaListo
        ) {
            iniciarEscaneoInterno()
        }
    }

    override fun onCreate() {
        super.onCreate()

        bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        bluetoothAdapter =
            bluetoothManager.adapter

        crearCanalNotificaciones()
        iniciarPrimerPlano()

        gattClient =
            SomnixGattClient(
                context = applicationContext,
                callback =
                    object : SomnixGattClient.Callback {

                        override fun onEstado(
                            estado: BleConnectionState
                        ) {
                            _estado.value = estado
                            actualizarNotificacion(estado)
                        }

                        override fun onListo() {
                            escaneando = false
                            intentosReconexion = 0

                            agregarLog(
                                "BLE listo para comandos"
                            )

                            /*
                             * Primero sincroniza y después entrega
                             * los comandos que se acumularon mientras
                             * se realizaba la conexión.
                             */
                            gattClient.enviarComando("SYNC")

                            handler.postDelayed({
                                vaciarComandosPendientes()
                            }, 300L)
                        }

                        override fun onMensaje(
                            mensaje: String
                        ) {
                            agregarLog(
                                "RX -> $mensaje"
                            )

                            _mensajes.tryEmit(mensaje)

                            GorraTelemetry
                                .desdeJson(mensaje)
                                ?.let {
                                    _telemetria.value = it
                                }
                        }

                        override fun onDesconectado(
                            status: Int
                        ) {
                            agregarLog(
                                "Gorra desconectada. Código: $status"
                            )

                            if (debeEstarConectado) {
                                programarReconexion()
                            }
                        }

                        override fun onLog(
                            mensaje: String
                        ) {
                            agregarLog(mensaje)
                        }
                    }
            )
    }

    override fun onBind(
        intent: Intent?
    ): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_CONECTAR ->
                iniciarConexion()

            ACTION_DESCONECTAR ->
                detenerConexion()
        }

        return START_NOT_STICKY
    }

    /**
     * Ordena al servicio mantener conexión con la gorra.
     */
    fun iniciarConexion() {
        debeEstarConectado = true

        if (
            gattClient.estaListo ||
            escaneando ||
            _estado.value is
                    BleConnectionState.Conectando ||
            _estado.value is
                    BleConnectionState.Configurando
        ) {
            return
        }

        intentosReconexion = 0
        iniciarEscaneoInterno()
    }

    /**
     * Desconexión solicitada explícitamente por el usuario.
     */
    fun detenerConexion() {
        debeEstarConectado = false
        intentosReconexion = 0

        handler.removeCallbacks(
            ejecutarReconexion
        )

        handler.removeCallbacks(
            timeoutEscaneo
        )

        detenerEscaneo()
        comandosPendientes.clear()

        gattClient.desconectar(
            notificar = true
        )

        _estado.value =
            BleConnectionState.Desconectado

        actualizarNotificacion(
            BleConnectionState.Desconectado
        )
    }

    /**
     * Si BLE está listo, envía el comando.
     * Si está reconectando, lo conserva hasta recuperar conexión.
     */
    fun enviarComando(
        comando: String
    ): Boolean {
        val limpio = comando.trim()

        if (limpio.isBlank()) {
            return false
        }

        if (gattClient.estaListo) {
            return gattClient.enviarComando(limpio)
        }

        /*
         * Evita duplicar SYNC o comandos idénticos consecutivos.
         */
        if (
            comandosPendientes.peekLast() != limpio
        ) {
            if (comandosPendientes.size >= 20) {
                comandosPendientes.pollFirst()
            }

            comandosPendientes.addLast(limpio)
        }

        agregarLog(
            "Comando en espera de BLE: $limpio"
        )

        if (debeEstarConectado) {
            programarReconexion()
        }

        /*
         * true significa que el servicio aceptó el comando,
         * aunque todavía esté esperando reconexión.
         */
        return true
    }

    fun estaListo(): Boolean =
        gattClient.estaListo

    private fun vaciarComandosPendientes() {
        if (!gattClient.estaListo) {
            return
        }

        while (comandosPendientes.isNotEmpty()) {
            val comando =
                comandosPendientes.pollFirst()

            gattClient.enviarComando(comando)
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneoInterno() {
        if (
            !debeEstarConectado ||
            escaneando ||
            gattClient.estaListo
        ) {
            return
        }

        if (!tienePermisoScan()) {
            _estado.value =
                BleConnectionState.Error(
                    "Falta permiso BLUETOOTH_SCAN"
                )
            return
        }

        val adapter = bluetoothAdapter

        if (adapter == null) {
            _estado.value =
                BleConnectionState.Error(
                    "Este dispositivo no tiene Bluetooth"
                )
            return
        }

        if (!adapter.isEnabled) {
            _estado.value =
                BleConnectionState.Error(
                    "Bluetooth está apagado"
                )

            programarReconexion(
                esperaPersonalizada = 5_000L
            )
            return
        }

        val scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {
            _estado.value =
                BleConnectionState.Error(
                    "No se pudo obtener el escáner BLE"
                )

            programarReconexion()
            return
        }

        handler.removeCallbacks(
            ejecutarReconexion
        )

        detenerEscaneo()

        escaneando = true
        _estado.value =
            BleConnectionState.Buscando

        agregarLog(
            "Buscando ${BleConstants.NOMBRE_GORRA}"
        )

        try {
            /*
             * Mismo escaneo utilizado por la aplicación
             * de tu compañero: sin filtros ni settings.
             */
            scanner.startScan(scanCallback)

            handler.removeCallbacks(timeoutEscaneo)

            handler.postDelayed(
                timeoutEscaneo,
                BleConstants.TIEMPO_ESCANEO_MS
            )

        } catch (error: Exception) {
            escaneando = false

            agregarLog(
                "Error al iniciar escaneo: ${error.message}"
            )

            _estado.value =
                BleConnectionState.Error(
                    "No se pudo iniciar la búsqueda BLE"
                )

            programarReconexion()
        }
    }

    private val scanCallback =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                if (!debeEstarConectado) {
                    return
                }

                val device = result.device

                val nombre =
                    result.scanRecord?.deviceName
                        ?: try {
                            device.name
                        } catch (_: Exception) {
                            null
                        }
                        ?: "Sin nombre"

                agregarLog(
                    "Detectado: $nombre | ${device.address}"
                )

                if (
                    nombre.equals(
                        BleConstants.NOMBRE_GORRA,
                        ignoreCase = true
                    )
                ) {
                    agregarLog(
                        "Gorra SOMNIX encontrada"
                    )

                    detenerEscaneo()

                    _estado.value =
                        BleConnectionState.Conectando

                    gattClient.conectar(device)
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                escaneando = false

                handler.removeCallbacks(
                    timeoutEscaneo
                )

                agregarLog(
                    "Error de escaneo BLE: $errorCode"
                )

                _estado.value =
                    BleConnectionState.Error(
                        "Error de escaneo: $errorCode"
                    )

                if (debeEstarConectado) {
                    programarReconexion()
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneo() {
        handler.removeCallbacks(
            timeoutEscaneo
        )

        if (!escaneando) {
            return
        }

        escaneando = false

        if (!tienePermisoScan()) {
            return
        }

        try {
            bluetoothAdapter
                ?.bluetoothLeScanner
                ?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    private fun programarReconexion(
        esperaPersonalizada: Long? = null
    ) {
        if (
            !debeEstarConectado ||
            gattClient.estaListo
        ) {
            return
        }

        handler.removeCallbacks(
            ejecutarReconexion
        )

        intentosReconexion++

        val espera =
            esperaPersonalizada ?: when {
                intentosReconexion <= 1 ->
                    1_000L

                intentosReconexion == 2 ->
                    2_000L

                intentosReconexion == 3 ->
                    3_000L

                else ->
                    5_000L
            }

        agregarLog(
            "Reconexión BLE en ${espera} ms"
        )

        handler.postDelayed(
            ejecutarReconexion,
            espera
        )
    }

    private fun tienePermisoScan(): Boolean {
        val permisoUbicacion =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!permisoUbicacion) {
            agregarLog(
                "Falta permiso ACCESS_FINE_LOCATION"
            )
            return false
        }

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun agregarLog(
        mensaje: String
    ) {
        _logs.tryEmit(mensaje)
        android.util.Log.d(
            "SOMNIX_BLE",
            mensaje
        )
    }

    private fun crearCanalNotificaciones() {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            val channel =
                NotificationChannel(
                    BleConstants.CANAL_NOTIFICACION_ID,
                    "Conexión con gorra SOMNIX",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        "Mantiene activa la conexión Bluetooth con la gorra"
                    setShowBadge(false)
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun construirNotificacion(
        texto: String
    ): Notification {
        return NotificationCompat.Builder(
            this,
            BleConstants.CANAL_NOTIFICACION_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SOMNIX")
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun iniciarPrimerPlano() {
        startForeground(
            BleConstants.NOTIFICACION_ID,
            construirNotificacion(
                "Servicio Bluetooth preparado"
            )
        )
    }

    private fun actualizarNotificacion(
        estado: BleConnectionState
    ) {
        val texto = when (estado) {
            BleConnectionState.Desconectado ->
                "Gorra desconectada"

            BleConnectionState.Buscando ->
                "Buscando gorra SOMNIX"

            BleConnectionState.Conectando ->
                "Conectando con la gorra"

            BleConnectionState.Configurando ->
                "Configurando comunicación BLE"

            BleConnectionState.Listo ->
                "Gorra conectada"

            is BleConnectionState.Error ->
                estado.mensaje
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            BleConstants.NOTIFICACION_ID,
            construirNotificacion(texto)
        )
    }

    override fun onDestroy() {
        debeEstarConectado = false

        handler.removeCallbacksAndMessages(null)

        detenerEscaneo()

        if (::gattClient.isInitialized) {
            gattClient.desconectar(
                notificar = false
            )
        }

        super.onDestroy()
    }

    companion object {

        const val ACTION_CONECTAR =
            "com.example.somnixapp.ble.CONECTAR"

        const val ACTION_DESCONECTAR =
            "com.example.somnixapp.ble.DESCONECTAR"

        fun iniciar(
            context: Context
        ) {
            val intent =
                Intent(
                    context,
                    SomnixBleService::class.java
                ).apply {
                    action = ACTION_CONECTAR
                }

            ContextCompat.startForegroundService(
                context,
                intent
            )
        }
    }
}