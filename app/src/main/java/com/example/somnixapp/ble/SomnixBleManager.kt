package com.example.somnixapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.UUID

class SomnixBleManager(
    private val context: Context,
    private val onEstado: (String) -> Unit = {},
    private val onMensaje: (String) -> Unit = {},
    private val onConectado: () -> Unit = {},
    private val onListo: () -> Unit = {},
    private val onDesconectado: () -> Unit = {}
) {

    companion object {

        private val SERVICE_UUID: UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

        /*
         * TX:
         * La gorra escribe y Android recibe notificaciones.
         */
        private val TX_UUID: UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

        /*
         * RX:
         * Android escribe comandos hacia la gorra.
         */
        private val RX_UUID: UUID =
            UUID.fromString("8a531e21-0a4a-4467-9bb3-392da798a7eb")

        private val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val INTERVALO_ENTRE_COMANDOS = 250L
        private const val TIEMPO_DESCUBRIR_SERVICIOS = 500L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private val colaComandos = ArrayDeque<String>()

    private var escribiendoComando = false
    private var bleListo = false
    private var desconexionManual = false

    var estaConectado: Boolean = false
        private set

    val estaListo: Boolean
        get() {
            return estaConectado &&
                    bleListo &&
                    bluetoothGatt != null &&
                    rxCharacteristic != null
        }

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

    // =========================================================
    // CONEXIÓN
    // =========================================================

    @SuppressLint("MissingPermission")
    fun conectar(device: BluetoothDevice) {
        if (!tienePermisoConnect()) {
            onEstado("Sin permiso BLUETOOTH_CONNECT")
            return
        }

        desconexionManual = false
        limpiarConexionAnterior(notificarDesconexion = false)

        onEstado("Conectando a la gorra...")

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(
                context,
                false,
                gattCallback
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun desconectar() {
        desconexionManual = true

        estaConectado = false
        bleListo = false
        escribiendoComando = false

        synchronized(colaComandos) {
            colaComandos.clear()
        }

        try {
            bluetoothGatt?.disconnect()
        } catch (_: Exception) {
        }

        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {
        }

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        onEstado("BLE desconectado")
    }

    @SuppressLint("MissingPermission")
    private fun limpiarConexionAnterior(
        notificarDesconexion: Boolean
    ) {
        estaConectado = false
        bleListo = false
        escribiendoComando = false

        synchronized(colaComandos) {
            colaComandos.clear()
        }

        try {
            bluetoothGatt?.disconnect()
        } catch (_: Exception) {
        }

        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {
        }

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        if (notificarDesconexion) {
            onDesconectado()
        }
    }

    // =========================================================
    // ENVÍO DE COMANDOS
    // =========================================================

    /**
     * Agrega un comando a la cola.
     *
     * IMPORTANTE:
     * El comando se manda exactamente como lo espera el ESP32.
     * No se agrega salto de línea al final.
     */
    fun enviarComando(comando: String): Boolean {
        val comandoLimpio = comando.trim()

        if (comandoLimpio.isBlank()) {
            return false
        }

        if (!tienePermisoConnect()) {
            onEstado("Sin permiso Bluetooth")
            return false
        }

        if (!estaListo) {
            onEstado("BLE todavía no está listo")
            return false
        }

        synchronized(colaComandos) {
            val ultimo = colaComandos.lastOrNull()

            /*
             * Evita duplicados consecutivos, principalmente cuando la cámara
             * detecta varias veces la misma condición.
             */
            if (ultimo != comandoLimpio) {
                colaComandos.addLast(comandoLimpio)
            }
        }

        procesarSiguienteComando()
        return true
    }

    /**
     * Envía varios comandos respetando el orden y el intervalo indicado.
     */
    fun enviarSecuencia(
        comandos: List<String>,
        intervaloMs: Long = 600L
    ) {
        if (comandos.isEmpty()) return

        comandos.forEachIndexed { index, comando ->
            handler.postDelayed(
                {
                    enviarComando(comando)
                },
                index * intervaloMs
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun procesarSiguienteComando() {
        if (escribiendoComando || !estaListo) {
            return
        }

        val gatt = bluetoothGatt ?: return
        val rx = rxCharacteristic ?: return

        val comando = synchronized(colaComandos) {
            colaComandos.pollFirst()
        } ?: return

        escribiendoComando = true

        /*
         * No agregar "\n".
         * El código funcional original enviaba directamente:
         * comando.toByteArray()
         */
        val datos = comando.toByteArray(Charsets.UTF_8)

        val escrituraIniciada =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    rx,
                    datos,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.value = datos

                @Suppress("DEPRECATION")
                rx.writeType =
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }

        if (!escrituraIniciada) {
            escribiendoComando = false
            onEstado("No se pudo enviar el comando BLE")

            handler.postDelayed(
                {
                    procesarSiguienteComando()
                },
                INTERVALO_ENTRE_COMANDOS
            )
        }
    }

    // =========================================================
    // RECEPCIÓN DE DATOS
    // =========================================================

    private fun procesarMensajeRecibido(datos: ByteArray) {
        val mensaje = datos
            .toString(Charsets.UTF_8)
            .trim()

        if (mensaje.isNotBlank()) {
            /*
             * El mensaje se entrega a la Activity.
             * La Activity NO debe convertirlo en notificación.
             */
            onMensaje(mensaje)
        }
    }

    private fun marcarBleListo() {
        if (bleListo) return

        bleListo = true

        onEstado("BLE listo")
        onListo()

        /*
         * Sincroniza después de que RX y las notificaciones están listas.
         * SYNC no generará notificación en la Activity.
         */
        handler.postDelayed(
            {
                enviarComando("SYNC")
            },
            300L
        )
    }

    // =========================================================
    // CALLBACK GATT
    // =========================================================

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                estaConectado = false
                bleListo = false
                escribiendoComando = false

                try {
                    gatt.close()
                } catch (_: Exception) {
                }

                if (bluetoothGatt === gatt) {
                    bluetoothGatt = null
                }

                rxCharacteristic = null
                txCharacteristic = null

                onEstado("Error de conexión BLE: $status")

                if (!desconexionManual) {
                    onDesconectado()
                }

                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    bluetoothGatt = gatt
                    estaConectado = true
                    bleListo = false
                    escribiendoComando = false

                    onEstado("Gorra conectada")
                    onConectado()

                    handler.postDelayed(
                        {
                            if (!tienePermisoConnect()) {
                                return@postDelayed
                            }

                            /*
                             * El código funcional solicitaba MTU 512.
                             */
                            val mtuSolicitada = gatt.requestMtu(512)

                            if (!mtuSolicitada) {
                                handler.postDelayed(
                                    {
                                        if (tienePermisoConnect()) {
                                            gatt.discoverServices()
                                        }
                                    },
                                    TIEMPO_DESCUBRIR_SERVICIOS
                                )
                            }
                        },
                        400L
                    )
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    estaConectado = false
                    bleListo = false
                    escribiendoComando = false

                    synchronized(colaComandos) {
                        colaComandos.clear()
                    }

                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }

                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }

                    rxCharacteristic = null
                    txCharacteristic = null

                    onEstado("Gorra desconectada")

                    if (!desconexionManual) {
                        onDesconectado()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(
            gatt: BluetoothGatt,
            mtu: Int,
            status: Int
        ) {
            handler.postDelayed(
                {
                    if (tienePermisoConnect()) {
                        gatt.discoverServices()
                    }
                },
                TIEMPO_DESCUBRIR_SERVICIOS
            )
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onEstado("No se pudieron descubrir los servicios BLE")
                return
            }

            val service = gatt.getService(SERVICE_UUID)

            if (service == null) {
                onEstado("Servicio SOMNIX no encontrado")
                return
            }

            rxCharacteristic = service.getCharacteristic(RX_UUID)
            txCharacteristic = service.getCharacteristic(TX_UUID)

            if (rxCharacteristic == null) {
                onEstado("Característica RX no encontrada")
                return
            }

            val tx = txCharacteristic

            /*
             * Aunque TX no exista, todavía se pueden mandar comandos por RX.
             */
            if (tx == null) {
                marcarBleListo()
                return
            }

            if (!tienePermisoConnect()) {
                return
            }

            val notificacionesActivadas =
                gatt.setCharacteristicNotification(tx, true)

            if (!notificacionesActivadas) {
                marcarBleListo()
                return
            }

            val descriptor = tx.getDescriptor(CCCD_UUID)

            if (descriptor == null) {
                marcarBleListo()
                return
            }

            val descriptorIniciado =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value =
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }

            if (!descriptorIniciado) {
                marcarBleListo()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                marcarBleListo()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid != RX_UUID) {
                return
            }

            escribiendoComando = false

            if (status != BluetoothGatt.GATT_SUCCESS) {
                onEstado("Error al enviar comando BLE")
            }

            handler.postDelayed(
                {
                    procesarSiguienteComando()
                },
                INTERVALO_ENTRE_COMANDOS
            )
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == TX_UUID
            ) {
                procesarMensajeRecibido(
                    characteristic.value ?: byteArrayOf()
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == TX_UUID
            ) {
                procesarMensajeRecibido(value)
            }
        }
    }
}