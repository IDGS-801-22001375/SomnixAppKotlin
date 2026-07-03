package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

        val cardMisRutas = findViewById<android.widget.LinearLayout>(R.id.cardMisRutas)
        val cardAgregarRuta = findViewById<android.widget.LinearLayout>(R.id.cardAgregarRuta)

        cardAgregarRuta.setOnClickListener {
            startActivity(
                Intent(this, AgregarRutaActivity::class.java)
            )
        }

        cardMisRutas.setOnClickListener {
            startActivity(
                Intent(this, ListaRutasActivity::class.java)
            )
        }
    }
}