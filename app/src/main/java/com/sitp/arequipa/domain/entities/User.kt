package com.sitp.arequipa.domain.entities

/**
 * Entidad: Ciudadano registrado en la aplicación.
 */
data class User(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val genero: String = "",
    val edad: Int = 0,
    val distrito: String = "",
    val fechaRegistro: Long = 0L
)
