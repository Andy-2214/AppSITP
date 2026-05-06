package com.sitp.arequipa.application.usecases.comments

import com.sitp.arequipa.domain.entities.Comment
import com.sitp.arequipa.domain.repositories.CommentRepository

/**
 * Caso de uso: Enviar comentario sobre una ruta (HU-21).
 * El comentario queda en estado PENDIENTE hasta que el admin lo apruebe.
 */
class SubmitCommentUseCase(private val repository: CommentRepository) {
    suspend operator fun invoke(comment: Comment) {
        require(comment.texto.length >= 10) {
            "Tu comentario es muy corto. Agrega más detalles para ayudar a otros ciudadanos"
        }
        repository.submitComment(comment)
    }
}
