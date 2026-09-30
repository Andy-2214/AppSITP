package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Comentario
import com.sitp.arequipa.domain.repository.ComentarioRepository

/** Carga los comentarios aprobados de una ruta (HU-21). */
class CargarComentariosUseCase(
    private val comentarioRepository: ComentarioRepository
) {
    suspend operator fun invoke(rutaId: String): List<Comentario> =
        comentarioRepository.cargarComentarios(rutaId)
}
