package com.sitp.arequipa.domain.entities

/**
 * Entidad: Comentario ciudadano sobre una ruta oficial (HU-21).
 * Requiere moderación por administrador antes de ser visible.
 */
data class Comment(
    val id: String = "",
    val routeId: String = "",
    val userId: String = "",
    val texto: String = "",
    val estado: EstadoComentario = EstadoComentario.PENDIENTE,
    val destacado: Boolean = false,
    val fecha: Long = 0L
)

enum class EstadoComentario {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
