package com.example.somnixapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
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

    private val nombreGorra = "SOMNIX_IDGS901"

    private val dispositivos = mutableListOf<BluetoothDevice>()
    private val nombres = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private val permisosLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            iniciarEscaneoBLE()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configurar_gorra)

        txtEstadoBle = findViewById(R.id.txtEstadoBle)
        btnBuscarGorra = findViewById(R.id.btnBuscarGorra)
        btnDetenerBusqueda = findViewById(R.id.btnDetenerBusqueda)
        listaDispositivos = findViewById(R.id.listaDispositivos)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nombres)
        listaDispositivos.adapter = adapter

        bleManager = SomnixBleManager(
            context = this,
            onEstado = { estado ->
                runOnUiThread {
                    txtEstadoBle.text = estado
                    Toast.makeText(this, estado, Toast.LENGTH_SHORT).show()
                }
            },
            onMensaje = { mensaje ->
                runOnUiThread {
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                }
            },
            onDesconectado = {
                if (shouldBeConnected) {
                    runOnUiThread {
                        txtEstadoBle.text = "Gorra perdida. Reconectando..."
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        iniciarEscaneoBLE()
                    }, 1000)
                }
            }
        )

        btnBuscarGorra.setOnClickListener {
            solicitarPermisos()
        }

        btnDetenerBusqueda.setOnClickListener {
            desconectarBLE()
        }

        listaDispositivos.setOnItemClickListener { _, _, position, _ ->
            val device = dispositivos[position]
            detenerEscaneo()
            txtEstadoBle.text = "Conectando a ${device.name ?: "dispositivo"}..."
            shouldBeConnected = true
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
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltan.isNotEmpty()) {
            permisosLauncher.launch(faltan.toTypedArray())
        } else {
            iniciarEscaneoBLE()
        }
    }

    private fun iniciarEscaneoBLE() {
        shouldBeConnected = true

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            txtEstadoBle.text = "Este celular no tiene Bluetooth."
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            txtEstadoBle.text = "Bluetooth apagado. Actívalo."
            return
        }

        if (escaneando) return

        dispositivos.clear()
        nombres.clear()
        adapter.notifyDataSetChanged()

        escaneando = true
        txtEstadoBle.text = "Buscando Gorra..."

        bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
    }

    private fun detenerEscaneo() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        escaneando = false
    }

    private fun desconectarBLE() {
        shouldBeConnected = false
        detenerEscaneo()
        bleManager.desconectar()
        txtEstadoBle.text = "Desconectado"
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val nombre = device.name ?: result.scanRecord?.deviceName ?: "Sin nombre"
            val mac = device.address ?: "Sin MAC"

            if (dispositivos.none { it.address == device.address }) {
                dispositivos.add(device)
                nombres.add("$nombre\n$mac")
                adapter.notifyDataSetChanged()
            }

            if (nombre == nombreGorra) {
                detenerEscaneo()

                runOnUiThread {
                    txtEstadoBle.text = "Gorra encontrada. Conectando..."
                }

                bleManager.conectar(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            escaneando = false
            txtEstadoBle.text = "Error al escanear BLE: $errorCode"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarBLE()
    }
}