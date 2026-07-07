package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val cardMisRutas = findViewById<LinearLayout>(R.id.cardMisRutas)
        val cardAgregarRuta = findViewById<LinearLayout>(R.id.cardAgregarRuta)
        val cardCamara = findViewById<LinearLayout>(R.id.cardCamara)

        cardAgregarRuta.setOnClickListener {
            startActivity(Intent(this, SeleccionarRutaMapaActivity::class.java))
        }

        cardMisRutas.setOnClickListener {
            startActivity(Intent(this, ListaRutasActivity::class.java))
        }

        cardCamara.setOnClickListener {
            val intent = Intent(this, ListaRutasActivity::class.java)
            intent.putExtra("MODO", "SELECCIONAR_RUTA")
            startActivity(intent)
        }
    }
}