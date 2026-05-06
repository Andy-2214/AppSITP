package com.sitp.arequipa.application.usecases.comments

import com.sitp.arequipa.domain.entities.Comment
import com.sitp.arequipa.domain.repositories.CommentRepository

/**
 * Caso de uso: Obtener comentarios aprobados de una ruta (HU-21).
 */
class GetRouteCommentsUseCase(private val repository: CommentRepository) {
    suspend operator fun invoke(routeId: String): List<Comment> {
        return repository.getApprovedComments(routeId)
    }
}
