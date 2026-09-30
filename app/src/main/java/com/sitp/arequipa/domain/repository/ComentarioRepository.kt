package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.Comentario

interface ComentarioRepository {
    suspend fun publicarComentario(
        uid: String,
        rutaId: String,
        rutaNombre: String,
        rutaCodigo: String,
        texto: String
    )
    suspend fun cargarComentarios(rutaId: String): List<Comentario>
}
