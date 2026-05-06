package com.sitp.arequipa.infrastructure.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sitp.arequipa.domain.entities.SearchHistory
import com.sitp.arequipa.domain.repositories.SearchHistoryRepository
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firebase de SearchHistoryRepository.
 * Guarda y consulta el historial de búsquedas en la colección "historial".
 */
class FirebaseHistoryRepository : SearchHistoryRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun saveSearch(entry: SearchHistory) {
        val data = hashMapOf(
            "userId" to entry.userId,
            "origenDescripcion" to entry.origenDescripcion,
            "destinoDescripcion" to entry.destinoDescripcion,
            "criterio" to entry.criterio,
            "usoBusquedaIA" to entry.usoBusquedaIA,
            "fecha" to com.google.firebase.Timestamp.now()
        )
        db.collection("historial").add(data).await()
    }

    override suspend fun getHistory(userId: String): List<SearchHistory> {
        val snapshot = db.collection("historial")
            .whereEqualTo("userId", userId)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            SearchHistory(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                origenDescripcion = doc.getString("origenDescripcion") ?: "",
                destinoDescripcion = doc.getString("destinoDescripcion") ?: "",
                criterio = doc.getString("criterio") ?: "tiempo",
                usoBusquedaIA = doc.getBoolean("usoBusquedaIA") ?: false,
                fecha = doc.getTimestamp("fecha")?.seconds ?: 0L
            )
        }
    }

    override suspend fun deleteEntry(id: String) {
        db.collection("historial").document(id).delete().await()
    }

    override suspend fun clearHistory(userId: String) {
        val snapshot = db.collection("historial")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        val batch = db.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
