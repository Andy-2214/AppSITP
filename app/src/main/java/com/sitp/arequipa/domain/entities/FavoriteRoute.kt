package com.sitp.arequipa.domain.entities

/**
 * Entidad: Ruta favorita guardada por el usuario.
 * Se guarda automáticamente al buscar la misma ruta 3 veces o manualmente.
 */
data class FavoriteRoute(
    val id: String = "",
    val userId: String = "",
    val nombre: String = "",
    val origenDescripcion: String = "",
    val destinoDescripcion: String = "",
    val contadorUsos: Int = 0,
    val fechaUltimoUso: Long = 0L
)
