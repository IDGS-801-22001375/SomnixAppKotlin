package com.example.somnixapp.models.response

data class AuthResponse(
    val id: String,
    val nombre: String,
    val email: String,
    val rol: String,
    val token: String
)