package com.example.somnixapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class TerminalFragment : Fragment() {

    private lateinit var tvJsonApi: TextView
    private lateinit var tvJsonBle: TextView
    private lateinit var tvConsole: TextView
    private lateinit var scrollConsole: ScrollView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.gorra_test_fragment_terminal, container, false)
        val host = activity as ConfigurarGorra

        tvJsonApi = view.findViewById(R.id.tvJsonApi)
        tvJsonBle = view.findViewById(R.id.tvJsonBle)
        tvConsole = view.findViewById(R.id.tvConsole)
        scrollConsole = view.findViewById(R.id.scrollConsole)

        view.findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            host.logHistory.setLength(0)
            tvConsole.text = ""
        }

        // Cargar datos actuales
        if (host.lastJsonTelemetria != "{}") actualizarRaw(host.lastJsonTelemetria)
        tvConsole.text = host.logHistory.toString()
        hacerScrollAbajo()

        return view
    }

    fun actualizarRaw(jsonStr: String) {
        val view = view ?: return
        tvJsonApi.text = jsonStr
        tvJsonBle.text = jsonStr
    }

    fun actualizarLogs(logs: String) {
        val view = view ?: return
        tvConsole.text = logs
        hacerScrollAbajo()
    }

    private fun hacerScrollAbajo() {
        scrollConsole.post { scrollConsole.fullScroll(View.FOCUS_DOWN) }
    }
}
