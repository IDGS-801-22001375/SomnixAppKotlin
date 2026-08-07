package com.example.somnixapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.PythonControlRepository
import com.example.somnixapp.repository.PythonFrameRepository
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.NotificationHelper
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.somnixapp.ble.BleConnectionState
import com.example.somnixapp.ble.SomnixBleService
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class MonitoreoActivity : AppCompatActivity() {

    private enum class EstadoViaje {
        INACTIVO,
        ACTIVO,
        PAUSADO
    }

    private var estadoViaje = EstadoViaje.INACTIVO
    private var monitoreoActivo = false
    private var operacionViajeEnProceso = false

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var sessionManager: SessionManager

    private var shouldBeConnected = false
    private var gorraConectada = false
    private var nivelAlertaGorraAnterior = 0

    private var bleService: SomnixBleService? = null
    private var servicioBleVinculado = false
    private var observadoresBleJob: Job? = null

    /*
     * Este Handler solamente separa comandos como
     * CALIBRAR -> VIAJE_INICIAR.
     *
     * Ya no administra reconexiones; eso lo hace el servicio.
     */
    private val handlerComandosGorra =
        Handler(Looper.getMainLooper())

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

                servicioBleVinculado =
                    bleService != null

                observarServicioBle()

                if (shouldBeConnected) {
                    bleService?.iniciarConexion()
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {
                observadoresBleJob?.cancel()
                observadoresBleJob = null

                bleService = null
                servicioBleVinculado = false
                gorraConectada = false

                actualizarIndicadorGorra(false)
            }
        }

    private val controlRepository = PythonControlRepository()
    private val frameRepository = PythonFrameRepository()
    private val rutaRepository = RutaRepository()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var frameLoopJob: Job? = null
    private var frameRequestJob: Job? = null

    @Volatile
    private var enviandoFrame = false

    private lateinit var usuarioId: String
    private lateinit var rutaId: String
    private lateinit var nombreRuta: String

    // Vistas

    private lateinit var btnBack: ImageView
    private lateinit var btnConfigurar: Button
    private lateinit var btnIniciarViaje: Button
    private lateinit var btnPausarViaje: Button
    private lateinit var btnReanudarViaje: Button
    private lateinit var btnTerminarViaje: Button
    private lateinit var btnTerminarViajePausado: Button
    private lateinit var btnApagarAlarma: Button
    private lateinit var btnApagarAlarmaPausado: Button

    private lateinit var contenedorAccionesIniciales: LinearLayout
    private lateinit var contenedorAccionesActivo: LinearLayout
    private lateinit var contenedorAccionesPausado: LinearLayout

    private lateinit var previewCamara: PreviewView

    private lateinit var txtEstadoMonitoreo: TextView
    private lateinit var txtPorcentajeFatiga: TextView
    private lateinit var txtEstadoConductor: TextView
    private lateinit var txtNivelAlerta: TextView
    private lateinit var txtRutaMonitoreo: TextView
    private lateinit var txtUltimasAlertas: TextView
    private lateinit var txtConexionGorra: TextView

    // Popup y alarma local

    private var dialogoNecesidad: AlertDialog? = null
    private var popupNecesidadVisible = false
    private var txtFatigaPopup: TextView? = null

    private var alarmaCelularActiva = false
    private var ringtoneAlarma: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var ultimoPorcentajeFatiga = 0
    private var alarmaFatiga5Enviada = false

    // Permisos

    private val permisosBleLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) { permisos ->

            val concedidos =
                permisos.values.all { it }

            if (concedidos) {
                conectarGorra()
            } else {
                shouldBeConnected = false

                notificationHelper.mostrarNotificacion(
                    "Permisos BLE",
                    "Autoriza dispositivos cercanos y ubicación para conectar la gorra."
                )

                actualizarIndicadorGorra(false)
            }
        }

    private val permisoCamaraLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permitido ->

            if (permitido) {
                iniciarCamara()
            } else {
                notificationHelper.mostrarNotificacion(
                    "Permiso requerido",
                    "Activa el permiso de cámara para usar el monitoreo."
                )
            }
        }

    private val permisoNotificacionesLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permitido ->

            if (permitido) {
                notificationHelper.mostrarNotificacion(
                    "SOMNIX",
                    "Notificaciones activadas correctamente."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_monitoreo)

        sessionManager = SessionManager(this)
        notificationHelper = NotificationHelper(this)

        usuarioId = sessionManager.obtenerUsuarioId().orEmpty()
        rutaId = sessionManager.obtenerRutaId().orEmpty()
        nombreRuta = sessionManager.obtenerNombreRuta().orEmpty()
        ultimoPorcentajeFatiga =
            sessionManager.obtenerFatigaActual()

        if (usuarioId.isBlank()) {
            notificationHelper.mostrarNotificacion(
                "Sesión no encontrada",
                "No hay una sesión iniciada."
            )

            finish()
            return
        }

        if (rutaId.isBlank()) {
            notificationHelper.mostrarNotificacion(
                "Ruta requerida",
                "Selecciona una ruta antes de iniciar el monitoreo."
            )

            finish()
            return
        }

        estadoViaje = when (
            sessionManager.obtenerEstadoViaje().uppercase()
        ) {
            "ACTIVO" -> EstadoViaje.ACTIVO
            "PAUSADO" -> EstadoViaje.PAUSADO
            else -> EstadoViaje.INACTIVO
        }

        monitoreoActivo =
            estadoViaje == EstadoViaje.ACTIVO

        cameraExecutor = Executors.newSingleThreadExecutor()

        inicializarVistas()
        txtPorcentajeFatiga.text =
            "Fatiga: $ultimoPorcentajeFatiga%"
        inicializarAlarmaCelular()

        configurarClicks()
        configurarBack()
        actualizarUIEstado()

        obtenerAlertasRuta()
    }

    override fun onStart() {
        super.onStart()

        /*
         * Si ya existen permisos, vinculamos la Activity con
         * el servicio. Si ConfigurarGorra ya conectó la gorra,
         * aquí recuperaremos esa misma conexión.
         */
        if (tienePermisosBleCompletos()) {
            prepararServicioBle()
            vincularServicioBle()
        }
    }

    override fun onStop() {
        observadoresBleJob?.cancel()
        observadoresBleJob = null

        if (servicioBleVinculado) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
            }

            servicioBleVinculado = false
            bleService = null
        }

        /*
         * No desconectamos la gorra.
         * El servicio conserva la conexión al cambiar de pantalla.
         */
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        validarPermisosIniciales()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        detenerAlarmaCelular()

        dialogoNecesidad?.dismiss()
        dialogoNecesidad = null
        txtFatigaPopup = null
        popupNecesidadVisible = false

        monitoreoActivo = false
        detenerEnvioFrames()

        handlerComandosGorra.removeCallbacksAndMessages(null)

        observadoresBleJob?.cancel()
        observadoresBleJob = null

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        /*
         * No desconectamos BLE aquí.
         * SomnixBleService es el único propietario de GATT.
         */

        super.onDestroy()
    }

    // =========================================================
    // VISTAS
    // =========================================================

    private fun inicializarVistas() {
        btnBack = findViewById(R.id.btnBack)
        btnConfigurar = findViewById(R.id.btnConfigurar)
        btnIniciarViaje = findViewById(R.id.btnIniciarViaje)
        btnPausarViaje = findViewById(R.id.btnPausarViaje)
        btnReanudarViaje = findViewById(R.id.btnReanudarViaje)
        btnTerminarViaje = findViewById(R.id.btnTerminarViaje)

        btnTerminarViajePausado =
            findViewById(R.id.btnTerminarViajePausado)

        btnApagarAlarma = findViewById(R.id.btnApagarAlarma)

        btnApagarAlarmaPausado =
            findViewById(R.id.btnApagarAlarmaPausado)

        contenedorAccionesIniciales =
            findViewById(R.id.contenedorAccionesIniciales)

        contenedorAccionesActivo =
            findViewById(R.id.contenedorAccionesActivo)

        contenedorAccionesPausado =
            findViewById(R.id.contenedorAccionesPausado)

        previewCamara = findViewById(R.id.previewCamara)

        txtEstadoMonitoreo = findViewById(R.id.txtEstadoMonitoreo)
        txtPorcentajeFatiga = findViewById(R.id.txtPorcentajeFatiga)
        txtEstadoConductor = findViewById(R.id.txtEstadoConductor)
        txtNivelAlerta = findViewById(R.id.txtNivelAlerta)
        txtRutaMonitoreo = findViewById(R.id.txtRutaMonitoreo)
        txtUltimasAlertas = findViewById(R.id.txtUltimasAlertas)
        txtConexionGorra = findViewById(R.id.txtConexionGorra)

        txtRutaMonitoreo.text =
            nombreRuta.ifBlank { "Ruta seleccionada" }
    }

    private fun configurarClicks() {
        btnBack.setOnClickListener {
            intentarSalir()
        }

        btnConfigurar.setOnClickListener {
            if (gorraConectada || shouldBeConnected) {
                desconectarGorraManualmente()
            } else {
                validarPermisosBle()
            }
        }

        btnIniciarViaje.setOnClickListener {
            if (!operacionViajeEnProceso) {
                mostrarDialogoCalibracion()
            }
        }

        btnPausarViaje.setOnClickListener {
            if (!operacionViajeEnProceso) {
                pausarViaje()
            }
        }

        btnReanudarViaje.setOnClickListener {
            if (!operacionViajeEnProceso) {
                reanudarViaje()
            }
        }

        btnTerminarViaje.setOnClickListener {
            if (!operacionViajeEnProceso) {
                terminarViaje()
            }
        }

        btnTerminarViajePausado.setOnClickListener {
            if (!operacionViajeEnProceso) {
                terminarViaje()
            }
        }

        btnApagarAlarma.setOnClickListener {
            apagarAlarmas()
        }

        btnApagarAlarmaPausado.setOnClickListener {
            apagarAlarmas()
        }
    }

    private fun configurarBack() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    intentarSalir()
                }
            }
        )
    }

    private fun actualizarUIEstado() {
        when (estadoViaje) {
            EstadoViaje.INACTIVO -> {
                txtEstadoMonitoreo.text = "Inactivo"
                txtEstadoMonitoreo.setBackgroundResource(
                    R.drawable.bg_badge_dark
                )
            }

            EstadoViaje.ACTIVO -> {
                txtEstadoMonitoreo.text = "Activo"
                txtEstadoMonitoreo.setBackgroundResource(
                    R.drawable.bg_badge_terminada
                )
                txtEstadoMonitoreo.setTextColor(
                    Color.parseColor("#166534")
                )
            }

            EstadoViaje.PAUSADO -> {
                txtEstadoMonitoreo.text = "Pausado"
                txtEstadoMonitoreo.setBackgroundResource(
                    R.drawable.bg_badge_pendiente
                )
                txtEstadoMonitoreo.setTextColor(
                    Color.parseColor("#7A4D00")
                )
            }
        }

        actualizarBotonesViaje()
    }

    private fun actualizarBotonesViaje() {
        val disponible = !operacionViajeEnProceso

        contenedorAccionesIniciales.visibility =
            if (estadoViaje == EstadoViaje.INACTIVO) {
                View.VISIBLE
            } else {
                View.GONE
            }

        contenedorAccionesActivo.visibility =
            if (estadoViaje == EstadoViaje.ACTIVO) {
                View.VISIBLE
            } else {
                View.GONE
            }

        contenedorAccionesPausado.visibility =
            if (estadoViaje == EstadoViaje.PAUSADO) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnConfigurar.isEnabled =
            disponible && estadoViaje == EstadoViaje.INACTIVO

        btnIniciarViaje.isEnabled =
            disponible && estadoViaje == EstadoViaje.INACTIVO

        btnPausarViaje.isEnabled =
            disponible && estadoViaje == EstadoViaje.ACTIVO

        btnReanudarViaje.isEnabled =
            disponible && estadoViaje == EstadoViaje.PAUSADO

        btnTerminarViaje.isEnabled =
            disponible && estadoViaje == EstadoViaje.ACTIVO

        btnTerminarViajePausado.isEnabled =
            disponible && estadoViaje == EstadoViaje.PAUSADO

        btnApagarAlarma.isEnabled = disponible
        btnApagarAlarmaPausado.isEnabled = disponible
    }

    private fun actualizarIndicadorGorra(
        conectada: Boolean
    ) {
        runOnUiThread {
            if (conectada) {
                txtConexionGorra.text = "Conectada"

                txtConexionGorra.setTextColor(
                    Color.parseColor("#166534")
                )

                txtConexionGorra.setBackgroundResource(
                    R.drawable.bg_badge_terminada
                )

                btnConfigurar.text = "Desconectar"
                btnConfigurar.isEnabled = true

            } else {
                txtConexionGorra.text =
                    if (shouldBeConnected) {
                        "Reconectando"
                    } else {
                        "Sin gorra"
                    }

                txtConexionGorra.setTextColor(
                    Color.parseColor("#7A4D00")
                )

                txtConexionGorra.setBackgroundResource(
                    R.drawable.bg_badge_pendiente
                )

                btnConfigurar.text =
                    if (shouldBeConnected) {
                        "Reconectando..."
                    } else {
                        "Conectar"
                    }
            }
        }
    }

    // =========================================================
    // ALARMA DEL CELULAR
    // =========================================================

    private fun inicializarAlarmaCelular() {
        vibrator = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            val manager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager

            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(
                Context.VIBRATOR_SERVICE
            ) as Vibrator
        }
    }

    private fun iniciarAlarmaCelular() {
        if (alarmaCelularActiva) {
            return
        }

        alarmaCelularActiva = true

        try {
            val uriAlarma =
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ) ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )

            ringtoneAlarma = RingtoneManager.getRingtone(
                applicationContext,
                uriAlarma
            )

            ringtoneAlarma?.audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtoneAlarma?.isLooping = true
            }

            ringtoneAlarma?.play()

        } catch (_: Exception) {
            notificationHelper.mostrarNotificacion(
                "Alarma SOMNIX",
                "No se pudo reproducir el sonido del celular."
            )
        }

        try {
            val patron = longArrayOf(
                0,
                600,
                300,
                600,
                300,
                900
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        patron,
                        0
                    ),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(patron, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun detenerAlarmaCelular() {
        alarmaCelularActiva = false

        try {
            ringtoneAlarma?.stop()
        } catch (_: Exception) {
        }

        ringtoneAlarma = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    // =========================================================
    // POPUP DE NECESIDADES
    // =========================================================

    private fun ejecutarAlertaFatiga(
        porcentaje: Int,
        motivo: String
    ) {
        if (
            estadoViaje != EstadoViaje.ACTIVO ||
            popupNecesidadVisible ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        iniciarAlarmaCelular()

        notificationHelper.mostrarNotificacion(
            "Alerta de fatiga",
            "Detente de forma segura y selecciona lo que necesitas."
        )

        mostrarPopupNecesidad(
            porcentaje = porcentaje,
            motivo = motivo
        )
    }

    private fun mostrarPopupNecesidad(
        porcentaje: Int,
        motivo: String
    ) {
        if (
            popupNecesidadVisible ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        popupNecesidadVisible = true

        val vista = LayoutInflater.from(this).inflate(
            R.layout.dialog_necesidad_conductor,
            null,
            false
        )

        val txtMensajePopup =
            vista.findViewById<TextView>(
                R.id.txtMensajePopup
            )

        txtFatigaPopup =
            vista.findViewById<TextView>(
                R.id.txtFatigaPopup
            )

        txtMensajePopup.text = motivo

        txtFatigaPopup?.text =
            if (porcentaje > 0) {
                "Fatiga detectada: $porcentaje%"
            } else {
                "Alerta detectada por la gorra"
            }

        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .setCancelable(false)
            .create()

        dialogo.setCanceledOnTouchOutside(false)

        dialogo.setOnDismissListener {
            popupNecesidadVisible = false
            dialogoNecesidad = null
            txtFatigaPopup = null
        }

        configurarOpcionNecesidad(
            vista,
            R.id.popupDescansar,
            "necesito_descansar",
            "El viaje se pausó porque necesitas descansar."
        )

        configurarOpcionNecesidad(
            vista,
            R.id.popupAgua,
            "necesito_hidratarme",
            "El viaje se pausó porque necesitas hidratarte."
        )

        configurarOpcionNecesidad(
            vista,
            R.id.popupComer,
            "necesito_comer",
            "El viaje se pausó porque necesitas comer."
        )

        configurarOpcionNecesidad(
            vista,
            R.id.popupEstirar,
            "necesito_estirar",
            "El viaje se pausó porque necesitas estirarte."
        )

        configurarOpcionNecesidad(
            vista,
            R.id.popupDormir,
            "necesito_dormir",
            "El viaje se pausó porque necesitas dormir."
        )

        configurarOpcionNecesidad(
            vista,
            R.id.popupNoConducir,
            "necesito_dejar_de_manejar",
            "El viaje se pausó porque necesitas dejar de conducir."
        )

        dialogoNecesidad = dialogo
        dialogo.show()

        dialogo.window?.apply {
            setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            setLayout(
                (
                        resources.displayMetrics.widthPixels * 0.92
                        ).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun configurarOpcionNecesidad(
        vista: View,
        idVista: Int,
        tipo: String,
        mensaje: String
    ) {
        vista.findViewById<View>(idVista)
            .setOnClickListener {

                if (!operacionViajeEnProceso) {
                    seleccionarNecesidad(
                        tipo = tipo,
                        mensaje = mensaje
                    )
                }
            }
    }

    private fun seleccionarNecesidad(
        tipo: String,
        mensaje: String
    ) {
        if (estadoViaje != EstadoViaje.ACTIVO) {
            detenerAlarmaCelular()
            dialogoNecesidad?.dismiss()
            return
        }

        if (operacionViajeEnProceso) {
            return
        }

        operacionViajeEnProceso = true

        /*
         * Cerramos el popup inmediatamente.
         */
        dialogoNecesidad?.dismiss()
        dialogoNecesidad = null
        popupNecesidadVisible = false

        /*
         * Pausamos localmente de inmediato para impedir que
         * otro frame vuelva a abrir el popup.
         */
        monitoreoActivo = false
        detenerEnvioFrames()
        estadoViaje = EstadoViaje.PAUSADO

        sessionManager.guardarFatigaActual(
            ultimoPorcentajeFatiga
        )

        sessionManager.guardarEstadoViaje(
            "PAUSADO"
        )

        detenerAlarmaCelular()
        actualizarUIEstado()

        if (gorraConectada) {
            enviarComandoGorra("APAGAR")
            enviarComandoGorra("VIAJE_PAUSAR")
        }

        lifecycleScope.launch {
            try {
                try {
                    controlRepository.apagarAlarma(
                        usuarioId = usuarioId,
                        rutaId = rutaId
                    )
                } catch (_: Exception) {
                }

                val respuestaPausa =
                    controlRepository.pausarViaje()

                val respuestaNecesidad =
                    controlRepository.registrarNecesidad(
                        usuarioId,
                        rutaId,
                        tipo,
                        mensaje
                    )

                obtenerAlertasRuta()

                when {
                    !respuestaPausa.isSuccessful -> {
                        notificationHelper.mostrarNotificacion(
                            "Viaje pausado localmente",
                            "El servidor no confirmó la pausa."
                        )
                    }

                    respuestaNecesidad.isSuccessful &&
                            respuestaNecesidad.body()?.ok == true -> {
                        notificationHelper.mostrarNotificacion(
                            "Viaje pausado",
                            mensaje
                        )
                    }

                    else -> {
                        notificationHelper.mostrarNotificacion(
                            "Viaje pausado",
                            "El viaje se pausó, pero no se registró la necesidad."
                        )
                    }
                }

            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Viaje pausado localmente",
                    "No se pudo sincronizar con el servidor: ${
                        e.message ?: "error de conexión"
                    }"
                )

            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
            }
        }
    }

    // =========================================================
    // CÁMARA
    // =========================================================

    private fun validarPermisosIniciales() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisoCamaraLauncher.launch(
                Manifest.permission.CAMERA
            )

            return
        }

        iniciarCamara()
        validarPermisoNotificaciones()
    }

    private fun validarPermisoNotificaciones() {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permisoNotificacionesLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun iniciarCamara() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider =
                    cameraProviderFuture.get()

                val preview =
                    Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(
                                previewCamara.surfaceProvider
                            )
                        }

                imageCapture =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build()

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageCapture
                )

                if (
                    estadoViaje == EstadoViaje.ACTIVO
                ) {
                    monitoreoActivo = true
                    iniciarEnvioFrames()
                }

            } catch (_: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error de cámara",
                    "No se pudo iniciar la cámara."
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun iniciarEnvioFrames() {
        if (frameLoopJob?.isActive == true) {
            return
        }

        frameLoopJob = lifecycleScope.launch {
            while (
                isActive &&
                monitoreoActivo &&
                estadoViaje == EstadoViaje.ACTIVO
            ) {
                capturarYEnviarFrame()
                delay(750L)
            }
        }
    }

    private fun detenerEnvioFrames() {
        frameLoopJob?.cancel()
        frameLoopJob = null

        frameRequestJob?.cancel()
        frameRequestJob = null

        enviandoFrame = false
    }

    private fun capturarYEnviarFrame() {
        if (
            enviandoFrame ||
            !monitoreoActivo ||
            estadoViaje != EstadoViaje.ACTIVO
        ) {
            return
        }

        val captura = imageCapture ?: return

        enviandoFrame = true

        val archivo = File(
            cacheDir,
            "frame_${System.currentTimeMillis()}.jpg"
        )

        val opciones =
            ImageCapture.OutputFileOptions.Builder(
                archivo
            ).build()

        captura.takePicture(
            opciones,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    frameRequestJob = lifecycleScope.launch {
                        try {
                            if (
                                !monitoreoActivo ||
                                estadoViaje != EstadoViaje.ACTIVO
                            ) {
                                return@launch
                            }

                            val response =
                                frameRepository.analizarFrame(
                                    usuarioId,
                                    rutaId,
                                    archivo
                                )

                            if (
                                response.isSuccessful &&
                                response.body()?.ok == true
                            ) {
                                val body = response.body()!!

                                val fatiga = body.fatiga ?: 0
                                val estado =
                                    body.estado ?: "Normal"

                                val nivel =
                                    body.nivel ?: "bajo"

                                ultimoPorcentajeFatiga =
                                    fatiga

                                sessionManager.guardarFatigaActual(
                                    fatiga
                                )

                                txtPorcentajeFatiga.text =
                                    "Fatiga: $fatiga%"

                                /*
                                 * Mantiene el porcentaje del popup
                                 * sincronizado con el último frame.
                                 */
                                txtFatigaPopup?.text =
                                    "Fatiga detectada: $fatiga%"

                                txtEstadoConductor.text =
                                    "Estado: $estado"

                                txtNivelAlerta.text =
                                    when {
                                        fatiga >= 70 -> "Alto"
                                        fatiga >= 50 -> "Medio"
                                        fatiga > 35 -> "Precaución"
                                        else -> nivel.replaceFirstChar {
                                            it.uppercase()
                                        }
                                    }

                                /*
                                 * Requisito solicitado:
                                 * mostrar popup cuando la fatiga
                                 * sea mayor al 20%.
                                 */
                                if (
                                    estadoViaje == EstadoViaje.ACTIVO &&
                                    fatiga >= 5 &&
                                    !alarmaFatiga5Enviada
                                ) {
                                    alarmaFatiga5Enviada = true

                                    if (gorraConectada) {
                                        enviarComandoGorra(
                                            "FORZAR_NIVEL_3"
                                        )
                                    }

                                    ejecutarAlertaFatiga(
                                        porcentaje = fatiga,
                                        motivo =
                                            "Se detectó fatiga igual o superior al 5%."
                                    )
                                }

                                if (
                                    fatiga < 3
                                ) {
                                    alarmaFatiga5Enviada = false
                                }
                            } else {
                                android.util.Log.e(
                                    "SOMNIX_FRAME",
                                    "Frame rechazado. HTTP=${response.code()}, " +
                                            "respuesta=${response.body()}, " +
                                            "error=${response.errorBody()?.string()}"
                                )
                            }

                        } catch (error: Exception) {
                            android.util.Log.e(
                                "SOMNIX_CAMARA",
                                "Error enviando frame",
                                error
                            )
                        } finally {
                            archivo.delete()
                            enviandoFrame = false
                            frameRequestJob = null
                        }
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    archivo.delete()
                    enviandoFrame = false

                    android.util.Log.e(
                        "SOMNIX_CAMARA",
                        "Error capturando frame",
                        exception
                    )
                }
            }
        )
    }

    // =========================================================
    // VIAJE
    // =========================================================

    private fun mostrarDialogoCalibracion() {
        if (estadoViaje == EstadoViaje.ACTIVO) {
            notificationHelper.mostrarNotificacion(
                "Viaje activo",
                "Ya tienes un viaje en curso."
            )
            return
        }

        if (estadoViaje == EstadoViaje.PAUSADO) {
            notificationHelper.mostrarNotificacion(
                "Viaje pausado",
                "Utiliza el botón Reanudar."
            )
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Calibración SOMNIX")
            .setMessage(
                "Acomódate la gorra, siéntate derecho y mira al frente. " +
                        "Al confirmar se iniciará el monitoreo."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                iniciarViaje()
            }
            .show()
    }

    private fun iniciarViaje() {
        if (
            estadoViaje != EstadoViaje.INACTIVO ||
            operacionViajeEnProceso
        ) {
            return
        }

        operacionViajeEnProceso = true
        estadoViaje = EstadoViaje.ACTIVO
        monitoreoActivo = true

        ultimoPorcentajeFatiga = 0
        alarmaFatiga5Enviada = false
        sessionManager.guardarFatigaActual(0)
        txtPorcentajeFatiga.text = "Fatiga: 0%"

        sessionManager.guardarEstadoViaje("ACTIVO")
        actualizarUIEstado()

        if (gorraConectada || shouldBeConnected) {
            enviarComandoGorra("CALIBRAR")

            handlerComandosGorra.postDelayed({
                if (
                    gorraConectada &&
                    estadoViaje == EstadoViaje.ACTIVO
                ) {
                    enviarComandoGorra("VIAJE_INICIAR")
                }
            }, 600L)
        } else {
            notificationHelper.mostrarNotificacion(
                "Gorra no conectada",
                "La cámara inició y la gorra se sincronizará al reconectar."
            )
        }

        iniciarEnvioFrames()

        notificationHelper.mostrarNotificacion(
            "Viaje iniciado",
            "El monitoreo del conductor ha comenzado."
        )

        operacionViajeEnProceso = false
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                val response =
                    controlRepository.iniciarViaje(
                        usuarioId = usuarioId,
                        rutaId = rutaId,
                        nombreRuta = nombreRuta
                    )

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    android.util.Log.e(
                        "SOMNIX_API",
                        "Python no confirmó el inicio"
                    )
                }

            } catch (error: Exception) {
                android.util.Log.e(
                    "SOMNIX_API",
                    "No se pudo informar el inicio",
                    error
                )

            } finally {
                obtenerAlertasRuta()
            }
        }
    }

    private fun pausarViaje() {
        if (
            estadoViaje != EstadoViaje.ACTIVO ||
            operacionViajeEnProceso
        ) {
            return
        }

        operacionViajeEnProceso = true
        monitoreoActivo = false
        detenerEnvioFrames()
        estadoViaje = EstadoViaje.PAUSADO

        sessionManager.guardarFatigaActual(
            ultimoPorcentajeFatiga
        )

        sessionManager.guardarEstadoViaje("PAUSADO")
        actualizarUIEstado()

        if (gorraConectada || shouldBeConnected) {
            enviarComandoGorra("VIAJE_PAUSAR")
        }

        notificationHelper.mostrarNotificacion(
            "Viaje pausado",
            "El monitoreo fue pausado."
        )

        operacionViajeEnProceso = false
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                val response =
                    controlRepository.pausarViaje()

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    android.util.Log.e(
                        "SOMNIX_API",
                        "Python no confirmó la pausa"
                    )
                }

            } catch (error: Exception) {
                android.util.Log.e(
                    "SOMNIX_API",
                    "No se pudo informar la pausa",
                    error
                )

            } finally {
                obtenerAlertasRuta()
            }
        }
    }

    private fun reanudarViaje() {
        if (
            estadoViaje != EstadoViaje.PAUSADO ||
            operacionViajeEnProceso
        ) {
            return
        }

        operacionViajeEnProceso = true
        estadoViaje = EstadoViaje.ACTIVO
        monitoreoActivo = true
        alarmaFatiga5Enviada = false

        sessionManager.guardarEstadoViaje("ACTIVO")
        actualizarUIEstado()

        if (gorraConectada || shouldBeConnected) {
            enviarComandoGorra("CALIBRAR")

            handlerComandosGorra.postDelayed({
                if (
                    gorraConectada &&
                    estadoViaje == EstadoViaje.ACTIVO
                ) {
                    enviarComandoGorra("VIAJE_REANUDAR")
                }
            }, 600L)
        }

        iniciarEnvioFrames()

        notificationHelper.mostrarNotificacion(
            "Viaje reanudado",
            "El monitoreo fue reanudado."
        )

        operacionViajeEnProceso = false
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                val response =
                    controlRepository.reanudarViaje()

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    android.util.Log.e(
                        "SOMNIX_API",
                        "Python no confirmó la reanudación"
                    )
                }

            } catch (error: Exception) {
                android.util.Log.e(
                    "SOMNIX_API",
                    "No se pudo informar la reanudación",
                    error
                )

            } finally {
                obtenerAlertasRuta()
            }
        }
    }

    private fun terminarViaje() {
        if (
            estadoViaje == EstadoViaje.INACTIVO ||
            operacionViajeEnProceso
        ) {
            return
        }

        operacionViajeEnProceso = true
        monitoreoActivo = false
        detenerEnvioFrames()

        detenerAlarmaCelular()
        dialogoNecesidad?.dismiss()
        dialogoNecesidad = null
        popupNecesidadVisible = false

        if (gorraConectada || shouldBeConnected) {
            enviarComandoGorra("VIAJE_TERMINAR")

            handlerComandosGorra.postDelayed({
                if (gorraConectada) {
                    enviarComandoGorra("APAGAR")
                }
            }, 300L)
        }

        estadoViaje = EstadoViaje.INACTIVO
        sessionManager.limpiarViajeActivo()
        actualizarUIEstado()

        notificationHelper.mostrarNotificacion(
            "Viaje terminado",
            "El monitoreo finalizó correctamente."
        )

        lifecycleScope.launch {
            try {
                controlRepository.terminarViaje(
                    usuarioId = usuarioId,
                    rutaId = rutaId
                )

            } catch (error: Exception) {
                android.util.Log.e(
                    "SOMNIX_API",
                    "No se pudo informar el término",
                    error
                )
            }
        }

        handlerComandosGorra.postDelayed({
            operacionViajeEnProceso = false

            if (!isFinishing) {
                finish()
            }
        }, 800L)
    }

    private fun apagarAlarmas() {
        detenerAlarmaCelular()

        dialogoNecesidad?.dismiss()

        if (gorraConectada || shouldBeConnected) {
            enviarComandoGorra("APAGAR")
        }
        lifecycleScope.launch {
            var backendApagado = false

            try {
                val response =
                    controlRepository.apagarAlarma(
                        usuarioId = usuarioId,
                        rutaId = rutaId
                    )

                backendApagado =
                    response.isSuccessful &&
                            response.body()?.ok == true

            } catch (_: Exception) {
                backendApagado = false
            }

            val mensaje =
                when {
                    backendApagado && gorraConectada ->
                        "Se apagaron las alarmas del celular, cámara y gorra."

                    backendApagado ->
                        "Se apagaron las alarmas del celular y cámara."

                    gorraConectada ->
                        "Se apagaron las alarmas del celular y gorra."

                    else ->
                        "Se apagó la alarma del celular."
                }

            notificationHelper.mostrarNotificacion(
                "Alarmas detenidas",
                mensaje
            )
        }
    }

    private fun intentarSalir() {
        if (estadoViaje == EstadoViaje.ACTIVO) {
            notificationHelper.mostrarNotificacion(
                "Viaje activo",
                "Primero pausa o termina el viaje para salir."
            )
        } else {
            finish()
        }
    }

    // =========================================================
    // ALERTAS
    // =========================================================

    private fun obtenerAlertasRuta() {
        lifecycleScope.launch {
            try {
                val response =
                    rutaRepository.obtenerAlertasPorRuta(
                        rutaId
                    )

                val alertas = response.body()

                txtUltimasAlertas.text =
                    if (
                        response.isSuccessful &&
                        !alertas.isNullOrEmpty()
                    ) {
                        val ultima = alertas.first()

                        "${ultima.nivel.uppercase()} · ${ultima.mensaje}"
                    } else {
                        "No hay alertas recientes"
                    }

            } catch (_: Exception) {
                txtUltimasAlertas.text =
                    "No se pudieron cargar las alertas"
            }
        }
    }

    private fun prepararServicioBle() {
        if (!tienePermisosBleCompletos()) {
            return
        }

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
        if (
            servicioBleVinculado ||
            !tienePermisosBleCompletos()
        ) {
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
            android.util.Log.e(
                "SOMNIX_BLE",
                "No se pudo vincular el servicio",
                error
            )
        }
    }

    private fun observarServicioBle() {
        observadoresBleJob?.cancel()

        val servicio =
            bleService ?: return

        observadoresBleJob =
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
                            procesarMensajeGorra(it)
                        }
                    }

                    launch {
                        servicio.logs.collect {
                            android.util.Log.d(
                                "SOMNIX_BLE",
                                it
                            )
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
                gorraConectada = false

                actualizarIndicadorGorra(false)
            }

            BleConnectionState.Buscando -> {
                gorraConectada = false
                shouldBeConnected = true

                runOnUiThread {
                    txtConexionGorra.text = "Buscando"
                    btnConfigurar.text = "Buscando..."
                }
            }

            BleConnectionState.Conectando -> {
                gorraConectada = false
                shouldBeConnected = true

                runOnUiThread {
                    txtConexionGorra.text = "Conectando"
                    btnConfigurar.text = "Conectando..."
                }
            }

            BleConnectionState.Configurando -> {
                gorraConectada = false
                shouldBeConnected = true

                runOnUiThread {
                    txtConexionGorra.text = "Configurando"
                    btnConfigurar.text = "Configurando..."
                }
            }

            BleConnectionState.Listo -> {
                val eraConectada =
                    gorraConectada

                gorraConectada = true
                shouldBeConnected = true

                actualizarIndicadorGorra(true)

                if (!eraConectada) {
                    notificationHelper.mostrarNotificacion(
                        "BLE SOMNIX",
                        "Gorra conectada correctamente.",
                        cooldownMs = 10_000L
                    )

                    sincronizarGorraConViaje()
                }
            }

            is BleConnectionState.Error -> {
                gorraConectada = false

                runOnUiThread {
                    txtConexionGorra.text = "Error BLE"
                    txtConexionGorra.setTextColor(
                        Color.parseColor("#B42318")
                    )

                    btnConfigurar.text =
                        if (shouldBeConnected) {
                            "Reconectando..."
                        } else {
                            "Conectar"
                        }
                }

                android.util.Log.e(
                    "SOMNIX_BLE",
                    estado.mensaje
                )
            }
        }
    }

    private fun sincronizarGorraConViaje() {
        when (estadoViaje) {
            EstadoViaje.ACTIVO -> {
                enviarComandoGorra("CALIBRAR")

                handlerComandosGorra.postDelayed({
                    if (
                        gorraConectada &&
                        estadoViaje ==
                        EstadoViaje.ACTIVO
                    ) {
                        enviarComandoGorra(
                            "VIAJE_INICIAR"
                        )
                    }
                }, 700L)
            }

            EstadoViaje.PAUSADO -> {
                enviarComandoGorra(
                    "VIAJE_PAUSAR"
                )
            }

            EstadoViaje.INACTIVO -> {
                enviarComandoGorra("SYNC")
            }
        }
    }

    private fun enviarComandoGorra(
        comando: String
    ): Boolean {
        val servicio = bleService

        if (servicio == null) {
            android.util.Log.e(
                "SOMNIX_COMANDO",
                "$comando → SERVICIO NO VINCULADO"
            )

            return false
        }

        val aceptado =
            servicio.enviarComando(comando)

        android.util.Log.d(
            "SOMNIX_COMANDO",
            "$comando → " +
                    if (aceptado) {
                        "ACEPTADO"
                    } else {
                        "RECHAZADO"
                    }
        )

        if (!aceptado) {
            runOnUiThread {
                txtConexionGorra.text =
                    "Sin comunicación"

                txtConexionGorra.setTextColor(
                    Color.parseColor("#B42318")
                )
            }
        }

        return aceptado
    }

    private fun validarPermisosBle() {
        val permisos =
            mutableListOf<String>()

        /*
         * Tu dispositivo OPPO requiere ubicación para
         * entregar resultados de escaneo BLE.
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
            conectarGorra()
        } else {
            permisosBleLauncher.launch(
                faltantes.toTypedArray()
            )
        }
    }

    private fun conectarGorra() {
        if (!tienePermisosBleCompletos()) {
            validarPermisosBle()
            return
        }

        shouldBeConnected = true

        prepararServicioBle()
        vincularServicioBle()

        val servicio = bleService

        if (servicio != null) {
            servicio.iniciarConexion()
        } else {
            /*
             * Inicia el servicio con la orden de conectar.
             * onServiceConnected recuperará después la instancia.
             */
            SomnixBleService.iniciar(this)
        }

        actualizarIndicadorGorra(false)
    }

    private fun desconectarGorraManualmente() {
        shouldBeConnected = false
        gorraConectada = false
        nivelAlertaGorraAnterior = 0

        bleService?.detenerConexion()

        actualizarIndicadorGorra(false)
    }

    private fun tienePermisosBleCompletos(): Boolean {
        val ubicacion =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!ubicacion) {
            return false
        }

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            val scan =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

            val connect =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            scan && connect
        } else {
            true
        }
    }

    private fun procesarMensajeGorra(
        mensaje: String
    ) {
        val mensajeLimpio = mensaje.trim()

        if (mensajeLimpio.isBlank()) {
            return
        }

        /*
         * Arduino manda su telemetría BLE como JSON:
         *
         * {
         *   "p": pitch,
         *   "r": roll,
         *   "n": nivelAlerta,
         *   "t": modoTest,
         *   "v": estadoViaje,
         *   "hc": codigoHttp,
         *   "tr": tiempoReaccion
         * }
         */
        if (
            mensajeLimpio.startsWith("{") &&
            mensajeLimpio.endsWith("}")
        ) {
            try {
                val json = JSONObject(mensajeLimpio)

                if (json.has("n")) {
                    val nivelAlertaGorra =
                        json.optInt("n", 0)

                    val estadoViajeGorra =
                        json.optInt("v", 0)

                    val pitch =
                        json.optDouble("p", 0.0)

                    val roll =
                        json.optDouble("r", 0.0)

                    android.util.Log.d(
                        "SOMNIX_TELEMETRIA",
                        "Nivel=$nivelAlertaGorra, " +
                                "Viaje=$estadoViajeGorra, " +
                                "Pitch=$pitch, Roll=$roll"
                    )

                    /*
                     * Si la gorra comienza a sonar,
                     * el celular también comienza a sonar.
                     */
                    if (
                        nivelAlertaGorra > 0 &&
                        nivelAlertaGorraAnterior == 0 &&
                        estadoViaje == EstadoViaje.ACTIVO
                    ) {
                        val motivo = when (
                            nivelAlertaGorra
                        ) {
                            1 ->
                                "La gorra detectó una inclinación de advertencia."

                            2 ->
                                "La gorra detectó una inclinación peligrosa."

                            else ->
                                "La gorra detectó una inclinación crítica."
                        }

                        ejecutarAlertaFatiga(
                            porcentaje = ultimoPorcentajeFatiga,
                            motivo = motivo
                        )
                    }

                    /*
                     * Si la alarma física deja de sonar,
                     * también detenemos el sonido y vibración
                     * del celular.
                     */
                    if (
                        nivelAlertaGorra == 0 &&
                        nivelAlertaGorraAnterior > 0
                    ) {
                        detenerAlarmaCelular()
                    }

                    nivelAlertaGorraAnterior =
                        nivelAlertaGorra

                    return
                }

            } catch (error: Exception) {
                android.util.Log.e(
                    "SOMNIX_BLE",
                    "JSON BLE inválido: $mensajeLimpio",
                    error
                )
            }
        }

        /*
         * Compatibilidad con mensajes de texto que pudiera
         * mandar otra versión del firmware.
         */
        val normalizado =
            mensajeLimpio.uppercase()

        val alarmaDetectada =
            normalizado == "ALARMA" ||
                    normalizado == "ALARMA_ON" ||
                    normalizado == "NIVEL_1" ||
                    normalizado == "NIVEL_2" ||
                    normalizado == "NIVEL_3" ||
                    normalizado == "SOMNOLENCIA" ||
                    normalizado == "FATIGA_ALTA" ||
                    normalizado == "INCLINACION_PELIGROSA" ||
                    normalizado == "INCLINACIÓN_PELIGROSA"

        if (
            alarmaDetectada &&
            estadoViaje == EstadoViaje.ACTIVO
        ) {
            ejecutarAlertaFatiga(
                porcentaje = ultimoPorcentajeFatiga,
                motivo = (
                        "La gorra SOMNIX detectó "
                                + "una condición de riesgo."
                        )
            )

            return
        }

        if (
            normalizado == "ALARMA_OFF" ||
            normalizado == "APAGADA"
        ) {
            detenerAlarmaCelular()
            nivelAlertaGorraAnterior = 0
            return
        }

        android.util.Log.d(
            "SOMNIX_MENSAJE_BLE",
            mensajeLimpio
        )
    }
}