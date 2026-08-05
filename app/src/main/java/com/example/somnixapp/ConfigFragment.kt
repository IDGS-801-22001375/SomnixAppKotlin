package com.example.somnixapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONObject

class ConfigFragment : Fragment() {

    private lateinit var etUmb: EditText
    private lateinit var etT1: EditText
    private lateinit var etT2: EditText
    private lateinit var etT3: EditText
    private lateinit var etIntApi: EditText
    private lateinit var etIntBle: EditText
    private lateinit var etUrl: EditText

    private var isApiActiva = false // NUEVO

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val vista = inflater.inflate(R.layout.gorra_test_fragment_config, container, false)
        val host = activity as ConfigurarGorra

        etUmb = vista.findViewById(R.id.etUmb)
        etT1 = vista.findViewById(R.id.etT1)
        etT2 = vista.findViewById(R.id.etT2)
        etT3 = vista.findViewById(R.id.etT3)
        etIntApi = vista.findViewById(R.id.etIntApi)
        etIntBle = vista.findViewById(R.id.etIntBle)
        etUrl = vista.findViewById(R.id.etUrl)

        vista.findViewById<Button>(R.id.btnSync).setOnClickListener { host.enviarComandoBLE("SYNC") }
        vista.findViewById<Button>(R.id.btnSetUmb).setOnClickListener { host.enviarComandoBLE("UMBRAL:${etUmb.text}") }
        vista.findViewById<Button>(R.id.btnSetT1).setOnClickListener { host.enviarComandoBLE("T1:${etT1.text}") }
        vista.findViewById<Button>(R.id.btnSetT2).setOnClickListener { host.enviarComandoBLE("T2:${etT2.text}") }
        vista.findViewById<Button>(R.id.btnSetT3).setOnClickListener { host.enviarComandoBLE("T3:${etT3.text}") }
        vista.findViewById<Button>(R.id.btnSetApi).setOnClickListener { host.enviarComandoBLE("INT_API:${etIntApi.text}") }
        vista.findViewById<Button>(R.id.btnSetBle).setOnClickListener { host.enviarComandoBLE("INT_BLE:${etIntBle.text}") }
        vista.findViewById<Button>(R.id.btnSetUrl).setOnClickListener { host.enviarComandoBLE("URL:${etUrl.text}") }

        // NUEVO: BotÃ³n Toggle
        vista.findViewById<Button>(R.id.btnToggleApi).setOnClickListener {
            if (isApiActiva) host.enviarComandoBLE("API_OFF") else host.enviarComandoBLE("API_ON")
            host.enviarComandoBLE("SYNC") // Forzar actualizaciÃ³n visual al instante
        }

        if (host.lastJsonConfig != "{}") {
            try { actualizarConfig(JSONObject(host.lastJsonConfig)) } catch(e: Exception){}
        }

        return vista
    }

    fun actualizarConfig(data: JSONObject) {
        val vista = view ?: return
        try {
            etUmb.setText(data.getDouble("umbral").toString())
            etT1.setText(data.getInt("t1").toString())
            etT2.setText(data.getInt("t2").toString())
            etT3.setText(data.getInt("t3").toString())
            etIntApi.setText(data.getInt("iapi").toString())
            etIntBle.setText(data.getInt("ible").toString())
            etUrl.setText(data.getString("url"))

            isApiActiva = data.getBoolean("api")
            val tvEstadoApi = vista.findViewById<TextView>(R.id.tvEstadoApi)
            val btnToggleApi = vista.findViewById<Button>(R.id.btnToggleApi)

            if (isApiActiva) {
                tvEstadoApi.text = "Estado API: Enviando"
                tvEstadoApi.setTextColor(android.graphics.Color.parseColor("#10B981"))
                btnToggleApi.text = "APAGAR"
                btnToggleApi.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
            } else {
                tvEstadoApi.text = "Estado API: Apagado"
                tvEstadoApi.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                btnToggleApi.text = "ENCENDER"
                btnToggleApi.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
            }

            Toast.makeText(context, "SincronizaciÃ³n NVS completada", Toast.LENGTH_SHORT).show()
        } catch(e: Exception) {}
    }

    fun actualizarApiLog(hc: Int) {
        val vista = view ?: return
        val tvApiLogs = vista.findViewById<TextView>(R.id.tvApiLogs)
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val statusText = if (hc in 200..299) "Ã%XITO (HTTP $hc)" else "FALLO (HTTP $hc)"
        tvApiLogs.append("[$time] POST -> $statusText\n")
    }
}

