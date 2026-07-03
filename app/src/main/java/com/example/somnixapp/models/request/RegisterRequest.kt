package com.example.somnixapp.models.request

data class RegisterRequest (
    val nombre: String,
    val email: String,
    val password: String
)