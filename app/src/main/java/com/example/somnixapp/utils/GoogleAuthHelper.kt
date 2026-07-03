package com.example.somnixapp.utils

import android.app.Activity
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.somnixapp.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuthHelper(
    private val activity: Activity
) {
    suspend fun obtenerIdTokenGoogle(): String? {
        return try {
            val credentialManager = CredentialManager.create(activity)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.google_web_client_id))
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = GoogleIdTokenCredential
                .createFrom(result.credential.data)

            credential.idToken

        } catch (e: GetCredentialException) {
            Toast.makeText(activity, "No se pudo iniciar con Google", Toast.LENGTH_LONG).show()
            null
        } catch (e: Exception) {
            Toast.makeText(activity, "Error Google: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }
}