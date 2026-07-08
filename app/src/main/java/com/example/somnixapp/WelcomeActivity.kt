package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.utils.GoogleAuthHelper
import com.example.somnixapp.utils.SocialAuthManager
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnLogin = findViewById<Button>(R.id.btnIniciarSesion)
        //val btnRegister = findViewById<Button>(R.id.btnRegistrarme)

        val googleAuthHelper = GoogleAuthHelper(this)
        val socialAuthManager = SocialAuthManager(this)

        btnLogin.setOnClickListener {

            startActivity(
                Intent(this, LoginActivity::class.java)
            )

        }

        /*btnRegister.setOnClickListener {

            startActivity(
                Intent(this, RegisterActivity::class.java)
            )

        }*/
    }
}