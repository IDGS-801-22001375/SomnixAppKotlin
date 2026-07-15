package com.example.somnixapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.somnixapp.ble.SomnixBleManager

@SuppressLint("MissingPermission")
class ConfigurarGorra : AppCompatActivity() {

    private lateinit var txtEstadoBle: TextView
    private lateinit var btnBuscarGorra: Button
    private lateinit var btnDetenerBusqueda: Button
    private lateinit var listaDispositivos: ListView

    private lateinit var bleManager: SomnixBleManager

    private var shouldBeConnected = false
    private var escaneando = false
    private var gorraLista = false
    private var destruccionActivity = false

    private val nombreGorra = "SOMNIX_IDGS901"

    private val dispositivos = mutableListOf<BluetoothDevice>()
    private val nombres = mutableListOf<String>()

    private lateinit var adapter: ArrayAdapter<String>

    private val handler = Handler(Looper.getMainLooper())

    private val permisosLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permisos ->
            val concedidos = permisos.values.all { it }

            if (concedidos) {
                iniciarEscaneoBLE()
            } else {
                txtEstadoBle.text =
                    "Debes permitir Bluetooth para buscar la gorra."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configurar_gorra)

        inicializarVistas()
        inicializarBleManager()
        configurarEventos()
    }

    private fun inicializarVistas() {
        txtEstadoBle = findViewById(R.id.txtEstadoBle)
        btnBuscarGorra = findViewById(R.id.btnBuscarGorra)
        btnDetenerBusqueda = findViewById(R.id.btnDetenerBusqueda)
        listaDispositivos = findViewById(R.id.listaDispositivos)

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            nombres
        )

        listaDispositivos.adapter = adapter
    }

    private fun inicializarBleManager() {
        bleManager = SomnixBleManager(
            context = applicationContext,

            /*
             * Los estados BLE únicamente se muestran en el TextView.
             * No se genera una notificación ni un Toast por cada comando.
             */
            onEstado = { estado ->
                runOnUiThread {
                    txtEstadoBle.text = estado
                }
            },

            /*
             * Las respuestas y telemetría de la gorra no se muestran
             * como notificaciones.
             */
            onMensaje = {
                // Intencionalmente vacío.
            },

            onConectado = {
                runOnUiThread {
                    txtEstadoBle.text =
                        "Gorra conectada. Preparando comunicación..."
                }
            },

            onListo = {
                gorraLista = true
                escaneando = false

                runOnUiThread {
                    txtEstadoBle.text =
                        "Gorra SOMNIX conectada y lista"
                    btnBuscarGorra.text = "Gorra conectada"

                    Toast.makeText(
                        this,
                        "Gorra conectada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },

            onDesconectado = {
                gorraLista = false

                if (
                    shouldBeConnected &&
                    !destruccionActivity
                ) {
                    runOnUiThread {
                        txtEstadoBle.text =
                            "Gorra perdida. Reconectando..."
                    }

                    handler.postDelayed(
                        {
                            if (
                                shouldBeConnected &&
                                !gorraLista &&
                                !destruccionActivity
                            ) {
                                iniciarEscaneoBLE()
                            }
                        },
                        1000L
                    )
                } else {
                    runOnUiThread {
                        txtEstadoBle.text = "Desconectado"
                        btnBuscarGorra.text = "Buscar gorra"
                    }
                }
            }
        )
    }

    private fun configurarEventos() {
        btnBuscarGorra.setOnClickListener {
            if (gorraLista) {
                Toast.makeText(
                    this,
                    "La gorra ya está conectada",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                solicitarPermisos()
            }
        }

        btnDetenerBusqueda.setOnClickListener {
            desconectarBLE()
        }

        listaDispositivos.setOnItemClickListener { _, _, position, _ ->
            if (position !in dispositivos.indices) {
                return@setOnItemClickListener
            }

            val device = dispositivos[position]

            detenerEscaneo()

            shouldBeConnected = true
            gorraLista = false

            txtEstadoBle.text =
                "Conectando a ${device.name ?: "dispositivo"}..."

            bleManager.conectar(device)
        }
    }

    private fun solicitarPermisos() {
        val permisos = mutableListOf<String>()

        permisos.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permisos.add(Manifest.permission.BLUETOOTH_SCAN)
            permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permisos.add(Manifest.permission.BLUETOOTH)
            permisos.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val faltan = permisos.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (faltan.isNotEmpty()) {
            permisosLauncher.launch(faltan.toTypedArray())
        } else {
            iniciarEscaneoBLE()
        }
    }

    private fun iniciarEscaneoBLE() {
        if (destruccionActivity || escaneando || gorraLista) {
            return
        }

        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            txtEstadoBle.text =
                "Este celular no tiene Bluetooth."
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            txtEstadoBle.text =
                "Bluetooth apagado. Actívalo para continuar."
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            txtEstadoBle.text =
                "No se pudo iniciar el escáner Bluetooth."
            return
        }

        shouldBeConnected = true
        escaneando = true
        gorraLista = false

        dispositivos.clear()
        nombres.clear()
        adapter.notifyDataSetChanged()

        txtEstadoBle.text = "Buscando gorra SOMNIX..."

        scanner.startScan(scanCallback)
    }

    private fun detenerEscaneo() {
        if (!escaneando) return

        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter = bluetoothManager.adapter

        try {
            bluetoothAdapter
                ?.bluetoothLeScanner
                ?.stopScan(scanCallback)
        } catch (_: Exception) {
        }

        escaneando = false
    }

    private fun desconectarBLE() {
        shouldBeConnected = false
        gorraLista = false

        handler.removeCallbacksAndMessages(null)

        detenerEscaneo()
        bleManager.desconectar()

        txtEstadoBle.text = "Desconectado"
        btnBuscarGorra.text = "Buscar gorra"
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device

            val nombre =
                device.name
                    ?: result.scanRecord?.deviceName
                    ?: "Sin nombre"

            val mac = device.address ?: "Sin dirección"

            runOnUiThread {
                if (
                    dispositivos.none {
                        it.address == device.address
                    }
                ) {
                    dispositivos.add(device)
                    nombres.add("$nombre\n$mac")
                    adapter.notifyDataSetChanged()
                }
            }

            if (nombre == nombreGorra) {
                detenerEscaneo()

                shouldBeConnected = true
                gorraLista = false

                runOnUiThread {
                    txtEstadoBle.text =
                        "Gorra encontrada. Conectando..."
                }

                bleManager.conectar(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            escaneando = false

            runOnUiThread {
                txtEstadoBle.text =
                    "Error al buscar dispositivos BLE: $errorCode"
            }

            if (
                shouldBeConnected &&
                !gorraLista &&
                !destruccionActivity
            ) {
                handler.postDelayed(
                    {
                        iniciarEscaneoBLE()
                    },
                    1500L
                )
            }
        }
    }

    override fun onDestroy() {
        destruccionActivity = true
        shouldBeConnected = false

        handler.removeCallbacksAndMessages(null)

        detenerEscaneo()
        bleManager.desconectar()

        super.onDestroy()
    }
}