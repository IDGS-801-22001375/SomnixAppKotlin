package com.example.somnixapp.utils

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.example.somnixapp.HomeActivity
import com.example.somnixapp.repository.UsuarioRepository

class SocialAuthManager(
    private val activity: Activity
) {
    private val usuarioRepository = UsuarioRepository()

    suspend fun loginConGoogle(idToken: String) {
        try {
            val response = usuarioRepository.loginGoogle(idToken)

            if (response.isSuccessful && response.body() != null) {
                val usuario = response.body()!!

                val sessionManager = SessionManager(activity)
                sessionManager.guardarToken(usuario.token)

                Toast.makeText(
                    activity,
                    "Bienvenido ${usuario.nombre}",
                    Toast.LENGTH_SHORT
                ).show()

                activity.startActivity(Intent(activity, HomeActivity::class.java))
                activity.finish()
            } else {
                Toast.makeText(
                    activity,
                    "No se pudo iniciar sesión con Google",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {
            Toast.makeText(
                activity,
                "Error de conexión Google: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}