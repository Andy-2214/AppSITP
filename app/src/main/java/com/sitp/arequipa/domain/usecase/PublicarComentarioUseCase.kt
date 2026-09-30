package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.ComentarioRepository

/**
 * Publica un comentario sobre una ruta (HU-21).
 * Valida que el texto tenga al menos 10 caracteres antes de enviarlo.
 */
class PublicarComentarioUseCase(
    private val comentarioRepository: ComentarioRepository
) {
    suspend operator fun invoke(
        uid: String,
        rutaId: String,
        rutaNombre: String,
        rutaCodigo: String,
        texto: String
    ) {
        if (texto.length < 10) {
            throw IllegalArgumentException(
                "Tu comentario es muy corto. Agrega más detalles para ayudar a otros ciudadanos."
            )
        }
        comentarioRepository.publicarComentario(uid, rutaId, rutaNombre, rutaCodigo, texto)
    }
}
