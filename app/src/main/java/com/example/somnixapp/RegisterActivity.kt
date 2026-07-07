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
import android.widget.ImageView
import android.text.method.PasswordTransformationMethod
import android.text.method.HideReturnsTransformationMethod
import android.widget.ImageButton
import com.example.somnixapp.utils.GoogleAuthHelper
import com.example.somnixapp.utils.SocialAuthManager

class RegisterActivity : AppCompatActivity() {

    private lateinit var edtNombreRegistro: EditText
    private lateinit var edtEmailRegistro: EditText
    private lateinit var edtPasswordRegistro: EditText
    private lateinit var btnRegistrarse: Button
    private lateinit var txtIniciarSesion: TextView
    private lateinit var iconEye: ImageView
    private var passwordVisible = false
    private lateinit var btnGoogle: ImageButton

    private val usuarioRepository = UsuarioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        edtNombreRegistro = findViewById(R.id.edtNombre)
        edtEmailRegistro = findViewById(R.id.edtEmail)
        edtPasswordRegistro = findViewById(R.id.edtPassword)

        iconEye = findViewById(R.id.iconEye)

        iconEye.isClickable = true
        iconEye.isFocusable = true
        iconEye.bringToFront()

        iconEye.setOnClickListener {
            passwordVisible = !passwordVisible

            if (passwordVisible) {
                edtPasswordRegistro.transformationMethod =
                    HideReturnsTransformationMethod.getInstance()

                iconEye.setImageResource(R.mipmap.visible)
            } else {
                edtPasswordRegistro.transformationMethod =
                    PasswordTransformationMethod.getInstance()

                iconEye.setImageResource(R.mipmap.invisible)
            }

            edtPasswordRegistro.setSelection(edtPasswordRegistro.text.length)
        }

        btnRegistrarse = findViewById(R.id.btnRegistrarme)
        txtIniciarSesion = findViewById(R.id.txtIniciarSesion)

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

        btnRegistrarse.setOnClickListener {
            registrarUsuario()
        }

        txtIniciarSesion.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registrarUsuario() {
        val nombre = edtNombreRegistro.text.toString().trim()
        val email = edtEmailRegistro.text.toString().trim()
        val password = edtPasswordRegistro.text.toString().trim()

        if (nombre.isEmpty()) {
            edtNombreRegistro.error = "Ingresa tu nombre"
            return
        }

        if (email.isEmpty()) {
            edtEmailRegistro.error = "Ingresa tu email"
            return
        }

        if (password.isEmpty()) {
            edtPasswordRegistro.error = "Ingresa tu contraseña"
            return
        }

        lifecycleScope.launch {
            try {
                val response = usuarioRepository.registrar(nombre, email, password)

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Usuario registrado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finish()
                } else {
                    val mensaje = usuarioRepository.obtenerMensajeError(response)

                    Toast.makeText(
                        this@RegisterActivity,
                        mensaje,
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@RegisterActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}