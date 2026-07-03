package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.UsuarioRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var edtEmailLogin: EditText
    private lateinit var edtPasswordLogin: EditText
    private lateinit var btnIniciarSesion: Button
    private lateinit var txtIrRegistro: TextView

    private val usuarioRepository = UsuarioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtEmailLogin = findViewById(R.id.edtEmailLogin)
        edtPasswordLogin = findViewById(R.id.edtPasswordLogin)
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion)
        txtIrRegistro = findViewById(R.id.txtIrRegistro)

        btnIniciarSesion.setOnClickListener {
            iniciarSesion()
        }

        txtIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun iniciarSesion() {
        val email = edtEmailLogin.text.toString().trim()
        val password = edtPasswordLogin.text.toString().trim()

        if (email.isEmpty()) {
            edtEmailLogin.error = "Ingresa tu email"
            return
        }

        if (password.isEmpty()) {
            edtPasswordLogin.error = "Ingresa tu contraseña"
            return
        }

        lifecycleScope.launch {
            try {
                val response = usuarioRepository.login(email, password)

                if (response.isSuccessful && response.body() != null) {
                    val usuario = response.body()!!

                    Toast.makeText(
                        this@LoginActivity,
                        "Bienvenido ${usuario.nombre}",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Correo o contraseña incorrectos",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}