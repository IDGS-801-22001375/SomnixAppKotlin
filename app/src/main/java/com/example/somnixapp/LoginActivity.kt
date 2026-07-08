package com.example.somnixapp

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.somnixapp.repository.UsuarioRepository
import com.example.somnixapp.utils.GoogleAuthHelper
import com.example.somnixapp.utils.SessionManager
import com.example.somnixapp.utils.SocialAuthManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var edtEmailLogin: EditText
    private lateinit var edtPasswordLogin: EditText
    private lateinit var btnIniciarSesion: Button
    //private lateinit var txtIrRegistro: TextView
    private lateinit var iconEye: ImageView

    private var passwordVisible = false
    private val usuarioRepository = UsuarioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        edtEmailLogin = findViewById(R.id.edtEmailLogin)
        edtPasswordLogin = findViewById(R.id.edtPasswordLogin)
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion)
        //txtIrRegistro = findViewById(R.id.txtIrRegistro)
        iconEye = findViewById(R.id.iconEyeLogin)

        configurarPassword()
        configurarBotones()
    }

    private fun configurarPassword() {
        iconEye.isClickable = true
        iconEye.isFocusable = true
        iconEye.bringToFront()

        iconEye.setOnClickListener {
            passwordVisible = !passwordVisible

            if (passwordVisible) {
                edtPasswordLogin.transformationMethod =
                    HideReturnsTransformationMethod.getInstance()
                iconEye.setImageResource(R.mipmap.visible)
            } else {
                edtPasswordLogin.transformationMethod =
                    PasswordTransformationMethod.getInstance()
                iconEye.setImageResource(R.mipmap.invisible)
            }

            edtPasswordLogin.setSelection(edtPasswordLogin.text.length)
        }
    }

    private fun configurarBotones() {
        btnIniciarSesion.setOnClickListener {
            iniciarSesion()
        }

        /*txtIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }*/
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

                    sessionManager.guardarSesion(usuario)

                    val tokenGuardado = sessionManager.obtenerToken()
                    Log.d("TOKEN_GUARDADO", tokenGuardado ?: "No hay token")

                    val mensaje = usuarioRepository.obtenerMensajeError(response)

                    Toast.makeText(
                        this@LoginActivity,
                        mensaje,
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