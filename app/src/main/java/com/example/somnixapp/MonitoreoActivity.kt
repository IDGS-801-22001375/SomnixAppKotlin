package com.example.somnixapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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
import com.example.somnixapp.ble.SomnixBleManager
import com.example.somnixapp.repository.PythonRepository
import com.example.somnixapp.repository.RutaRepository
import com.example.somnixapp.utils.NotificationHelper
import com.example.somnixapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MonitoreoActivity : AppCompatActivity() {

    private enum class EstadoViaje {
        INACTIVO,
        ACTIVO,
        PAUSADO
    }

    private var intentosReconexionBle = 0

    private val handlerReconexionBle =
        Handler(Looper.getMainLooper())

    private val runnableReconexionBle = Runnable {
        if (
            shouldBeConnected &&
            !gorraConectada &&
            !bleManager.estaConectando
        ) {
            iniciarEscaneoBle()
        }
    }

    private var estadoViaje = EstadoViaje.INACTIVO
    private var monitoreoActivo = false
    private var operacionViajeEnProceso = false

    private var escaneandoBle = false
    private var shouldBeConnected = false
    private var gorraConectada = false

    private val nombreGorraBle = "SOMNIX_IDGS901"

    private lateinit var bleManager: SomnixBleManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var sessionManager: SessionManager

    private val pythonRepository = PythonRepository()
    private val rutaRepository = RutaRepository()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

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

    private var alarmaCelularActiva = false
    private var ringtoneAlarma: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var ultimoPorcentajeFatiga = 0

    // Permisos

    private val permisosBleLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permisos ->

            val concedidos = permisos.values.all { it }

            if (concedidos) {
                iniciarEscaneoBle()
            } else {
                notificationHelper.mostrarNotificacion(
                    "Permisos BLE",
                    "Activa los permisos Bluetooth para conectar la gorra."
                )
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

        setContentView(R.layout.activity_monitoreo)

        sessionManager = SessionManager(this)
        notificationHelper = NotificationHelper(this)

        usuarioId = sessionManager.obtenerUsuarioId().orEmpty()
        rutaId = sessionManager.obtenerRutaId().orEmpty()
        nombreRuta = sessionManager.obtenerNombreRuta().orEmpty()

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

        cameraExecutor = Executors.newSingleThreadExecutor()

        inicializarVistas()
        inicializarAlarmaCelular()
        inicializarBle()

        configurarClicks()
        configurarBack()
        actualizarUIEstado()

        obtenerAlertasRuta()
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
        popupNecesidadVisible = false

        shouldBeConnected = false
        monitoreoActivo = false

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        if (::bleManager.isInitialized) {
            if (tienePermisoScan()) {
                detenerEscaneoBle()
            }

            bleManager.desconectar()
        }

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

        val txtFatigaPopup =
            vista.findViewById<TextView>(
                R.id.txtFatigaPopup
            )

        txtMensajePopup.text = motivo

        txtFatigaPopup.text =
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
        estadoViaje = EstadoViaje.PAUSADO

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
                    pythonRepository.apagarAlarma(
                        usuarioId = usuarioId,
                        rutaId = rutaId
                    )
                } catch (_: Exception) {
                }

                val respuestaPausa =
                    pythonRepository.pausarViaje()

                val respuestaNecesidad =
                    pythonRepository.registrarNecesidad(
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

            } catch (_: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error de cámara",
                    "No se pudo iniciar la cámara."
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun iniciarEnvioFrames() {
        lifecycleScope.launch {
            while (monitoreoActivo) {
                capturarYEnviarFrame()
                delay(2000)
            }
        }
    }

    private fun capturarYEnviarFrame() {
        val captura = imageCapture ?: return

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
                    lifecycleScope.launch {
                        try {
                            val response =
                                pythonRepository.analizarFrame(
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

                                txtPorcentajeFatiga.text =
                                    "Fatiga: $fatiga%"

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
                                    fatiga > 45
                                ) {
                                    ejecutarAlertaFatiga(
                                        porcentaje = fatiga,
                                        motivo =
                                            "Se detectó un nivel de fatiga superior al recomendado."
                                    )
                                }

                                if (
                                    fatiga >= 75 ||
                                    nivel.equals(
                                        "alto",
                                        ignoreCase = true
                                    ) ||
                                    estado.equals(
                                        "OJOS_CERRADOS",
                                        ignoreCase = true
                                    ) ||
                                    estado.equals(
                                        "SOMNOLENCIA",
                                        ignoreCase = true
                                    )
                                ) {
                                    if (gorraConectada) {
                                        bleManager.enviarComando(
                                            "FORZAR_NIVEL_3"
                                        )
                                    }
                                }
                            }

                        } catch (_: Exception) {
                        } finally {
                            archivo.delete()
                        }
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    archivo.delete()
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
        if (estadoViaje != EstadoViaje.INACTIVO) {
            return
        }

        if (operacionViajeEnProceso) {
            return
        }

        operacionViajeEnProceso = true
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                val response =
                    pythonRepository.iniciarViaje(
                        usuarioId = usuarioId,
                        rutaId = rutaId,
                        nombreRuta = nombreRuta
                    )

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo iniciar el monitoreo."
                    )

                    return@launch
                }

                estadoViaje = EstadoViaje.ACTIVO
                monitoreoActivo = true

                sessionManager.guardarEstadoViaje(
                    "ACTIVO"
                )

                actualizarUIEstado()

                if (gorraConectada) {
                    bleManager.enviarComando("CALIBRAR")
                    delay(600)
                    bleManager.enviarComando("VIAJE_INICIAR")
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Gorra no conectada",
                        "La cámara inició, pero falta conectar la gorra."
                    )
                }

                iniciarEnvioFrames()
                obtenerAlertasRuta()

                notificationHelper.mostrarNotificacion(
                    "Viaje iniciado",
                    "El monitoreo del conductor ha comenzado."
                )

            } catch (e: Exception) {
                monitoreoActivo = false

                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo iniciar el viaje: ${
                        e.message ?: "error de conexión"
                    }"
                )

            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
            }
        }
    }

    private fun pausarViaje() {
        if (estadoViaje != EstadoViaje.ACTIVO) {
            return
        }

        if (operacionViajeEnProceso) {
            return
        }

        operacionViajeEnProceso = true
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                val response =
                    pythonRepository.pausarViaje()

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo pausar el monitoreo."
                    )

                    return@launch
                }

                monitoreoActivo = false

                if (gorraConectada) {
                    bleManager.enviarComando(
                        "VIAJE_PAUSAR"
                    )
                }

                estadoViaje = EstadoViaje.PAUSADO

                sessionManager.guardarEstadoViaje(
                    "PAUSADO"
                )

                actualizarUIEstado()
                obtenerAlertasRuta()

                notificationHelper.mostrarNotificacion(
                    "Viaje pausado",
                    "El monitoreo fue pausado correctamente."
                )

            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo pausar el viaje: ${
                        e.message ?: "error de conexión"
                    }"
                )

            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
            }
        }
    }

    private fun reanudarViaje() {
        if (estadoViaje != EstadoViaje.PAUSADO) {
            return
        }

        if (operacionViajeEnProceso) {
            return
        }

        operacionViajeEnProceso = true
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                val response =
                    pythonRepository.iniciarViaje(
                        usuarioId = usuarioId,
                        rutaId = rutaId,
                        nombreRuta = nombreRuta
                    )

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo reanudar el monitoreo."
                    )

                    return@launch
                }

                estadoViaje = EstadoViaje.ACTIVO
                monitoreoActivo = true

                sessionManager.guardarEstadoViaje(
                    "ACTIVO"
                )

                actualizarUIEstado()

                if (gorraConectada) {
                    bleManager.enviarComando("CALIBRAR")
                    delay(600)

                    bleManager.enviarComando(
                        "VIAJE_REANUDAR"
                    )
                }

                iniciarEnvioFrames()
                obtenerAlertasRuta()

                notificationHelper.mostrarNotificacion(
                    "Viaje reanudado",
                    "El monitoreo fue reanudado."
                )

            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo reanudar el viaje: ${
                        e.message ?: "error de conexión"
                    }"
                )

            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
            }
        }
    }

    private fun terminarViaje() {
        if (estadoViaje == EstadoViaje.INACTIVO) {
            return
        }

        if (operacionViajeEnProceso) {
            return
        }

        operacionViajeEnProceso = true
        actualizarBotonesViaje()

        detenerAlarmaCelular()
        dialogoNecesidad?.dismiss()

        lifecycleScope.launch {
            try {
                val response =
                    pythonRepository.terminarViaje(
                        usuarioId = usuarioId,
                        rutaId = rutaId
                    )

                if (
                    !response.isSuccessful ||
                    response.body()?.ok != true
                ) {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo terminar el viaje."
                    )

                    return@launch
                }

                monitoreoActivo = false

                if (gorraConectada) {
                    bleManager.enviarComando(
                        "VIAJE_TERMINAR"
                    )

                    bleManager.enviarComando("APAGAR")
                }

                estadoViaje = EstadoViaje.INACTIVO

                sessionManager.limpiarViajeActivo()

                actualizarUIEstado()

                notificationHelper.mostrarNotificacion(
                    "Viaje terminado",
                    "El monitoreo finalizó correctamente."
                )

                finish()

            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo terminar el viaje: ${
                        e.message ?: "error de conexión"
                    }"
                )

            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
            }
        }
    }

    private fun apagarAlarmas() {
        detenerAlarmaCelular()

        dialogoNecesidad?.dismiss()

        if (gorraConectada) {
            bleManager.enviarComando("APAGAR")
        }

        lifecycleScope.launch {
            var backendApagado = false

            try {
                val response =
                    pythonRepository.apagarAlarma(
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

    // =========================================================
    // BLE
    // =========================================================

    private fun inicializarBle() {
        bleManager = SomnixBleManager(
            context = this,

            onEstado = { estado ->
                /*
                 * Los estados BLE se pueden revisar en Logcat,
                 * pero no se convierten en notificaciones.
                 */
                android.util.Log.d(
                    "SOMNIX_BLE",
                    estado
                )

                if (
                    estado.startsWith(
                        "ERROR:",
                        ignoreCase = true
                    )
                ) {
                    runOnUiThread {
                        txtConexionGorra.text = "Error BLE"
                        txtConexionGorra.setTextColor(
                            Color.parseColor("#B42318")
                        )
                    }
                }
            },

            onMensaje = { mensaje ->
                runOnUiThread {
                    procesarMensajeGorra(mensaje)
                }
            },

            onConectado = {
                gorraConectada = true
                escaneandoBle = false
                intentosReconexionBle = 0

                handlerReconexionBle.removeCallbacks(
                    runnableReconexionBle
                )

                runOnUiThread {
                    actualizarIndicadorGorra(true)

                    notificationHelper.mostrarNotificacion(
                        "BLE SOMNIX",
                        "Gorra conectada correctamente.",
                        cooldownMs = 10_000L
                    )
                }

                /*
                 * Muy importante:
                 * si la gorra se conectó después de iniciar o pausar
                 * el viaje, hay que sincronizarla.
                 */
                lifecycleScope.launch {
                    delay(600L)

                    when (estadoViaje) {
                        EstadoViaje.ACTIVO -> {
                            bleManager.enviarComando("CALIBRAR")
                            delay(700L)
                            bleManager.enviarComando("VIAJE_INICIAR")
                        }

                        EstadoViaje.PAUSADO -> {
                            bleManager.enviarComando("VIAJE_PAUSAR")
                        }

                        EstadoViaje.INACTIVO -> {
                            bleManager.enviarComando("SYNC")
                        }
                    }
                }
            },

            onDesconectado = {
                gorraConectada = false
                escaneandoBle = false

                runOnUiThread {
                    actualizarIndicadorGorra(false)
                }

                if (shouldBeConnected) {
                    programarReconexionBle()
                }
            }
        )
    }

    private fun programarReconexionBle() {
        handlerReconexionBle.removeCallbacks(
            runnableReconexionBle
        )

        intentosReconexionBle++

        val espera = when (intentosReconexionBle) {
            1 -> 3_000L
            2 -> 6_000L
            3 -> 12_000L
            4 -> 20_000L
            else -> 30_000L
        }

        runOnUiThread {
            txtConexionGorra.text = "Reconectando"
            btnConfigurar.text = "Reconectando..."
        }

        handlerReconexionBle.postDelayed(
            runnableReconexionBle,
            espera
        )
    }

    private fun procesarMensajeGorra(
        mensaje: String
    ) {
        val normalizado = mensaje
            .trim()
            .uppercase()

        /*
         * Mensajes informativos enviados frecuentemente
         * por la gorra. No generan notificaciones ni popup.
         */
        val esTelemetria =
            normalizado.startsWith("POSICION") ||
                    normalizado.startsWith("POSICIÓN") ||
                    normalizado.startsWith("ANGULO") ||
                    normalizado.startsWith("ÁNGULO") ||
                    normalizado.startsWith("PITCH") ||
                    normalizado.startsWith("ROLL") ||
                    normalizado.startsWith("YAW") ||
                    normalizado.startsWith("IMU") ||
                    normalizado.startsWith("MPU") ||
                    normalizado.startsWith("ESTADO_NORMAL") ||
                    normalizado.startsWith("NORMAL") ||
                    normalizado.startsWith("CALIBRANDO") ||
                    normalizado.startsWith("CALIBRADO") ||
                    normalizado.startsWith("SYNC_OK")

        if (esTelemetria) {
            android.util.Log.d(
                "SOMNIX_TELEMETRIA",
                mensaje
            )

            return
        }

        /*
         * Solamente estos mensajes se consideran una alarma.
         */
        val alarmaDetectada =
            normalizado == "ALARMA" ||
                    normalizado == "ALARMA_ON" ||
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
                motivo =
                    "La gorra SOMNIX detectó una condición de riesgo."
            )

            return
        }

        /*
         * Mensajes desconocidos solamente se registran
         * en Logcat para identificar qué manda el ESP32.
         */
        android.util.Log.d(
            "SOMNIX_MENSAJE_BLE",
            mensaje
        )
    }

    private fun enviarComandoGorra(
        comando: String
    ): Boolean {
        val enviado = bleManager.enviarComando(comando)

        android.util.Log.d(
            "SOMNIX_COMANDO",
            "$comando → ${if (enviado) "ENVIADO" else "NO ENVIADO"}"
        )

        if (!enviado) {
            runOnUiThread {
                txtConexionGorra.text = "Sin comunicación"
                txtConexionGorra.setTextColor(
                    Color.parseColor("#B42318")
                )
            }
        }

        return enviado
    }

    private fun validarPermisosBle() {
        val permisos = mutableListOf<String>()

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
        } else {
            permisos.add(
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            permisos.add(
                Manifest.permission.BLUETOOTH
            )

            permisos.add(
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val faltanPermisos = permisos.any {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (faltanPermisos) {
            permisosBleLauncher.launch(
                permisos.toTypedArray()
            )
        } else {
            iniciarEscaneoBle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneoBle() {
        if (escaneandoBle || gorraConectada) {
            return
        }

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val bluetoothAdapter =
            bluetoothManager.adapter

        if (
            bluetoothAdapter == null ||
            !bluetoothAdapter.isEnabled
        ) {
            notificationHelper.mostrarNotificacion(
                "Bluetooth apagado",
                "Activa Bluetooth para conectar la gorra."
            )

            return
        }

        val scanner =
            bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            notificationHelper.mostrarNotificacion(
                "BLE SOMNIX",
                "El teléfono no pudo iniciar el escaneo."
            )

            return
        }

        shouldBeConnected = true
        escaneandoBle = true

        btnConfigurar.text = "Buscando..."

        shouldBeConnected = true
        escaneandoBle = true

        runOnUiThread {
            btnConfigurar.text = "Buscando..."
            txtConexionGorra.text = "Buscando"
        }

        scanner.startScan(scanCallbackBle)
    }

    private val scanCallbackBle =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val device = result.device

                val nombre =
                    device.name
                        ?: result.scanRecord?.deviceName
                        ?: ""

                if (
                    nombre.equals(
                        nombreGorraBle,
                        ignoreCase = true
                    )
                ) {
                    detenerEscaneoBle()

                    notificationHelper.mostrarNotificacion(
                        "BLE SOMNIX",
                        "Gorra encontrada."
                    )

                    bleManager.conectar(device)
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                escaneandoBle = false

                runOnUiThread {
                    btnConfigurar.text = "Conectar"
                }

                notificationHelper.mostrarNotificacion(
                    "BLE SOMNIX",
                    "Error al escanear: $errorCode"
                )
            }
        }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneoBle() {
        if (!tienePermisoScan()) {
            return
        }

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val bluetoothAdapter =
            bluetoothManager.adapter ?: return

        val scanner =
            bluetoothAdapter.bluetoothLeScanner
                ?: return

        try {
            scanner.stopScan(scanCallbackBle)
        } catch (_: Exception) {
        }

        escaneandoBle = false
    }

    private fun desconectarGorraManualmente() {
        shouldBeConnected = false
        gorraConectada = false
        intentosReconexionBle = 0

        handlerReconexionBle.removeCallbacks(
            runnableReconexionBle
        )

        detenerEscaneoBle()
        bleManager.desconectar()

        actualizarIndicadorGorra(false)
    }

    private fun tienePermisoScan(): Boolean {
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
}