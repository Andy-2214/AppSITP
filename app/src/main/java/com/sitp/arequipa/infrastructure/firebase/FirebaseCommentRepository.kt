package com.sitp.arequipa.infrastructure.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sitp.arequipa.domain.entities.Comment
import com.sitp.arequipa.domain.entities.EstadoComentario
import com.sitp.arequipa.domain.repositories.CommentRepository
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firebase de CommentRepository.
 * Los comentarios se guardan en la colección "comentarios" de Firestore.
 */
class FirebaseCommentRepository : CommentRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun submitComment(comment: Comment) {
        val data = hashMapOf(
            "routeId" to comment.routeId,
            "userId" to comment.userId,
            "texto" to comment.texto,
            "estado" to EstadoComentario.PENDIENTE.name,
            "destacado" to false,
            "fecha" to com.google.firebase.Timestamp.now()
        )
        db.collection("comentarios").add(data).await()
    }

    override suspend fun getApprovedComments(routeId: String): List<Comment> {
        val snapshot = db.collection("comentarios")
            .whereEqualTo("routeId", routeId)
            .whereEqualTo("estado", EstadoComentario.APROBADO.name)
            .orderBy("destacado", Query.Direction.DESCENDING)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            Comment(
                id = doc.id,
                routeId = doc.getString("routeId") ?: "",
                userId = doc.getString("userId") ?: "",
                texto = doc.getString("texto") ?: "",
                estado = EstadoComentario.APROBADO,
                destacado = doc.getBoolean("destacado") ?: false,
                fecha = doc.getTimestamp("fecha")?.seconds ?: 0L
            )
        }
    }
}
