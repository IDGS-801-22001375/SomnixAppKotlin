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

class SomnixGattClient(
    context: Context,
    private val callback: Callback
) {

    interface Callback {

        fun onEstado(
            estado: BleConnectionState
        )

        fun onListo()

        fun onMensaje(
            mensaje: String
        )

        fun onDesconectado(
            status: Int
        )

        fun onLog(
            mensaje: String
        )
    }

    private val appContext =
        context.applicationContext

    private val handler =
        Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null

    private var rxCharacteristic:
            BluetoothGattCharacteristic? = null

    private var txCharacteristic:
            BluetoothGattCharacteristic? = null

    private var descubriendoServicios = false
    private var escribiendoComando = false
    private var conexionLista = false

    private val colaComandos =
        ArrayDeque<String>()

    private val timeoutConexion = Runnable {
        val gatt = bluetoothGatt

        if (
            gatt != null &&
            !conexionLista
        ) {
            callback.onLog(
                "Tiempo de conexión BLE agotado"
            )

            cerrarGatt(
                gatt = gatt,
                notificar = true,
                status = BluetoothGatt.GATT_FAILURE
            )
        }
    }

    val estaListo: Boolean
        get() =
            conexionLista &&
                    bluetoothGatt != null &&
                    rxCharacteristic != null

    private fun tienePermisoConnect(): Boolean {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun conectar(
        device: BluetoothDevice
    ) {
        if (!tienePermisoConnect()) {
            callback.onEstado(
                BleConnectionState.Error(
                    "Falta permiso BLUETOOTH_CONNECT"
                )
            )
            return
        }

        desconectar(notificar = false)

        conexionLista = false
        descubriendoServicios = false
        escribiendoComando = false
        colaComandos.clear()

        callback.onEstado(
            BleConnectionState.Conectando
        )

        callback.onLog(
            "Conectando con ${device.address}"
        )

        val nuevoGatt =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M
            ) {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback
                )
            }

        bluetoothGatt = nuevoGatt

        handler.removeCallbacks(timeoutConexion)
        handler.postDelayed(
            timeoutConexion,
            BleConstants.TIEMPO_CONEXION_MS
        )
    }

    /**
     * Coloca un comando en una cola serial.
     *
     * No se inicia un segundo writeCharacteristic hasta recibir
     * onCharacteristicWrite del comando anterior.
     */
    fun enviarComando(
        comando: String
    ): Boolean {
        val limpio = comando.trim()

        if (
            limpio.isBlank() ||
            !estaListo
        ) {
            return false
        }

        /*
         * Limita la cola para evitar una acumulación accidental.
         */
        if (colaComandos.size >= 30) {
            callback.onLog(
                "Cola BLE llena. Comando descartado: $limpio"
            )
            return false
        }

        colaComandos.addLast(limpio)
        procesarSiguienteComando()

        return true
    }

    @SuppressLint("MissingPermission")
    private fun procesarSiguienteComando() {
        if (
            escribiendoComando ||
            !estaListo ||
            !tienePermisoConnect()
        ) {
            return
        }

        val gatt = bluetoothGatt ?: return
        val rx = rxCharacteristic ?: return
        val comando = colaComandos.pollFirst() ?: return

        escribiendoComando = true

        val datos =
            comando.toByteArray(Charsets.UTF_8)

        val escrituraIniciada =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                gatt.writeCharacteristic(
                    rx,
                    datos,
                    BluetoothGattCharacteristic
                        .WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.value = datos

                @Suppress("DEPRECATION")
                rx.writeType =
                    BluetoothGattCharacteristic
                        .WRITE_TYPE_DEFAULT

                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }

        if (!escrituraIniciada) {
            escribiendoComando = false

            callback.onLog(
                "No se pudo iniciar TX: $comando"
            )

            handler.postDelayed(
                { procesarSiguienteComando() },
                150L
            )

            return
        }

        callback.onLog(
            "TX -> $comando"
        )
    }

    @SuppressLint("MissingPermission")
    fun desconectar(
        notificar: Boolean = false
    ) {
        handler.removeCallbacks(timeoutConexion)

        val gatt = bluetoothGatt

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        conexionLista = false
        descubriendoServicios = false
        escribiendoComando = false
        colaComandos.clear()

        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }

            handler.postDelayed({
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
            }, 300L)
        }

        if (notificar) {
            callback.onEstado(
                BleConnectionState.Desconectado
            )
        }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (gatt !== bluetoothGatt) {
                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }
                    return
                }

                if (
                    status == BluetoothGatt.GATT_SUCCESS &&
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {
                    handler.removeCallbacks(
                        timeoutConexion
                    )

                    callback.onEstado(
                        BleConnectionState.Configurando
                    )

                    callback.onLog(
                        "GATT conectado. Solicitando MTU"
                    )

                    try {
                        gatt.requestConnectionPriority(
                            BluetoothGatt
                                .CONNECTION_PRIORITY_HIGH
                        )
                    } catch (_: Exception) {
                    }

                    /*
                     * Conservamos la secuencia que funciona
                     * en la aplicación de prueba:
                     *
                     * conexión -> 600 ms -> MTU.
                     */
                    handler.postDelayed({
                        if (
                            gatt === bluetoothGatt &&
                            tienePermisoConnect()
                        ) {
                            val inicio =
                                gatt.requestMtu(512)

                            if (!inicio) {
                                descubrirServicios(gatt)
                            } else {
                                /*
                                 * Respaldo para dispositivos que
                                 * no ejecuten onMtuChanged.
                                 */
                                handler.postDelayed({
                                    descubrirServicios(gatt)
                                }, 2_000L)
                            }
                        }
                    }, 600L)

                    return
                }

                if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {
                    cerrarGatt(
                        gatt = gatt,
                        notificar = true,
                        status = status
                    )
                    return
                }

                if (
                    status != BluetoothGatt.GATT_SUCCESS
                ) {
                    cerrarGatt(
                        gatt = gatt,
                        notificar = true,
                        status = status
                    )
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                if (gatt !== bluetoothGatt) {
                    return
                }

                callback.onLog(
                    if (
                        status ==
                        BluetoothGatt.GATT_SUCCESS
                    ) {
                        "MTU configurado: $mtu"
                    } else {
                        "No se cambió MTU. Código: $status"
                    }
                )

                descubrirServicios(gatt)
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (gatt !== bluetoothGatt) {
                    return
                }

                descubriendoServicios = false

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    callback.onLog(
                        "Error descubriendo servicios: $status"
                    )

                    cerrarGatt(
                        gatt,
                        true,
                        status
                    )
                    return
                }

                val servicio =
                    gatt.getService(
                        BleConstants.SERVICE_UUID
                    )

                if (servicio == null) {
                    callback.onLog(
                        "Servicio SOMNIX no encontrado"
                    )

                    cerrarGatt(
                        gatt,
                        true,
                        BluetoothGatt.GATT_FAILURE
                    )
                    return
                }

                rxCharacteristic =
                    servicio.getCharacteristic(
                        BleConstants.RX_UUID
                    )

                txCharacteristic =
                    servicio.getCharacteristic(
                        BleConstants.TX_UUID
                    )

                if (rxCharacteristic == null) {
                    callback.onLog(
                        "Característica RX no encontrada"
                    )

                    cerrarGatt(
                        gatt,
                        true,
                        BluetoothGatt.GATT_FAILURE
                    )
                    return
                }

                val tx = txCharacteristic

                if (tx == null) {
                    /*
                     * RX existe: todavía se pueden enviar comandos.
                     */
                    marcarComoListo()
                    return
                }

                val notificacionesActivas =
                    gatt.setCharacteristicNotification(
                        tx,
                        true
                    )

                if (!notificacionesActivas) {
                    callback.onLog(
                        "No se activaron notificaciones locales"
                    )

                    /*
                     * Los comandos RX todavía son funcionales.
                     */
                    marcarComoListo()
                    return
                }

                val descriptor =
                    tx.getDescriptor(
                        BleConstants.CCCD_UUID
                    )

                if (descriptor == null) {
                    callback.onLog(
                        "CCCD no encontrado; RX continúa disponible"
                    )
                    marcarComoListo()
                    return
                }

                val escrituraIniciada =
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
                    ) {
                        gatt.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor
                                .ENABLE_NOTIFICATION_VALUE
                        ) == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value =
                            BluetoothGattDescriptor
                                .ENABLE_NOTIFICATION_VALUE

                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }

                if (!escrituraIniciada) {
                    callback.onLog(
                        "No se pudo escribir CCCD"
                    )
                    marcarComoListo()
                    return
                }

                /*
                 * Respaldo por si Android no entrega
                 * onDescriptorWrite.
                 */
                handler.postDelayed({
                    if (
                        gatt === bluetoothGatt &&
                        !conexionLista &&
                        rxCharacteristic != null
                    ) {
                        callback.onLog(
                            "BLE listo mediante respaldo CCCD"
                        )
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
                    descriptor.uuid !=
                    BleConstants.CCCD_UUID
                ) {
                    return
                }

                if (
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    callback.onLog(
                        "Notificaciones TX activadas"
                    )
                } else {
                    callback.onLog(
                        "Error activando TX: $status"
                    )
                }

                /*
                 * Aunque falle TX, RX puede mandar comandos.
                 */
                marcarComoListo()
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic,
                status: Int
            ) {
                if (
                    gatt !== bluetoothGatt ||
                    characteristic.uuid !=
                    BleConstants.RX_UUID
                ) {
                    return
                }

                escribiendoComando = false

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    callback.onLog(
                        "Error escribiendo comando: $status"
                    )
                }

                handler.postDelayed(
                    { procesarSiguienteComando() },
                    100L
                )
            }

            @Deprecated(
                "Callback anterior a Android 13"
            )
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic
            ) {
                if (
                    gatt !== bluetoothGatt ||
                    characteristic.uuid !=
                    BleConstants.TX_UUID
                ) {
                    return
                }

                @Suppress("DEPRECATION")
                entregarMensaje(
                    characteristic.value
                )
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (
                    gatt !== bluetoothGatt ||
                    characteristic.uuid !=
                    BleConstants.TX_UUID
                ) {
                    return
                }

                entregarMensaje(value)
            }
        }

    @SuppressLint("MissingPermission")
    private fun descubrirServicios(
        gatt: BluetoothGatt
    ) {
        if (
            gatt !== bluetoothGatt ||
            descubriendoServicios ||
            !tienePermisoConnect()
        ) {
            return
        }

        descubriendoServicios = true

        /*
         * Segundo retraso utilizado por la implementación
         * que ya funcionó en la prueba.
         */
        handler.postDelayed({
            if (
                gatt === bluetoothGatt &&
                tienePermisoConnect()
            ) {
                val inicio =
                    gatt.discoverServices()

                if (!inicio) {
                    descubriendoServicios = false

                    callback.onLog(
                        "No se inició discoverServices"
                    )

                    cerrarGatt(
                        gatt,
                        true,
                        BluetoothGatt.GATT_FAILURE
                    )
                }
            }
        }, 600L)
    }

    private fun marcarComoListo() {
        if (
            conexionLista ||
            bluetoothGatt == null ||
            rxCharacteristic == null
        ) {
            return
        }

        conexionLista = true
        descubriendoServicios = false

        handler.removeCallbacks(timeoutConexion)

        callback.onEstado(
            BleConnectionState.Listo
        )

        callback.onListo()
    }

    private fun entregarMensaje(
        datos: ByteArray?
    ) {
        val mensaje =
            datos
                ?.toString(Charsets.UTF_8)
                ?.trim()
                .orEmpty()

        if (mensaje.isBlank()) {
            return
        }

        callback.onMensaje(mensaje)
    }

    @SuppressLint("MissingPermission")
    private fun cerrarGatt(
        gatt: BluetoothGatt,
        notificar: Boolean,
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

        handler.removeCallbacks(timeoutConexion)

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null

        conexionLista = false
        descubriendoServicios = false
        escribiendoComando = false
        colaComandos.clear()

        try {
            gatt.disconnect()
        } catch (_: Exception) {
        }

        try {
            gatt.close()
        } catch (_: Exception) {
        }

        if (notificar) {
            callback.onEstado(
                BleConnectionState.Desconectado
            )

            callback.onDesconectado(status)
        }
    }
}