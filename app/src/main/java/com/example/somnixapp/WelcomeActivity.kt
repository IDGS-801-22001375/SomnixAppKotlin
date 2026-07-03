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

    private lateinit var btnGoogle: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnLogin = findViewById<TextView>(R.id.txtIniciarSesion)
        val btnRegister = findViewById<Button>(R.id.btnRegistrarme)
        btnGoogle = findViewById(R.id.btnGoogle)

        val googleAuthHelper = GoogleAuthHelper(this)
        val socialAuthManager = SocialAuthManager(this)

        btnGoogle.setOnClickListener {
            lifecycleScope.launch {
                val idToken = googleAuthHelper.obtenerIdTokenGoogle()

                if (idToken != null) {
                    socialAuthManager.loginConGoogle(idToken)
                }
            }
        }

        btnLogin.setOnClickListener {

            startActivity(
                Intent(this, LoginActivity::class.java)
            )

        }

        btnRegister.setOnClickListener {

            startActivity(
                Intent(this, RegisterActivity::class.java)
            )

        }
    }
}