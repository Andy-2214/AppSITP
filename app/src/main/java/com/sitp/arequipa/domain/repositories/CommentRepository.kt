package com.sitp.arequipa.domain.repositories

import com.sitp.arequipa.domain.entities.Comment

/**
 * Contrato del repositorio de comentarios ciudadanos (HU-21).
 * La implementación concreta vive en infrastructure/firebase.
 */
interface CommentRepository {
    suspend fun submitComment(comment: Comment)
    suspend fun getApprovedComments(routeId: String): List<Comment>
}
