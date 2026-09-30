package com.sitp.arequipa.domain.model

data class Comentario(
    val id: String = "",
    val usuarioId: String = "",
    val nombreUsuario: String = "",
    val rutaId: String = "",
    val rutaNombre: String = "",
    val texto: String = "",
    val fecha: Long? = null,
    val estado: String = "pendiente",
    val destacado: Boolean = false
)