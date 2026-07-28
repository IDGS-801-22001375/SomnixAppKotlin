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
import java.util.UUID

class SomnixBleManager(
    private val context: Context,
    private val onEstado: (String) -> Unit = {},
    private val onMensaje: (String) -> Unit = {},
    private val onConectado: () -> Unit = {},
    private val onDesconectado: () -> Unit = {}
) {

    companion object {
        private val SERVICE_UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

        private val TX_UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

        private val RX_UUID =
            UUID.fromString("8a531e21-0a4a-4467-9bb3-392da798a7eb")

        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private var desconexionManual = false
    private var notificoDesconexion = false

    private var ultimoMensaje = ""
    private var tiempoUltimoMensaje = 0L

    var estaConectando = false
        private set

    var estaConectado = false
        private set

    var estaListo = false
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
            onEstado("ERROR: falta permiso BLUETOOTH_CONNECT")
            return
        }

        /*
         * Evita intentar conectarse otra vez mientras existe
         * una conexión activa o en proceso.
         */
        if (estaConectando || estaConectado || estaListo) {
            return
        }

        desconexionManual = false
        notificoDesconexion = false

        limpiarGattAnterior()

        estaConectando = true
        estaConectado = false
        estaListo = false

        onEstado("Conectando a la gorra SOMNIX")

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                context.applicationContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(
                context.applicationContext,
                false,
                gattCallback
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun desconectar() {
        desconexionManual = true

        estaConectando = false
        estaConectado = false
        estaListo = false

        val gatt = bluetoothGatt

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }

        handler.postDelayed({
            try {
                gatt?.close()
            } catch (_: Exception) {
            }
        }, 300)
    }

    @SuppressLint("MissingPermission")
    private fun limpiarGattAnterior() {
        val anterior = bluetoothGatt

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        try {
            anterior?.disconnect()
        } catch (_: Exception) {
        }

        try {
            anterior?.close()
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun enviarComando(comando: String): Boolean {
        if (!tienePermisoConnect()) {
            return false
        }

        val gatt = bluetoothGatt
        val rx = rxCharacteristic

        if (
            gatt == null ||
            rx == null ||
            !estaConectado ||
            !estaListo
        ) {
            return false
        }

        val datos = comando
            .trim()
            .toByteArray(Charsets.UTF_8)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            /*
             * Si el callback pertenece a una conexión anterior,
             * lo ignoramos y cerramos ese GATT.
             */
            if (gatt !== bluetoothGatt) {
                try {
                    gatt.close()
                } catch (_: Exception) {
                }

                return
            }

            if (
                status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED
            ) {
                estaConectando = false
                estaConectado = true
                estaListo = false

                onEstado("Gorra conectada. Buscando servicios")

                /*
                 * No llamamos onConectado todavía.
                 * Primero deben encontrarse los servicios.
                 */
                handler.postDelayed({
                    if (
                        tienePermisoConnect() &&
                        gatt === bluetoothGatt &&
                        estaConectado
                    ) {
                        val iniciado = gatt.discoverServices()

                        if (!iniciado) {
                            manejarErrorGatt(
                                gatt,
                                "No se pudo iniciar la búsqueda de servicios"
                            )
                        }
                    }
                }, 500)

                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                manejarDesconexion(gatt, status)
                return
            }

            /*
             * Un status diferente de GATT_SUCCESS también representa
             * un fallo de conexión aunque Android no siempre mande
             * inmediatamente STATE_DISCONNECTED.
             */
            if (status != BluetoothGatt.GATT_SUCCESS) {
                manejarDesconexion(gatt, status)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            if (gatt !== bluetoothGatt) {
                return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                manejarErrorGatt(
                    gatt,
                    "Error al descubrir servicios BLE: $status"
                )
                return
            }

            val service = gatt.getService(SERVICE_UUID)

            if (service == null) {
                manejarErrorGatt(
                    gatt,
                    "Servicio SOMNIX no encontrado"
                )
                return
            }

            rxCharacteristic =
                service.getCharacteristic(RX_UUID)

            txCharacteristic =
                service.getCharacteristic(TX_UUID)

            if (rxCharacteristic == null) {
                manejarErrorGatt(
                    gatt,
                    "Característica RX no encontrada"
                )
                return
            }

            val tx = txCharacteristic

            if (tx == null) {
                /*
                 * Se pueden mandar comandos, pero no recibir eventos.
                 */
                marcarComoListo()
                return
            }

            if (!tienePermisoConnect()) {
                manejarErrorGatt(
                    gatt,
                    "Falta permiso para activar notificaciones BLE"
                )
                return
            }

            val notificationsEnabled =
                gatt.setCharacteristicNotification(
                    tx,
                    true
                )

            if (!notificationsEnabled) {
                manejarErrorGatt(
                    gatt,
                    "No se pudieron activar las notificaciones BLE"
                )
                return
            }

            val descriptor = tx.getDescriptor(CCCD_UUID)

            if (descriptor == null) {
                /*
                 * Aunque no exista el descriptor, RX ya está disponible
                 * y todavía podremos enviar comandos.
                 */
                marcarComoListo()
                return
            }

            val escrituraIniciada =
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

            if (!escrituraIniciada) {
                /*
                 * No bloqueamos toda la conexión por un fallo
                 * al activar las notificaciones.
                 */
                onEstado("ERROR: no se pudieron activar las notificaciones TX")
                marcarComoListo()
                return
            }

            /*
             * Algunos teléfonos no ejecutan onDescriptorWrite.
             * Este respaldo marca la conexión como lista después de 1.5 segundos.
             */
            handler.postDelayed({
                if (
                    estaConectado &&
                    !estaListo &&
                    gatt === bluetoothGatt &&
                    rxCharacteristic != null
                ) {
                    onEstado("BLE listo mediante respaldo")
                    marcarComoListo()
                }
            }, 1_500L)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (
                gatt !== bluetoothGatt ||
                descriptor.uuid != CCCD_UUID
            ) {
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                marcarComoListo()
            } else {
                /*
                 * RX puede seguir funcionando aunque TX no haya
                 * activado correctamente sus notificaciones.
                 */
                onEstado(
                    "ERROR: no se pudo activar la recepción TX. Código: $status"
                )

                marcarComoListo()
            }
        }

        @Deprecated(
            "Callback anterior a Android 13",
            ReplaceWith("procesarMensajeRecibido(characteristic.value)")
        )
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != TX_UUID) {
                return
            }

            @Suppress("DEPRECATION")
            procesarMensajeRecibido(
                characteristic.value
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != TX_UUID) {
                return
            }

            procesarMensajeRecibido(value)
        }
    }

    private fun marcarComoListo() {
        if (estaListo) {
            return
        }

        if (
            bluetoothGatt == null ||
            rxCharacteristic == null ||
            !estaConectado
        ) {
            return
        }

        estaConectando = false
        estaConectado = true
        estaListo = true
        notificoDesconexion = false

        onEstado("BLE listo para comandos")
        onConectado()

        handler.postDelayed({
            if (estaListo) {
                enviarComando("SYNC")
            }
        }, 500L)
    }

    private fun procesarMensajeRecibido(datos: ByteArray?) {
        val mensaje = datos
            ?.toString(Charsets.UTF_8)
            ?.trim()
            .orEmpty()

        if (mensaje.isBlank()) {
            return
        }

        val ahora = System.currentTimeMillis()

        /*
         * Evita entregar el mismo mensaje muchas veces
         * en un intervalo muy corto.
         */
        if (
            mensaje == ultimoMensaje &&
            ahora - tiempoUltimoMensaje < 1_500L
        ) {
            return
        }

        ultimoMensaje = mensaje
        tiempoUltimoMensaje = ahora

        onMensaje(mensaje)
    }

    @SuppressLint("MissingPermission")
    private fun manejarErrorGatt(
        gatt: BluetoothGatt,
        mensaje: String
    ) {
        onEstado("ERROR: $mensaje")

        try {
            gatt.disconnect()
        } catch (_: Exception) {
        }

        manejarDesconexion(
            gatt,
            BluetoothGatt.GATT_FAILURE
        )
    }

    @SuppressLint("MissingPermission")
    private fun manejarDesconexion(
        gatt: BluetoothGatt,
        status: Int
    ) {
        if (
            gatt !== bluetoothGatt &&
            bluetoothGatt != null
        ) {
            try {
                gatt.close()
            } catch (_: Exception) {
            }

            return
        }

        estaConectando = false
        estaConectado = false
        estaListo = false

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        try {
            gatt.close()
        } catch (_: Exception) {
        }

        /*
         * Evita ejecutar onDesconectado dos veces por el mismo GATT.
         */
        if (!notificoDesconexion) {
            notificoDesconexion = true

            if (!desconexionManual) {
                onEstado(
                    "Gorra desconectada. Código BLE: $status"
                )
            }

            onDesconectado()
        }
    }
}