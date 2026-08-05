package com.example.somnixapp

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

class MonitorFragment : Fragment() {
    private var isModoTest = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val vista = inflater.inflate(R.layout.gorra_test_fragment_monitor, container, false)
        val host = activity as ConfigurarGorra

        val botonConectar = vista.findViewById<Button>(R.id.btnConnect)

        botonConectar.setOnClickListener {
            if (host.shouldBeConnected) {
                host.desconectarBLE()
            } else {
                host.iniciarEscaneoBLE()
                botonConectar.text = "DESCONECTAR"
                botonConectar.setBackgroundColor(Color.parseColor("#EF4444"))
            }
        }

        vista.findViewById<Button>(R.id.btnCalibrate).setOnClickListener { host.enviarComandoBLE("CALIBRAR") }
        vista.findViewById<Button>(R.id.btnTest1).setOnClickListener { host.enviarComandoBLE("FORZAR_NIVEL_1") }
        vista.findViewById<Button>(R.id.btnTest2).setOnClickListener { host.enviarComandoBLE("FORZAR_NIVEL_2") }
        vista.findViewById<Button>(R.id.btnTest3).setOnClickListener { host.enviarComandoBLE("FORZAR_NIVEL_3") }
        vista.findViewById<Button>(R.id.btnStopAlert).setOnClickListener { host.enviarComandoBLE("APAGAR") }

        vista.findViewById<Button>(R.id.btnToggleModo).setOnClickListener {
            if (isModoTest) host.enviarComandoBLE("MODO_AUTO") else host.enviarComandoBLE("MODO_TEST")
        }

        // CORRECCIÃ"N: Eventos de Ruta con Auto-CalibraciÃ³n
        vista.findViewById<Button>(R.id.btnIniciarRuta).setOnClickListener {
            host.enviarComandoBLE("CALIBRAR")
            Handler(Looper.getMainLooper()).postDelayed({ host.enviarComandoBLE("VIAJE_INICIAR") }, 600)
        }

        vista.findViewById<Button>(R.id.btnPausarRuta).setOnClickListener { host.enviarComandoBLE("VIAJE_PAUSAR") }

        vista.findViewById<Button>(R.id.btnReanudarRuta).setOnClickListener {
            host.enviarComandoBLE("CALIBRAR")
            Handler(Looper.getMainLooper()).postDelayed({ host.enviarComandoBLE("VIAJE_REANUDAR") }, 600)
        }

        vista.findViewById<Button>(R.id.btnTerminarRuta).setOnClickListener { host.enviarComandoBLE("VIAJE_TERMINAR") }

        if (host.lastJsonTelemetria != "{}") {
            try { actualizarTelemetria(JSONObject(host.lastJsonTelemetria)) } catch (e: Exception) {}
        }

        return vista
    }

    fun onBleConnected() {
        view?.findViewById<Button>(R.id.btnConnect)?.let {
            it.text = "DESCONECTAR"
            it.setBackgroundColor(Color.parseColor("#EF4444"))
        }
    }

    fun onBleDisconnected() {
        view?.findViewById<Button>(R.id.btnConnect)?.let {
            it.text = "CONECTAR GORRA (BLE)"
            it.setBackgroundColor(Color.parseColor("#2563EB"))
        }
    }

    fun actualizarTelemetria(data: JSONObject) {
        val vista = view ?: return
        try {
            val p = data.getDouble("p").toFloat()
            val r = data.getDouble("r").toFloat()
            val n = data.getInt("n")
            isModoTest = (data.getInt("t") == 1)
            val estadoViaje = if (data.has("v")) data.getInt("v") else 0
            val tr = data.getLong("tr")

            vista.findViewById<TextView>(R.id.tvPitch).text = "${p}Â°"
            vista.findViewById<TextView>(R.id.tvRoll).text = "${r}Â°"
            if (tr > 0) vista.findViewById<TextView>(R.id.tvReact).text = "${tr}ms"

            vista.findViewById<View>(R.id.cvHead3D).animate().rotationX(p).rotationY(-r).setDuration(400).start()

            val tvAlertBanner = vista.findViewById<TextView>(R.id.tvAlertBanner)
            when (n) {
                0 -> { tvAlertBanner.text = "ESTADO: SEGURO Y ESTABLE"; tvAlertBanner.setTextColor(Color.parseColor("#047857")); tvAlertBanner.setBackgroundColor(Color.parseColor("#ECFDF5")) }
                1 -> { tvAlertBanner.text = "NIVEL 1: CANSANCIO DETECTADO"; tvAlertBanner.setTextColor(Color.parseColor("#B45309")); tvAlertBanner.setBackgroundColor(Color.parseColor("#FFFBEB")) }
                2 -> { tvAlertBanner.text = "NIVEL 2: PELIGRO DE MICROSUEÃ'O"; tvAlertBanner.setTextColor(Color.parseColor("#C2410C")); tvAlertBanner.setBackgroundColor(Color.parseColor("#FFF7ED")) }
                3 -> { tvAlertBanner.text = "NIVEL 3: RIESGO CRÃ?TICO INMINENTE"; tvAlertBanner.setTextColor(Color.parseColor("#B91C1C")); tvAlertBanner.setBackgroundColor(Color.parseColor("#FEF2F2")) }
            }

            val btnToggleModo = vista.findViewById<Button>(R.id.btnToggleModo)
            val tvModoState = vista.findViewById<TextView>(R.id.tvModoState)
            if (isModoTest) {
                tvModoState.text = "Modo de OperaciÃ³n: Test (Forzado)"
                tvModoState.setTextColor(Color.parseColor("#8B5CF6"))
                btnToggleModo.text = "VOLVER A MODO AUTOMÃ?TICO"
                btnToggleModo.setBackgroundColor(Color.parseColor("#2563EB"))
            } else {
                tvModoState.text = "Modo de OperaciÃ³n: AutomÃ¡tico"
                tvModoState.setTextColor(Color.parseColor("#64748B"))
                btnToggleModo.text = "ACTIVAR MODO TEST"
                btnToggleModo.setBackgroundColor(Color.parseColor("#8B5CF6"))
            }

            // CORRECCIÃ"N: Interfaz reactiva a la mÃ¡quina de estados del Viaje (0, 1, 2)
            val tvRutaState = vista.findViewById<TextView>(R.id.tvRutaState)
            val btnIniciar = vista.findViewById<Button>(R.id.btnIniciarRuta)
            val btnPausar = vista.findViewById<Button>(R.id.btnPausarRuta)
            val btnReanudar = vista.findViewById<Button>(R.id.btnReanudarRuta)
            val btnTerminar = vista.findViewById<Button>(R.id.btnTerminarRuta)

            when (estadoViaje) {
                1 -> {
                    tvRutaState.text = "GestiÃ³n de Ruta: Activa"
                    tvRutaState.setTextColor(Color.parseColor("#10B981"))
                    btnIniciar.visibility = View.GONE
                    btnPausar.visibility = View.VISIBLE
                    btnReanudar.visibility = View.GONE
                    btnTerminar.visibility = View.VISIBLE
                }
                2 -> {
                    tvRutaState.text = "GestiÃ³n de Ruta: Pausada"
                    tvRutaState.setTextColor(Color.parseColor("#F59E0B"))
                    btnIniciar.visibility = View.GONE
                    btnPausar.visibility = View.GONE
                    btnReanudar.visibility = View.VISIBLE
                    btnTerminar.visibility = View.VISIBLE
                }
                else -> {
                    tvRutaState.text = "GestiÃ³n de Ruta: Inactiva"
                    tvRutaState.setTextColor(Color.parseColor("#64748B"))
                    btnIniciar.visibility = View.VISIBLE
                    btnPausar.visibility = View.GONE
                    btnReanudar.visibility = View.GONE
                    btnTerminar.visibility = View.GONE
                }
            }
        } catch (e: Exception) {}
    }
}

