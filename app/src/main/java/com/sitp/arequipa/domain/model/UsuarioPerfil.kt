package com.sitp.arequipa.domain.model

data class UsuarioPerfil(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val genero: String = "",
    val edad: String = "",
    val distrito: String = "",
    val comentariosCount: Int = 0
)
