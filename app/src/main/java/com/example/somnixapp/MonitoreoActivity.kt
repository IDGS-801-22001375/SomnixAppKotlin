package com.example.somnixapp

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
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
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission

class MonitoreoActivity : AppCompatActivity() {

    private enum class EstadoViaje {
        INACTIVO, ACTIVO, PAUSADO
    }

    private var estadoViaje = EstadoViaje.INACTIVO
    private var monitoreoActivo = false

    private var escaneandoBle = false
    private val nombreGorraBle = "SOMNIX"
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

    // ==========================================================
    //  VISTAS
    // ==========================================================

    private lateinit var btnBack: ImageView
    private lateinit var btnIniciarViaje: Button
    private lateinit var btnPausarViaje: Button
    private lateinit var btnReanudarViaje: Button
    private lateinit var btnTerminarViaje: Button
    private lateinit var btnApagarAlarma: Button

    private lateinit var previewCamara: PreviewView
    private lateinit var txtEstadoMonitoreo: TextView
    private lateinit var txtPorcentajeFatiga: TextView
    private lateinit var txtEstadoConductor: TextView
    private lateinit var txtNivelAlerta: TextView
    private lateinit var txtRutaMonitoreo: TextView
    private lateinit var txtUltimasAlertas: TextView

    private lateinit var chipDescansar: LinearLayout
    private lateinit var chipAgua: LinearLayout
    private lateinit var chipComer: LinearLayout
    private lateinit var chipEstirar: LinearLayout
    private lateinit var chipDormir: LinearLayout
    private lateinit var chipNoConducir: LinearLayout

    // ==========================================================
    //  ACTIVITY RESULT LAUNCHERS
    // ==========================================================

    private val permisosBleLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { permitido ->
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { permitido ->
            if (permitido) {
                notificationHelper.mostrarNotificacion(
                    "SOMNIX",
                    "Notificaciones activadas correctamente."
                )
            }
        }

    // ==========================================================
    //  CICLO DE VIDA
    // ==========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitoreo)

        sessionManager = SessionManager(this)
        notificationHelper = NotificationHelper(this)

        bleManager = SomnixBleManager(
            context = this,
            onEstado = { estado ->
                runOnUiThread {
                    notificationHelper.mostrarNotificacion("BLE SOMNIX", estado)
                }
            },
            onMensaje = { mensaje ->
                runOnUiThread {
                    notificationHelper.mostrarNotificacion("Gorra SOMNIX", mensaje)
                }
            }
        )

        usuarioId = sessionManager.obtenerUsuarioId() ?: ""
        rutaId = sessionManager.obtenerRutaId() ?: ""
        nombreRuta = sessionManager.obtenerNombreRuta() ?: ""

        if (usuarioId.isEmpty()) {
            notificationHelper.mostrarNotificacion(
                "Sesión no encontrada",
                "No hay una sesión iniciada."
            )
            finish()
            return
        }

        if (rutaId.isEmpty()) {
            notificationHelper.mostrarNotificacion(
                "Ruta requerida",
                "Selecciona una ruta antes de iniciar monitoreo."
            )
            finish()
            return
        }

        estadoViaje = when (sessionManager.obtenerEstadoViaje()) {
            "ACTIVO" -> EstadoViaje.ACTIVO
            "PAUSADO" -> EstadoViaje.PAUSADO
            else -> EstadoViaje.INACTIVO
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        inicializarVistas()
        configurarClicks()
        configurarBack()
        actualizarUIEstado()
        obtenerAlertasRuta()
        validarPermisosBle()
    }

    override fun onResume() {
        super.onResume()
        validarPermisosIniciales()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        monitoreoActivo = false
        cameraExecutor.shutdown()

        if (tienePermisoScan()) {
            detenerEscaneoBle()
        }

        bleManager.desconectar()
    }

    // ==========================================================
    //  CONFIGURACIÓN DE UI
    // ==========================================================

    private fun inicializarVistas() {
        btnBack = findViewById(R.id.btnBack)
        btnIniciarViaje = findViewById(R.id.btnIniciarViaje)
        btnPausarViaje = findViewById(R.id.btnPausarViaje)
        btnReanudarViaje = findViewById(R.id.btnReanudarViaje)
        btnTerminarViaje = findViewById(R.id.btnTerminarViaje)
        btnApagarAlarma = findViewById(R.id.btnApagarAlarma)

        previewCamara = findViewById(R.id.previewCamara)
        txtEstadoMonitoreo = findViewById(R.id.txtEstadoMonitoreo)
        txtPorcentajeFatiga = findViewById(R.id.txtPorcentajeFatiga)
        txtEstadoConductor = findViewById(R.id.txtEstadoConductor)
        txtNivelAlerta = findViewById(R.id.txtNivelAlerta)
        txtRutaMonitoreo = findViewById(R.id.txtRutaMonitoreo)
        txtUltimasAlertas = findViewById(R.id.txtUltimasAlertas)

        txtRutaMonitoreo.text = nombreRuta

        chipDescansar = findViewById(R.id.chipDescansar)
        chipAgua = findViewById(R.id.chipAgua)
        chipComer = findViewById(R.id.chipComer)
        chipEstirar = findViewById(R.id.chipEstirar)
        chipDormir = findViewById(R.id.chipDormir)
        chipNoConducir = findViewById(R.id.chipNoConducir)
    }

    private fun configurarClicks() {
        btnBack.setOnClickListener {
            intentarSalir()
        }

        btnIniciarViaje.setOnClickListener {
            mostrarDialogoCalibracion()
        }

        btnPausarViaje.setOnClickListener {
            pausarViaje()
        }

        btnReanudarViaje.setOnClickListener {
            reanudarViaje()
        }

        btnTerminarViaje.setOnClickListener {
            terminarViaje()
        }

        btnApagarAlarma.setOnClickListener {
            apagarAlarma()
        }

        chipDescansar.setOnClickListener {
            pausarPorNecesidad(
                "necesito_descansar",
                "El viaje se pausó porque necesitas descansar."
            )
        }

        chipAgua.setOnClickListener {
            pausarPorNecesidad(
                "necesito_hidratarme",
                "El viaje se pausó porque necesitas hidratarte."
            )
        }

        chipComer.setOnClickListener {
            pausarPorNecesidad(
                "necesito_comer",
                "El viaje se pausó porque necesitas comer algo."
            )
        }

        chipEstirar.setOnClickListener {
            pausarPorNecesidad(
                "necesito_estirar",
                "El viaje se pausó porque necesitas estirar un poco."
            )
        }

        chipDormir.setOnClickListener {
            pausarPorNecesidad(
                "necesito_dormir",
                "El viaje se pausó porque necesitas dormir antes de continuar."
            )
        }

        chipNoConducir.setOnClickListener {
            pausarPorNecesidad(
                "necesito_dejar_de_manejar",
                "El viaje se pausó porque necesitas dejar de manejar."
            )
        }
    }

    private fun configurarBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                intentarSalir()
            }
        })
    }

    private fun mostrarDialogoCalibracion() {
        AlertDialog.Builder(this)
            .setTitle("Calibración SOMNIX")
            .setMessage("Acomódate la gorra, siéntate derecho y mira al frente")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                lifecycleScope.launch {
                    bleManager.enviarComando("CALIBRAR")
                    delay(500)
                    bleManager.enviarComando("VIAJE_INICIAR")
                    iniciarViaje()
                }
            }
            .show()
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

    private fun actualizarUIEstado() {
        txtEstadoMonitoreo.text = when (estadoViaje) {
            EstadoViaje.INACTIVO -> "Inactivo"
            EstadoViaje.ACTIVO -> "Activo"
            EstadoViaje.PAUSADO -> "Pausado"
        }
    }

    // ==========================================================
    //  PERMISOS
    // ==========================================================

    private fun validarPermisosIniciales() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        iniciarCamara()
        validarPermisoNotificaciones()
    }

    private fun validarPermisoCamara() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCamara()
        } else {
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun validarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun validarPermisosBle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permisosNecesarios = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            val faltanPermisos = permisosNecesarios.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (faltanPermisos) {
                permisosBleLauncher.launch(permisosNecesarios)
            } else {
                iniciarEscaneoBle()
            }
        } else {
            iniciarEscaneoBle()
        }
    }

    // ==========================================================
    //  CÁMARA
    // ==========================================================

    private fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewCamara.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
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
        val imageCaptureActual = imageCapture ?: return
        val archivo = File(cacheDir, "frame_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()

        imageCaptureActual.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    lifecycleScope.launch {
                        try {
                            val response = pythonRepository.analizarFrame(
                                usuarioId,
                                rutaId,
                                archivo
                            )

                            if (response.isSuccessful && response.body()?.ok == true) {
                                val body = response.body()!!

                                val fatiga = body.fatiga ?: 0
                                val estado = body.estado ?: "Normal"
                                val nivel = body.nivel ?: "bajo"

                                if (
                                    fatiga >= 75 ||
                                    nivel.equals("alto", ignoreCase = true) ||
                                    estado.equals("OJOS_CERRADOS", ignoreCase = true) ||
                                    estado.equals("SOMNOLENCIA", ignoreCase = true)
                                ) {
                                    bleManager.enviarComando("FORZAR_NIVEL_3")
                                }

                                txtPorcentajeFatiga.text = "Fatiga: $fatiga%"
                                txtEstadoConductor.text = "Estado: $estado"

                                txtNivelAlerta.text = when {
                                    fatiga >= 70 -> "Alto"
                                    fatiga >= 50 -> "Medio"
                                    else -> nivel.replaceFirstChar { it.uppercase() }
                                }
                            }

                            archivo.delete()
                        } catch (e: Exception) {
                            archivo.delete()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    notificationHelper.mostrarNotificacion(
                        "Error cámara",
                        "No se pudo capturar el frame."
                    )
                }
            }
        )
    }

    // ==========================================================
    //  GESTIÓN DEL VIAJE
    // ==========================================================

    private fun iniciarViaje() {
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
                "Usa el botón Reanudar viaje."
            )
            return
        }

        lifecycleScope.launch {
            try {
                val response = pythonRepository.iniciarViaje(usuarioId, rutaId, nombreRuta)

                if (response.isSuccessful && response.body()?.ok == true) {
                    estadoViaje = EstadoViaje.ACTIVO
                    monitoreoActivo = true

                    sessionManager.guardarEstadoViaje("ACTIVO")
                    actualizarUIEstado()

                    notificationHelper.mostrarNotificacion(
                        "Viaje iniciado",
                        "El monitoreo de $nombreRuta ha comenzado."
                    )

                    iniciarEnvioFrames()
                    obtenerAlertasRuta()
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo iniciar el viaje."
                    )
                }
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "Error al iniciar el viaje."
                )
            }
        }
    }

    private fun pausarViaje() {
        bleManager.enviarComando("VIAJE_PAUSAR")
        if (estadoViaje != EstadoViaje.ACTIVO) {
            notificationHelper.mostrarNotificacion(
                "Acción no permitida",
                "No puedes pausar si no hay un viaje activo."
            )
            return
        }

        lifecycleScope.launch {
            try {
                monitoreoActivo = false

                val response = pythonRepository.pausarViaje()

                if (response.isSuccessful && response.body()?.ok == true) {
                    estadoViaje = EstadoViaje.PAUSADO

                    sessionManager.guardarEstadoViaje("PAUSADO")
                    actualizarUIEstado()

                    notificationHelper.mostrarNotificacion(
                        "Viaje pausado",
                        "El monitoreo se pausó correctamente."
                    )

                    obtenerAlertasRuta()
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo pausar el viaje."
                    )
                }
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "Error al pausar el viaje."
                )
            }
        }
    }

    private fun reanudarViaje() {
        bleManager.enviarComando("VIAJE_REANUDAR")
        if (estadoViaje != EstadoViaje.PAUSADO) {
            notificationHelper.mostrarNotificacion(
                "Acción no permitida",
                "Solo puedes reanudar un viaje pausado."
            )
            return
        }

        lifecycleScope.launch {
            try {
                val response = pythonRepository.iniciarViaje(usuarioId, rutaId, nombreRuta)

                if (response.isSuccessful && response.body()?.ok == true) {
                    estadoViaje = EstadoViaje.ACTIVO
                    monitoreoActivo = true

                    sessionManager.guardarEstadoViaje("ACTIVO")
                    actualizarUIEstado()

                    notificationHelper.mostrarNotificacion(
                        "Viaje reanudado",
                        "El monitoreo volvió a estar activo."
                    )

                    iniciarEnvioFrames()
                    obtenerAlertasRuta()
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo reanudar el viaje."
                    )
                }
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "Error al reanudar el viaje."
                )
            }
        }
    }

    private fun terminarViaje() {
        bleManager.enviarComando("VIAJE_TERMINAR")
        if (estadoViaje == EstadoViaje.INACTIVO) {
            notificationHelper.mostrarNotificacion(
                "Acción no permitida",
                "No hay un viaje activo para terminar."
            )
            return
        }

        lifecycleScope.launch {
            try {
                monitoreoActivo = false

                val response = pythonRepository.terminarViaje(usuarioId, rutaId)

                if (response.isSuccessful && response.body()?.ok == true) {
                    estadoViaje = EstadoViaje.INACTIVO

                    sessionManager.limpiarViajeActivo()
                    actualizarUIEstado()

                    notificationHelper.mostrarNotificacion(
                        "Viaje terminado",
                        "El viaje se terminó correctamente."
                    )

                    finish()
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo terminar el viaje."
                    )
                }
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "Error al terminar el viaje."
                )
            }
        }
    }

    private fun apagarAlarma() {
        bleManager.enviarComando("STOP_ALERT")
        lifecycleScope.launch {
            try {
                val response = pythonRepository.apagarAlarma(usuarioId, rutaId)

                if (response.isSuccessful && response.body()?.ok == true) {
                    notificationHelper.mostrarNotificacion(
                        "Alarma apagada",
                        "La alarma fue apagada correctamente."
                    )
                } else {
                    notificationHelper.mostrarNotificacion(
                        "SOMNIX",
                        "No hay alarmas pendientes."
                    )
                }
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "Error al apagar alarma."
                )
            }
        }
    }

    private fun pausarPorNecesidad(tipo: String, mensaje: String) {
        if (estadoViaje != EstadoViaje.ACTIVO) {
            notificationHelper.mostrarNotificacion(
                "Acción no permitida",
                "Solo puedes registrar una necesidad durante un viaje activo."
            )
            return
        }

        lifecycleScope.launch {
            try {
                monitoreoActivo = false

                pythonRepository.pausarViaje()

                val response = pythonRepository.registrarNecesidad(
                    usuarioId,
                    rutaId,
                    tipo,
                    mensaje
                )

                if (response.isSuccessful && response.body()?.ok == true) {
                    estadoViaje = EstadoViaje.PAUSADO

                    sessionManager.guardarEstadoViaje("PAUSADO")
                    actualizarUIEstado()

                    notificationHelper.mostrarNotificacion(
                        "Viaje pausado",
                        mensaje
                    )

                    obtenerAlertasRuta()
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo registrar la necesidad."
                    )
                }
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo pausar el viaje correctamente."
                )
            }
        }
    }

    // ==========================================================
    //  ALERTAS DE RUTA
    // ==========================================================

    private fun obtenerAlertasRuta() {
        lifecycleScope.launch {
            try {
                val response = rutaRepository.obtenerAlertasPorRuta(rutaId)

                if (response.isSuccessful && response.body() != null) {
                    val alertas = response.body()!!

                    txtUltimasAlertas.text =
                        if (alertas.isEmpty()) {
                            "No hay alertas recientes"
                        } else {
                            alertas.take(3).joinToString("\n\n") {
                                "• ${it.nivel.uppercase()} - ${it.mensaje}"
                            }
                        }
                } else {
                    txtUltimasAlertas.text = "No se pudieron cargar las alertas"
                }
            } catch (e: Exception) {
                txtUltimasAlertas.text = "Error al cargar alertas"
            }
        }
    }

    // ==========================================================
    //  BLE (GORRA SOMNIX)
    // ==========================================================

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneoBle() {
        if (escaneandoBle) return

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            notificationHelper.mostrarNotificacion(
                "Bluetooth apagado",
                "Activa Bluetooth para conectar la gorra SOMNIX."
            )
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner

        escaneandoBle = true

        notificationHelper.mostrarNotificacion(
            "BLE SOMNIX",
            "Buscando gorra..."
        )

        scanner.startScan(scanCallbackBle)

        lifecycleScope.launch {
            delay(10000)

            if (escaneandoBle) {
                detenerEscaneoBle()
                notificationHelper.mostrarNotificacion(
                    "BLE SOMNIX",
                    "No se encontró la gorra."
                )
            }
        }
    }

    private val scanCallbackBle = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device
            val nombre = device.name ?: result.scanRecord?.deviceName ?: ""

            if (nombre.contains(nombreGorraBle, ignoreCase = true)) {
                detenerEscaneoBle()

                notificationHelper.mostrarNotificacion(
                    "BLE SOMNIX",
                    "Gorra encontrada: $nombre"
                )

                bleManager.conectar(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            escaneandoBle = false

            notificationHelper.mostrarNotificacion(
                "BLE SOMNIX",
                "Error al escanear BLE: $errorCode"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneoBle() {
        if (!tienePermisoScan()) return

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter ?: return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return

        scanner.stopScan(scanCallbackBle)
        escaneandoBle = false
    }

    private fun tienePermisoScan(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}