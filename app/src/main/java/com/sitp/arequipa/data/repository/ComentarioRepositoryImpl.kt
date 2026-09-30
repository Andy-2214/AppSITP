package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.remote.FirestoreComentarioSource
import com.sitp.arequipa.domain.model.Comentario
import com.sitp.arequipa.domain.repository.ComentarioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComentarioRepositoryImpl(
    private val remoteSource: FirestoreComentarioSource = FirestoreComentarioSource()
) : ComentarioRepository {

    override suspend fun publicarComentario(
        uid: String,
        rutaId: String,
        rutaNombre: String,
        rutaCodigo: String,
        texto: String
    ) = withContext(Dispatchers.IO) {
        remoteSource.publicarComentario(uid, rutaId, rutaNombre, rutaCodigo, texto)
    }

    override suspend fun cargarComentarios(rutaId: String): List<Comentario> =
        withContext(Dispatchers.IO) { remoteSource.cargarComentarios(rutaId) }
}