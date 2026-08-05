package com.example.somnixapp.ble

sealed class BleConnectionState {

    object Desconectado : BleConnectionState()

    object Buscando : BleConnectionState()

    object Conectando : BleConnectionState()

    object Configurando : BleConnectionState()

    object Listo : BleConnectionState()

    data class Error(
        val mensaje: String
    ) : BleConnectionState()
}