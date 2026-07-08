package com.example.somnixapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

class SomnixBleManager(
    private val context: Context,
    private val onEstado: (String) -> Unit = {},
    private val onMensaje: (String) -> Unit = {}
) {

    companion object {
        private val SERVICE_UUID: UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

        private val TX_UUID: UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

        private val RX_UUID: UUID =
            UUID.fromString("8a531e21-0a4a-4467-9bb3-392da798a7eb")
    }

    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    var estaConectado = false
        private set

    private fun tienePermisoConnect(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun conectar(device: BluetoothDevice) {
        if (!tienePermisoConnect()) {
            onEstado("Sin permiso BLUETOOTH_CONNECT")
            return
        }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        onEstado("Conectando a gorra...")
    }

    @SuppressLint("MissingPermission")
    fun desconectar() {
        if (!tienePermisoConnect()) return

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        onEstado("BLE desconectado")
    }

    @SuppressLint("MissingPermission")
    fun enviarComando(comando: String): Boolean {
        if (!tienePermisoConnect()) {
            onEstado("Sin permiso BLE")
            return false
        }

        val gatt = bluetoothGatt ?: return false
        val rx = rxCharacteristic ?: return false

        rx.value = comando.toByteArray(Charsets.UTF_8)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                rx,
                comando.toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(rx)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                estaConectado = true
                onEstado("Gorra conectada. Solicitando MTU...")

                handler.postDelayed({
                    if (tienePermisoConnect()) {
                        gatt.requestMtu(512)
                    }
                }, 600)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                estaConectado = false
                onEstado("Gorra desconectada")
                rxCharacteristic = null
                txCharacteristic = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(
            gatt: BluetoothGatt,
            mtu: Int,
            status: Int
        ) {
            onEstado("MTU configurada: $mtu")

            handler.postDelayed({
                if (tienePermisoConnect()) {
                    gatt.discoverServices()
                }
            }, 600)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            val service = gatt.getService(SERVICE_UUID)

            if (service == null) {
                onEstado("Servicio SOMNIX no encontrado")
                return
            }

            txCharacteristic = service.getCharacteristic(TX_UUID)
            rxCharacteristic = service.getCharacteristic(RX_UUID)

            if (rxCharacteristic == null) {
                onEstado("Característica RX no encontrada")
                return
            }

            if (txCharacteristic != null && tienePermisoConnect()) {
                gatt.setCharacteristicNotification(txCharacteristic, true)

                val descriptor = txCharacteristic?.descriptors?.firstOrNull()
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                descriptor?.let { gatt.writeDescriptor(it) }
            }

            onEstado("BLE listo para comandos")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == TX_UUID) {
                val mensaje = characteristic.value?.toString(Charsets.UTF_8) ?: ""
                onMensaje(mensaje)
            }
        }
    }
}