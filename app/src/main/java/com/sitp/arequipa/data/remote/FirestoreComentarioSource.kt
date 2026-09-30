package com.sitp.arequipa.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.domain.model.Comentario
import kotlinx.coroutines.tasks.await

/**
 * Fuente de datos remota para comentarios de rutas en Firestore.
 */
class FirestoreComentarioSource {

    private val db = FirebaseFirestore.getInstance()

    suspend fun publicarComentario(
        uid: String,
        rutaId: String,
        rutaNombre: String,
        rutaCodigo: String,
        texto: String
    ) {
        // Obtener nombre del usuario desde Firestore
        val userDoc = db.collection("usuarios")
            .document(uid)
            .get()
            .await()
        val nombreUsuario = userDoc.getString("nombre") ?: "Usuario"

        val comentario = hashMapOf(
            "usuarioId" to uid,
            "nombreUsuario" to nombreUsuario,
            "rutaId" to rutaId,
            "rutaNombre" to rutaNombre,
            "texto" to texto,
            "fecha" to Timestamp.now(),
            "estado" to "aprobado",
            "rutaCodigo" to rutaCodigo,
            "destacado" to false
        )

        db.collection("comentarios")
            .add(comentario)
            .await()
    }

    suspend fun cargarComentarios(rutaId: String): List<Comentario> {
        val snapshot = db.collection("comentarios")
            .whereEqualTo("rutaId", rutaId)
            .whereEqualTo("estado", "aprobado")
            .get()
            .await()

        return snapshot.documents.map { doc ->
            Comentario(
                id = doc.id,
                usuarioId = doc.getString("usuarioId") ?: "",
                nombreUsuario = doc.getString("nombreUsuario") ?: "",
                rutaId = doc.getString("rutaId") ?: "",
                rutaNombre = doc.getString("rutaNombre") ?: "",
                texto = doc.getString("texto") ?: "",
                fecha = doc.getTimestamp("fecha")?.toDate()?.time,
                estado = doc.getString("estado") ?: "pendiente",
                destacado = doc.getBoolean("destacado") ?: false
            )
        }.sortedWith(
            compareByDescending<Comentario> { it.destacado }
                .thenByDescending { it.fecha ?: 0L }
        )
    }
}
