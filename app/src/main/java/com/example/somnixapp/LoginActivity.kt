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
import android.view.View
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var edtEmailLogin: EditText
    private lateinit var edtPasswordLogin: EditText
    private lateinit var btnIniciarSesion: Button
    //private lateinit var txtIrRegistro: TextView
    private lateinit var iconEye: ImageView

    private var passwordVisible = false
    private val usuarioRepository = UsuarioRepository()

    private lateinit var contenedorCargando: FrameLayout
    private lateinit var scrollLogin: NestedScrollView

    private var iniciandoSesion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        edtEmailLogin = findViewById(R.id.edtEmailLogin)
        edtPasswordLogin = findViewById(R.id.edtPasswordLogin)
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion)
        iconEye = findViewById(R.id.iconEyeLogin)

        contenedorCargando = findViewById(R.id.contenedorCargando)
        scrollLogin = findViewById(R.id.scrollLogin)

        configurarPassword()
        configurarBotones()
        configurarScroll()
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
        if (iniciandoSesion) {
            return
        }

        val email = edtEmailLogin.text.toString().trim()

        // No uses trim() en la contraseña, porque un espacio
        // podría formar parte de una contraseña válida.
        val password = edtPasswordLogin.text.toString()

        edtEmailLogin.error = null
        edtPasswordLogin.error = null

        if (email.isEmpty()) {
            edtEmailLogin.error = "Ingresa tu correo electrónico"
            edtEmailLogin.requestFocus()
            return
        }

        if (password.isEmpty()) {
            edtPasswordLogin.error = "Ingresa tu contraseña"
            edtPasswordLogin.requestFocus()

            scrollLogin.post {
                scrollLogin.smoothScrollTo(
                    0,
                    edtPasswordLogin.bottom + 250
                )
            }

            return
        }

        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val response = usuarioRepository.login(email, password)

                if (response.isSuccessful) {
                    val usuario = response.body()

                    if (usuario == null) {
                        Toast.makeText(
                            this@LoginActivity,
                            "El servidor respondió sin información del usuario",
                            Toast.LENGTH_LONG
                        ).show()

                        return@launch
                    }

                    sessionManager.guardarSesion(usuario)

                    val tokenGuardado = sessionManager.obtenerToken()

                    Log.d(
                        "TOKEN_GUARDADO",
                        tokenGuardado ?: "No hay token"
                    )

                    Toast.makeText(
                        this@LoginActivity,
                        "Inicio de sesión correcto",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(
                        this@LoginActivity,
                        HomeActivity::class.java
                    )

                    startActivity(intent)
                    finish()

                } else {
                    val mensaje = when (response.code()) {
                        400 -> "Verifica los datos ingresados"
                        401 -> "Correo o contraseña incorrectos"
                        403 -> "Tu cuenta no tiene acceso"
                        404 -> "No se encontró el servicio de inicio de sesión"
                        500 -> "El servidor presentó un problema"
                        502, 503, 504 -> "El servidor está iniciando. Intenta nuevamente"
                        else -> "No fue posible iniciar sesión"
                    }

                    Toast.makeText(
                        this@LoginActivity,
                        mensaje,
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: java.net.SocketTimeoutException) {
                Toast.makeText(
                    this@LoginActivity,
                    "El servidor tardó demasiado en responder",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: java.net.UnknownHostException) {
                Toast.makeText(
                    this@LoginActivity,
                    "No se pudo conectar con el servidor. Revisa tu conexión",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Log.e("LOGIN_ERROR", "Error al iniciar sesión", e)

                Toast.makeText(
                    this@LoginActivity,
                    "Error de conexión: ${e.localizedMessage ?: "desconocido"}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                if (!isFinishing && !isDestroyed) {
                    mostrarCargando(false)
                }
            }
        }
    }

    private fun configurarScroll() {
        edtPasswordLogin.setOnFocusChangeListener { _, tieneFoco ->
            if (tieneFoco) {
                scrollLogin.postDelayed({
                    scrollLogin.smoothScrollTo(
                        0,
                        edtPasswordLogin.bottom + 250
                    )
                }, 250)
            }
        }
    }
    private fun mostrarCargando(mostrar: Boolean) {
        iniciandoSesion = mostrar

        contenedorCargando.visibility =
            if (mostrar) View.VISIBLE else View.GONE

        btnIniciarSesion.isEnabled = !mostrar
        edtEmailLogin.isEnabled = !mostrar
        edtPasswordLogin.isEnabled = !mostrar
        iconEye.isEnabled = !mostrar

        btnIniciarSesion.text =
            if (mostrar) "Iniciando..." else "Iniciar sesión"

        btnIniciarSesion.alpha =
            if (mostrar) 0.7f else 1f
    }
}