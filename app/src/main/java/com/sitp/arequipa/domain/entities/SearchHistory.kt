package com.sitp.arequipa.domain.entities

/**
 * Entidad: Entrada del historial de búsquedas del usuario (HU-22).
 */
data class SearchHistory(
    val id: String = "",
    val userId: String = "",
    val origenDescripcion: String = "",
    val destinoDescripcion: String = "",
    val criterio: String = "tiempo",  // "tiempo" | "costo" | "transbordos"
    val usoBusquedaIA: Boolean = false,
    val fecha: Long = 0L
)
