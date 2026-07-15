package com.example.somnixapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class MonitoreoActivity : AppCompatActivity() {

    private enum class EstadoViaje {
        INACTIVO,
        ACTIVO,
        PAUSADO
    }

    companion object {
        /*
         * Intervalo corto para detectar rápidamente la inclinación,
         * ojos cerrados, cabeceo o somnolencia.
         */
        private const val INTERVALO_ANALISIS_CAMARA_MS = 700L

        private const val TIEMPO_RECONEXION_BLE_MS = 1000L

        /*
         * Se utiliza únicamente como protección para no repetir
         * FORZAR_NIVEL_3 en cada frame.
         */
        private const val FRAMES_SEGUROS_PARA_REARMAR = 2
    }

    private var estadoViaje = EstadoViaje.INACTIVO
    private var monitoreoActivo = false
    private var operacionViajeEnProceso = false

    /*
     * La gorra solamente puede detectar y activar alarmas después de
     * presionar "Iniciar viaje".
     */
    private var monitoreoGorraActivo = false

    // =========================================================
    // BLE
    // =========================================================

    private lateinit var bleManager: SomnixBleManager

    private var escaneandoBle = false
    private var shouldBeConnected = false
    private var gorraConectada = false
    private var gorraLista = false
    private var activityDestruida = false

    private val nombreGorraBle = "SOMNIX_IDGS901"
    private val handlerBle = Handler(Looper.getMainLooper())

    // =========================================================
    // CÁMARA Y ALARMA
    // =========================================================

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var envioFramesJob: Job? = null
    private var capturaEnProceso = false

    /*
     * Evita enviar FORZAR_NIVEL_3 continuamente mientras
     * la misma condición peligrosa permanece activa.
     */
    private var alarmaCamaraActiva = false

    /*
     * Cuando el usuario presiona "Apagar alarma", la alarma no vuelve
     * a sonar hasta que la cámara vea nuevamente una condición segura.
     */
    private var alarmaSilenciadaHastaSeguro = false

    private var framesSegurosConsecutivos = 0

    // =========================================================
    // REPOSITORIOS Y SESIÓN
    // =========================================================

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var sessionManager: SessionManager

    private val pythonRepository = PythonRepository()
    private val rutaRepository = RutaRepository()

    private lateinit var usuarioId: String
    private lateinit var rutaId: String
    private lateinit var nombreRuta: String

    // =========================================================
    // VISTAS
    // =========================================================

    private lateinit var btnBack: ImageView
    private lateinit var btnConfigurar: Button

    /*
     * Es nullable para que la Activity no falle si todavía no agregaste
     * btnCalibrarGorra al XML.
     */
    private var btnCalibrarGorra: Button? = null

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

    /*
     * Son nullable para mantener compatibilidad con el XML anterior.
     */
    private var txtEstadoGorra: TextView? = null
    private var indicadorEstadoGorra: View? = null

    private lateinit var chipDescansar: LinearLayout
    private lateinit var chipAgua: LinearLayout
    private lateinit var chipComer: LinearLayout
    private lateinit var chipEstirar: LinearLayout
    private lateinit var chipDormir: LinearLayout
    private lateinit var chipNoConducir: LinearLayout

    // =========================================================
    // PERMISOS
    // =========================================================

    private val permisosBleLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permisos ->
            val concedidos = permisos.values.all { it }

            if (concedidos) {
                iniciarEscaneoBle()
            } else {
                notificationHelper.mostrarNotificacion(
                    "Permisos Bluetooth",
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
                    "Permiso de cámara",
                    "Activa el permiso de cámara para utilizar el monitoreo."
                )
            }
        }

    private val permisoNotificacionesLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            /*
             * No se muestra una notificación indicando que las
             * notificaciones fueron activadas.
             */
        }

    // =========================================================
    // CICLO DE VIDA
    // =========================================================

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

        /*
         * Al abrir esta pantalla siempre se inicia en estado INACTIVO.
         *
         * Esto evita recuperar un viaje viejo guardado como ACTIVO o PAUSADO,
         * cuando el backend ya no tiene ese viaje ejecutándose.
         */
        estadoViaje = EstadoViaje.INACTIVO
        monitoreoActivo = false
        monitoreoGorraActivo = false

        sessionManager.guardarEstadoViaje("INACTIVO")

        cameraExecutor = Executors.newSingleThreadExecutor()

        inicializarVistas()
        inicializarBleManager()
        configurarClicks()
        configurarBack()

        txtRutaMonitoreo.text = nombreRuta

        actualizarUIEstado()
        actualizarEstadoGorra("Dispositivo desconectado", false)

        obtenerAlertasRuta()
        validarPermisosIniciales()

        /*
         * Se intenta conectar automáticamente a la gorra al entrar.
         */
        validarPermisosBle()
    }

    override fun onResume() {
        super.onResume()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCamara()
        }
    }

    override fun onDestroy() {
        activityDestruida = true

        monitoreoActivo = false
        shouldBeConnected = false
        gorraLista = false
        gorraConectada = false

        envioFramesJob?.cancel()
        envioFramesJob = null

        handlerBle.removeCallbacksAndMessages(null)

        if (tienePermisoScan()) {
            detenerEscaneoBle()
        }

        bleManager.desconectar()

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        super.onDestroy()
    }

    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    private fun inicializarVistas() {
        btnBack = findViewById(R.id.btnBack)
        btnConfigurar = findViewById(R.id.btnConfigurar)

        btnCalibrarGorra =
            findViewById<Button?>(R.id.btnCalibrarGorra)

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

        txtEstadoGorra =
            findViewById<TextView?>(R.id.txtEstadoGorra)

        indicadorEstadoGorra =
            findViewById<View?>(R.id.indicadorEstadoGorra)

        chipDescansar = findViewById(R.id.chipDescansar)
        chipAgua = findViewById(R.id.chipAgua)
        chipComer = findViewById(R.id.chipComer)
        chipEstirar = findViewById(R.id.chipEstirar)
        chipDormir = findViewById(R.id.chipDormir)
        chipNoConducir = findViewById(R.id.chipNoConducir)
    }

    private fun inicializarBleManager() {
        bleManager = SomnixBleManager(
            context = applicationContext,

            /*
             * Los estados BLE se muestran únicamente dentro de la pantalla.
             * No generan notificaciones Android.
             */
            onEstado = { estado ->
                runOnUiThread {
                    actualizarEstadoGorra(
                        mensaje = estado,
                        conectado = gorraLista
                    )
                }
            },

            /*
             * Las respuestas de la gorra no generan notificaciones.
             * Aquí pueden recibirse JSON, ACK, SYNC o telemetría.
             */
            onMensaje = {
                // Intencionalmente vacío.
            },

            onConectado = {
                gorraConectada = true
                gorraLista = false
                escaneandoBle = false

                runOnUiThread {
                    btnConfigurar.text =
                        "Desconectar dispositivo"

                    actualizarEstadoGorra(
                        "Gorra conectada. Preparando comunicación...",
                        false
                    )
                }
            },

            onListo = {
                gorraConectada = true
                gorraLista = true
                escaneandoBle = false

                runOnUiThread {
                    btnConfigurar.text = "Desconectar dispositivo"

                    actualizarEstadoGorra(
                        "Gorra conectada y lista",
                        true
                    )

                    actualizarBotonesViaje()
                }

                /*
                 * Al conectar la gorra NO debe comenzar el monitoreo.
                 *
                 * Se apaga cualquier alarma anterior y se coloca la gorra
                 * en estado de viaje terminado.
                 */
                bleManager.enviarSecuencia(
                    comandos = listOf(
                        "APAGAR",
                        "VIAJE_TERMINAR"
                    ),
                    intervaloMs = 400L
                )

                monitoreoGorraActivo = false
            },

            onDesconectado = {
                gorraConectada = false
                gorraLista = false

                runOnUiThread {
                    actualizarEstadoGorra(
                        "Gorra perdida. Reconectando...",
                        false
                    )
                }

                if (
                    shouldBeConnected &&
                    !activityDestruida
                ) {
                    handlerBle.postDelayed(
                        {
                            if (
                                shouldBeConnected &&
                                !gorraLista &&
                                !activityDestruida
                            ) {
                                iniciarEscaneoBle()
                            }
                        },
                        TIEMPO_RECONEXION_BLE_MS
                    )
                } else {
                    runOnUiThread {
                        btnConfigurar.text =
                            "Conectar dispositivo"
                    }
                }
            }
        )
    }

    // =========================================================
    // EVENTOS
    // =========================================================

    private fun configurarClicks() {
        btnBack.setOnClickListener {
            intentarSalir()
        }

        btnConfigurar.setOnClickListener {
            if (
                shouldBeConnected ||
                gorraConectada ||
                gorraLista
            ) {
                desconectarGorraManualmente()
            } else {
                validarPermisosBle()
            }
        }

        btnCalibrarGorra?.setOnClickListener {
            calibrarGorraManualmente()
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

        btnApagarAlarma.setOnClickListener {
            apagarAlarma()
        }

        chipDescansar.setOnClickListener {
            pausarPorNecesidad(
                tipo = "necesito_descansar",
                mensaje =
                    "El viaje se pausó porque necesitas descansar."
            )
        }

        chipAgua.setOnClickListener {
            pausarPorNecesidad(
                tipo = "necesito_hidratarme",
                mensaje =
                    "El viaje se pausó porque necesitas hidratarte."
            )
        }

        chipComer.setOnClickListener {
            pausarPorNecesidad(
                tipo = "necesito_comer",
                mensaje =
                    "El viaje se pausó porque necesitas comer algo."
            )
        }

        chipEstirar.setOnClickListener {
            pausarPorNecesidad(
                tipo = "necesito_estirar",
                mensaje =
                    "El viaje se pausó porque necesitas estirar."
            )
        }

        chipDormir.setOnClickListener {
            pausarPorNecesidad(
                tipo = "necesito_dormir",
                mensaje =
                    "El viaje se pausó porque necesitas dormir."
            )
        }

        chipNoConducir.setOnClickListener {
            pausarPorNecesidad(
                tipo = "necesito_dejar_de_manejar",
                mensaje =
                    "El viaje se pausó porque necesitas dejar de manejar."
            )
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

    // =========================================================
    // PERMISOS
    // =========================================================

    private fun validarPermisosIniciales() {
        val permisoCamara =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            )

        if (permisoCamara == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            permisoCamaraLauncher.launch(
                Manifest.permission.CAMERA
            )
        }

        validarPermisoNotificaciones()
    }

    private fun validarPermisoNotificaciones() {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            val permiso =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                )

            if (permiso != PackageManager.PERMISSION_GRANTED) {
                permisoNotificacionesLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun validarPermisosBle() {
        val permisosNecesarios =
            mutableListOf<String>()

        permisosNecesarios.add(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permisosNecesarios.add(
                Manifest.permission.BLUETOOTH_SCAN
            )

            permisosNecesarios.add(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            permisosNecesarios.add(
                Manifest.permission.BLUETOOTH
            )

            permisosNecesarios.add(
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val faltanPermisos =
            permisosNecesarios.any {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (faltanPermisos) {
            permisosBleLauncher.launch(
                permisosNecesarios.toTypedArray()
            )
        } else {
            iniciarEscaneoBle()
        }
    }

    // =========================================================
    // CÁMARA
    // =========================================================

    private fun iniciarCamara() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
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
                                ImageCapture
                                    .CAPTURE_MODE_MINIMIZE_LATENCY
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
                        "No se pudo iniciar la cámara frontal."
                    )
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun iniciarEnvioFrames() {
        envioFramesJob?.cancel()

        envioFramesJob = lifecycleScope.launch {
            while (monitoreoActivo) {
                if (!capturaEnProceso) {
                    capturarYEnviarFrame()
                }

                delay(INTERVALO_ANALISIS_CAMARA_MS)
            }
        }
    }

    private fun detenerEnvioFrames() {
        monitoreoActivo = false

        envioFramesJob?.cancel()
        envioFramesJob = null

        capturaEnProceso = false
    }

    private fun capturarYEnviarFrame() {
        if (
            !monitoreoActivo ||
            estadoViaje != EstadoViaje.ACTIVO ||
            capturaEnProceso
        ) {
            return
        }

        val imageCaptureActual = imageCapture ?: return

        capturaEnProceso = true

        val archivo = File(
            cacheDir,
            "frame_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(archivo)
                .build()

        imageCaptureActual.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {
                    lifecycleScope.launch {
                        try {
                            if (
                                !monitoreoActivo ||
                                estadoViaje != EstadoViaje.ACTIVO
                            ) {
                                return@launch
                            }

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

                                actualizarDatosCamara(
                                    fatiga = fatiga,
                                    estado = estado,
                                    nivel = nivel
                                )

                                procesarDeteccionInmediata(
                                    fatiga = fatiga,
                                    estado = estado,
                                    nivel = nivel
                                )
                            }
                        } catch (_: Exception) {
                            /*
                             * No se genera una notificación por cada frame
                             * que falla para no saturar al conductor.
                             */
                        } finally {
                            archivo.delete()
                            capturaEnProceso = false
                        }
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    capturaEnProceso = false
                    archivo.delete()

                    /*
                     * Tampoco se genera una notificación por cada error
                     * temporal de captura.
                     */
                }
            }
        )
    }

    private fun actualizarDatosCamara(
        fatiga: Int,
        estado: String,
        nivel: String
    ) {
        txtPorcentajeFatiga.text =
            "Fatiga: $fatiga%"

        txtEstadoConductor.text =
            "Estado: $estado"

        txtNivelAlerta.text =
            when {
                esDeteccionPeligrosa(
                    fatiga,
                    estado,
                    nivel
                ) -> "Alerta"

                nivel.equals(
                    "medio",
                    ignoreCase = true
                ) -> "Medio"

                else -> nivel.replaceFirstChar {
                    it.uppercase()
                }
            }
    }

    /**
     * Ya no espera a fatiga 75.
     *
     * La alarma se activa desde la primera respuesta del backend que indique:
     *
     * - Fatiga mayor que cero.
     * - Cabeceo.
     * - Cabeza inclinada.
     * - Ojos cerrados.
     * - Somnolencia.
     * - Microsueño.
     * - Nivel medio, alto o crítico.
     */
    private fun esDeteccionPeligrosa(
        fatiga: Int,
        estado: String,
        nivel: String
    ): Boolean {
        val estadoNormalizado =
            estado
                .trim()
                .uppercase(Locale.ROOT)
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("Ñ", "N")

        val nivelNormalizado =
            nivel
                .trim()
                .uppercase(Locale.ROOT)
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")

        val estadoDetectado =
            estadoNormalizado.contains("CABECEO") ||
                    estadoNormalizado.contains("CABEZA") ||
                    estadoNormalizado.contains("INCLIN") ||
                    estadoNormalizado.contains("OJOS_CERRADOS") ||
                    estadoNormalizado.contains("OJOS CERRADOS") ||
                    estadoNormalizado.contains("SOMNOLENCIA") ||
                    estadoNormalizado.contains("MICROSUENO") ||
                    estadoNormalizado.contains("FATIGA") ||
                    estadoNormalizado.contains("DORMIDO") ||
                    estadoNormalizado.contains("ALERTA")

        val nivelDetectado =
            nivelNormalizado == "MEDIO" ||
                    nivelNormalizado == "ALTO" ||
                    nivelNormalizado == "CRITICO" ||
                    nivelNormalizado == "PELIGRO"

        /*
         * fatiga > 0 significa que no se espera a 75, 70, 50, etc.
         */
        return fatiga > 0 ||
                estadoDetectado ||
                nivelDetectado
    }

    private fun procesarDeteccionInmediata(
        fatiga: Int,
        estado: String,
        nivel: String
    ) {
        val peligro =
            esDeteccionPeligrosa(
                fatiga,
                estado,
                nivel
            )

        if (peligro) {
            framesSegurosConsecutivos = 0

            if (
                !alarmaCamaraActiva &&
                !alarmaSilenciadaHastaSeguro
            ) {
                alarmaCamaraActiva = true

                /*
                 * La cámara solamente puede activar la alarma física cuando:
                 *
                 * 1. Existe un viaje activo.
                 * 2. El monitoreo de cámara está activo.
                 * 3. El monitoreo de la gorra fue iniciado.
                 * 4. La gorra está conectada y lista.
                 */
                if (
                    estadoViaje == EstadoViaje.ACTIVO &&
                    monitoreoActivo &&
                    monitoreoGorraActivo &&
                    gorraLista
                ) {
                    bleManager.enviarComando("FORZAR_NIVEL_3")
                }

                /*
                 * Esta sí es una notificación útil:
                 * una alerta real de seguridad.
                 */
                notificationHelper.mostrarNotificacion(
                    "Alerta de somnolencia",
                    "Se detectó $estado. Corrige tu postura y mantente alerta."
                )

                txtUltimasAlertas.text =
                    "• ALERTA - Se detectó $estado"
            }

            return
        }

        /*
         * Cuando vuelve a estar seguro durante varios análisis,
         * se permite que una futura detección vuelva a activar la alarma.
         */
        framesSegurosConsecutivos++

        if (
            framesSegurosConsecutivos >=
            FRAMES_SEGUROS_PARA_REARMAR
        ) {
            alarmaCamaraActiva = false
            alarmaSilenciadaHastaSeguro = false
            framesSegurosConsecutivos = 0
        }
    }

    // =========================================================
    // VIAJE
    // =========================================================

    private fun mostrarDialogoCalibracion() {
        when (estadoViaje) {
            EstadoViaje.ACTIVO -> {
                notificationHelper.mostrarNotificacion(
                    "Viaje activo",
                    "Ya tienes un viaje en curso."
                )
                return
            }

            EstadoViaje.PAUSADO -> {
                notificationHelper.mostrarNotificacion(
                    "Viaje pausado",
                    "Utiliza el botón Reanudar."
                )
                return
            }

            EstadoViaje.INACTIVO -> Unit
        }

        AlertDialog.Builder(this)
            .setTitle("Calibración SOMNIX")
            .setMessage(
                "Acomódate la gorra, siéntate derecho y mira al frente. " +
                        "Al confirmar se iniciarán la cámara y la gorra."
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
                        "No se pudo iniciar el monitoreo de cámara."
                    )
                    return@launch
                }

                estadoViaje = EstadoViaje.ACTIVO
                monitoreoActivo = true
                monitoreoGorraActivo = true

                alarmaCamaraActiva = false
                alarmaSilenciadaHastaSeguro = false
                framesSegurosConsecutivos = 0

                sessionManager.guardarEstadoViaje("ACTIVO")

                actualizarUIEstado()

                /*
                 * Misma secuencia que el código funcional:
                 * CALIBRAR y después VIAJE_INICIAR.
                 */
                if (gorraLista) {
                    /*
                     * Primero establece la posición actual como posición normal.
                     * Después comienza el monitoreo físico de la gorra.
                     */
                    bleManager.enviarSecuencia(
                        comandos = listOf(
                            "APAGAR",
                            "CALIBRAR",
                            "VIAJE_INICIAR"
                        ),
                        intervaloMs = 600L
                    )
                } else {
                    monitoreoGorraActivo = false

                    if (!shouldBeConnected) {
                        validarPermisosBle()
                    }

                    notificationHelper.mostrarNotificacion(
                        "Gorra no conectada",
                        "La cámara inició, pero falta conectar la gorra."
                    )
                }

                iniciarEnvioFrames()
                obtenerAlertasRuta()

                notificationHelper.mostrarNotificacion(
                    "Viaje iniciado",
                    if (gorraLista) {
                        "La cámara y la gorra comenzaron el monitoreo."
                    } else {
                        "La cámara comenzó el monitoreo."
                    }
                )
            } catch (e: Exception) {
                detenerEnvioFrames()

                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo iniciar el viaje: " +
                            (e.message ?: "error de conexión")
                )
            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
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

                detenerEnvioFrames()
                monitoreoGorraActivo = false

                if (gorraLista) {
                    bleManager.enviarSecuencia(
                        comandos = listOf(
                            "APAGAR",
                            "VIAJE_PAUSAR"
                        ),
                        intervaloMs = 300L
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
                    "El monitoreo fue pausado."
                )
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo pausar el viaje: " +
                            (e.message ?: "error de conexión")
                )
            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
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
        actualizarBotonesViaje()

        lifecycleScope.launch {
            try {
                /*
                 * Tu repositorio actual utiliza iniciarViaje para reanudar.
                 */
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
                monitoreoGorraActivo = true

                alarmaCamaraActiva = false
                alarmaSilenciadaHastaSeguro = false
                framesSegurosConsecutivos = 0

                sessionManager.guardarEstadoViaje(
                    "ACTIVO"
                )

                actualizarUIEstado()

                /*
                 * Reanudar también vuelve a calibrar, como el código
                 * funcional que enviaste.
                 */
                if (gorraLista) {
                    bleManager.enviarSecuencia(
                        comandos = listOf(
                            "APAGAR",
                            "CALIBRAR",
                            "VIAJE_REANUDAR"
                        ),
                        intervaloMs = 600L
                    )
                } else {
                    monitoreoGorraActivo = false

                    if (!shouldBeConnected) {
                        validarPermisosBle()
                    }
                }

                iniciarEnvioFrames()
                obtenerAlertasRuta()

                notificationHelper.mostrarNotificacion(
                    "Viaje reanudado",
                    "El monitoreo fue reanudado."
                )
            } catch (e: Exception) {
                detenerEnvioFrames()

                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo reanudar el viaje: " +
                            (e.message ?: "error de conexión")
                )
            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
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
        actualizarBotonesViaje()

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

                detenerEnvioFrames()
                monitoreoGorraActivo = false

                if (gorraLista) {
                    bleManager.enviarSecuencia(
                        comandos = listOf(
                            "APAGAR",
                            "VIAJE_TERMINAR"
                        ),
                        intervaloMs = 300L
                    )
                }

                estadoViaje = EstadoViaje.INACTIVO

                alarmaCamaraActiva = false
                alarmaSilenciadaHastaSeguro = false
                framesSegurosConsecutivos = 0

                sessionManager.limpiarViajeActivo()

                actualizarUIEstado()
                obtenerAlertasRuta()

                notificationHelper.mostrarNotificacion(
                    "Viaje terminado",
                    "La cámara y la gorra finalizaron el monitoreo."
                )
            } catch (e: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo terminar el viaje: " +
                            (e.message ?: "error de conexión")
                )
            } finally {
                operacionViajeEnProceso = false
                actualizarBotonesViaje()
            }
        }
    }

    private fun apagarAlarma() {
        lifecycleScope.launch {
            var camaraApagada = false

            /*
             * Impide que la alarma vuelva a encenderse inmediatamente
             * mientras la cabeza continúa agachada.
             */
            alarmaCamaraActiva = false
            alarmaSilenciadaHastaSeguro = true
            framesSegurosConsecutivos = 0

            try {
                val response =
                    pythonRepository.apagarAlarma(
                        usuarioId = usuarioId,
                        rutaId = rutaId
                    )

                camaraApagada =
                    response.isSuccessful &&
                            response.body()?.ok == true
            } catch (_: Exception) {
                camaraApagada = false
            }

            val comandoGorraEnviado =
                if (gorraLista) {
                    bleManager.enviarComando("APAGAR")
                } else {
                    false
                }

            when {
                camaraApagada && comandoGorraEnviado -> {
                    notificationHelper.mostrarNotificacion(
                        "Alarma apagada",
                        "Se apagaron las alarmas de cámara y gorra."
                    )
                }

                camaraApagada -> {
                    notificationHelper.mostrarNotificacion(
                        "Alarma apagada",
                        "Se apagó la alarma de cámara."
                    )
                }

                comandoGorraEnviado -> {
                    notificationHelper.mostrarNotificacion(
                        "Alarma apagada",
                        "Se envió el apagado a la gorra."
                    )
                }

                else -> {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo apagar la alarma."
                    )
                }
            }
        }
    }

    private fun calibrarGorraManualmente() {
        if (!gorraLista) {
            notificationHelper.mostrarNotificacion(
                "Gorra no conectada",
                "Conecta la gorra antes de calibrarla."
            )
            return
        }

        val enviado =
            bleManager.enviarComando("CALIBRAR")

        if (enviado) {
            notificationHelper.mostrarNotificacion(
                "Gorra calibrada",
                "La posición actual se estableció como referencia."
            )
        }
    }

    private fun sincronizarEstadoViajeConGorra() {
        if (!gorraLista) return

        /*
         * Solo se recupera el monitoreo cuando el viaje está realmente
         * activo dentro de esta misma ejecución de la pantalla.
         */
        when {
            estadoViaje == EstadoViaje.ACTIVO && monitoreoGorraActivo -> {
                bleManager.enviarSecuencia(
                    comandos = listOf(
                        "CALIBRAR",
                        "VIAJE_INICIAR"
                    ),
                    intervaloMs = 600L
                )
            }

            estadoViaje == EstadoViaje.PAUSADO -> {
                bleManager.enviarComando("VIAJE_PAUSAR")
            }

            else -> {
                /*
                 * Conectada, pero sin viaje:
                 * no debe detectar inclinación ni activar alarmas.
                 */
                bleManager.enviarSecuencia(
                    comandos = listOf(
                        "APAGAR",
                        "VIAJE_TERMINAR"
                    ),
                    intervaloMs = 400L
                )

                monitoreoGorraActivo = false
            }
        }
    }

    // =========================================================
    // NECESIDADES
    // =========================================================

    private fun pausarPorNecesidad(
        tipo: String,
        mensaje: String
    ) {
        if (estadoViaje != EstadoViaje.ACTIVO) {
            notificationHelper.mostrarNotificacion(
                "Acción no permitida",
                "Solo puedes registrar una necesidad durante un viaje activo."
            )
            return
        }

        lifecycleScope.launch {
            try {
                val respuestaPausa =
                    pythonRepository.pausarViaje()

                if (
                    !respuestaPausa.isSuccessful ||
                    respuestaPausa.body()?.ok != true
                ) {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo pausar el viaje."
                    )
                    return@launch
                }

                val response =
                    pythonRepository.registrarNecesidad(
                        usuarioId,
                        rutaId,
                        tipo,
                        mensaje
                    )

                if (
                    response.isSuccessful &&
                    response.body()?.ok == true
                ) {
                    detenerEnvioFrames()

                    if (gorraLista) {
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
                        mensaje
                    )
                } else {
                    notificationHelper.mostrarNotificacion(
                        "Error SOMNIX",
                        "No se pudo registrar la necesidad."
                    )
                }
            } catch (_: Exception) {
                notificationHelper.mostrarNotificacion(
                    "Error SOMNIX",
                    "No se pudo pausar el viaje."
                )
            }
        }
    }

    // =========================================================
    // UI
    // =========================================================

    private fun actualizarUIEstado() {
        txtEstadoMonitoreo.text =
            when (estadoViaje) {
                EstadoViaje.INACTIVO -> "Inactivo"
                EstadoViaje.ACTIVO -> "Activo"
                EstadoViaje.PAUSADO -> "Pausado"
            }

        actualizarBotonesViaje()
    }

    private fun actualizarBotonesViaje() {
        val disponible = !operacionViajeEnProceso

        when (estadoViaje) {
            EstadoViaje.INACTIVO -> {
                btnIniciarViaje.visibility = View.VISIBLE
                btnPausarViaje.visibility = View.GONE
                btnReanudarViaje.visibility = View.GONE
                btnTerminarViaje.visibility = View.GONE

                btnIniciarViaje.isEnabled = disponible
            }

            EstadoViaje.ACTIVO -> {
                btnIniciarViaje.visibility = View.GONE
                btnPausarViaje.visibility = View.VISIBLE
                btnReanudarViaje.visibility = View.GONE
                btnTerminarViaje.visibility = View.VISIBLE

                btnPausarViaje.isEnabled = disponible
                btnTerminarViaje.isEnabled = disponible
            }

            EstadoViaje.PAUSADO -> {
                btnIniciarViaje.visibility = View.GONE
                btnPausarViaje.visibility = View.GONE
                btnReanudarViaje.visibility = View.VISIBLE
                btnTerminarViaje.visibility = View.VISIBLE

                btnReanudarViaje.isEnabled = disponible
                btnTerminarViaje.isEnabled = disponible
            }
        }

        btnConfigurar.isEnabled = disponible
        btnApagarAlarma.isEnabled = disponible

        /*
         * Solo se permite calibrar cuando la gorra está conectada
         * y todavía no ha comenzado un viaje.
         */
        btnCalibrarGorra?.isEnabled =
            disponible &&
                    gorraLista &&
                    estadoViaje == EstadoViaje.INACTIVO
    }

    private fun actualizarEstadoGorra(
        mensaje: String,
        conectado: Boolean
    ) {
        txtEstadoGorra?.text = mensaje

        indicadorEstadoGorra?.setBackgroundResource(
            if (conectado) {
                R.drawable.bg_status_circle_connected
            } else {
                R.drawable.bg_status_circle_disconnected
            }
        )
    }

    private fun desconectarGorraManualmente() {
        shouldBeConnected = false
        gorraConectada = false
        gorraLista = false

        handlerBle.removeCallbacksAndMessages(null)

        detenerEscaneoBle()
        bleManager.desconectar()

        btnConfigurar.text =
            "Conectar dispositivo"

        actualizarEstadoGorra(
            "Dispositivo desconectado",
            false
        )
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
    // ALERTAS DE RUTA
    // =========================================================

    private fun obtenerAlertasRuta() {
        lifecycleScope.launch {
            try {
                val response =
                    rutaRepository.obtenerAlertasPorRuta(
                        rutaId
                    )

                if (
                    response.isSuccessful &&
                    response.body() != null
                ) {
                    val alertas = response.body()!!

                    txtUltimasAlertas.text =
                        if (alertas.isEmpty()) {
                            "No hay alertas recientes"
                        } else {
                            alertas
                                .take(3)
                                .joinToString("\n\n") {
                                    "• ${it.nivel.uppercase()} - ${it.mensaje}"
                                }
                        }
                } else {
                    txtUltimasAlertas.text =
                        "No se pudieron cargar las alertas"
                }
            } catch (_: Exception) {
                txtUltimasAlertas.text =
                    "Error al cargar alertas"
            }
        }
    }

    // =========================================================
    // ESCANEO BLE
    // =========================================================

    private fun iniciarEscaneoBle() {
        if (
            activityDestruida ||
            escaneandoBle ||
            gorraLista
        ) {
            return
        }

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val bluetoothAdapter =
            bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            actualizarEstadoGorra(
                "El celular no tiene Bluetooth",
                false
            )
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            actualizarEstadoGorra(
                "Bluetooth apagado",
                false
            )

            notificationHelper.mostrarNotificacion(
                "Bluetooth apagado",
                "Activa Bluetooth para conectar la gorra."
            )
            return
        }

        val scanner =
            bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            actualizarEstadoGorra(
                "No se pudo iniciar el escaneo",
                false
            )
            return
        }

        shouldBeConnected = true
        escaneandoBle = true
        gorraConectada = false
        gorraLista = false

        actualizarEstadoGorra(
            "Buscando gorra SOMNIX...",
            false
        )

        /*
         * No se genera una notificación por iniciar el escaneo.
         */
        scanner.startScan(scanCallbackBle)
    }

    private val scanCallbackBle =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val device = result.device

                val nombre =
                    device.name
                        ?: result.scanRecord?.deviceName
                        ?: ""

                if (nombre == nombreGorraBle) {
                    detenerEscaneoBle()

                    actualizarEstadoGorra(
                        "Gorra encontrada. Conectando...",
                        false
                    )

                    /*
                     * No se genera una notificación de
                     * "Gorra encontrada".
                     */
                    bleManager.conectar(device)
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                escaneandoBle = false

                actualizarEstadoGorra(
                    "Error de escaneo BLE: $errorCode",
                    false
                )

                if (
                    shouldBeConnected &&
                    !gorraLista &&
                    !activityDestruida
                ) {
                    handlerBle.postDelayed(
                        {
                            iniciarEscaneoBle()
                        },
                        1500L
                    )
                }
            }
        }

    private fun detenerEscaneoBle() {
        if (!tienePermisoScan()) return

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val bluetoothAdapter =
            bluetoothManager.adapter ?: return

        val scanner =
            bluetoothAdapter.bluetoothLeScanner ?: return

        try {
            scanner.stopScan(scanCallbackBle)
        } catch (_: Exception) {
        }

        escaneandoBle = false
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