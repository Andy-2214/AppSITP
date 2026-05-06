package com.sitp.arequipa.infrastructure.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sitp.arequipa.domain.entities.FavoriteRoute
import com.sitp.arequipa.domain.repositories.FavoriteRepository
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firebase de FavoriteRepository.
 * Guarda rutas favoritas en la colección "favoritas".
 */
class FirebaseFavoriteRepository : FavoriteRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun saveFavorite(favorite: FavoriteRoute) {
        val data = hashMapOf(
            "userId" to favorite.userId,
            "nombre" to favorite.nombre,
            "origenDescripcion" to favorite.origenDescripcion,
            "destinoDescripcion" to favorite.destinoDescripcion,
            "contadorUsos" to favorite.contadorUsos,
            "fechaUltimoUso" to com.google.firebase.Timestamp.now()
        )
        if (favorite.id.isEmpty()) {
            db.collection("favoritas").add(data).await()
        } else {
            db.collection("favoritas").document(favorite.id).set(data).await()
        }
    }

    override suspend fun getFavorites(userId: String): List<FavoriteRoute> {
        val snapshot = db.collection("favoritas")
            .whereEqualTo("userId", userId)
            .orderBy("contadorUsos", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            FavoriteRoute(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                nombre = doc.getString("nombre") ?: "",
                origenDescripcion = doc.getString("origenDescripcion") ?: "",
                destinoDescripcion = doc.getString("destinoDescripcion") ?: "",
                contadorUsos = doc.getLong("contadorUsos")?.toInt() ?: 0,
                fechaUltimoUso = doc.getTimestamp("fechaUltimoUso")?.seconds ?: 0L
            )
        }
    }

    override suspend fun deleteFavorite(id: String) {
        db.collection("favoritas").document(id).delete().await()
    }

    override suspend fun incrementUsageCount(
        userId: String,
        origenDescripcion: String,
        destinoDescripcion: String
    ): Int {
        // Buscar si ya existe un contador para esta combinación
        val snapshot = db.collection("contadores_busqueda")
            .whereEqualTo("userId", userId)
            .whereEqualTo("origenDescripcion", origenDescripcion)
            .whereEqualTo("destinoDescripcion", destinoDescripcion)
            .get()
            .await()

        return if (snapshot.isEmpty) {
            val data = hashMapOf(
                "userId" to userId,
                "origenDescripcion" to origenDescripcion,
                "destinoDescripcion" to destinoDescripcion,
                "contador" to 1
            )
            db.collection("contadores_busqueda").add(data).await()
            1
        } else {
            val doc = snapshot.documents.first()
            val nuevoContador = (doc.getLong("contador") ?: 0L) + 1
            doc.reference.update("contador", nuevoContador).await()
            nuevoContador.toInt()
        }
    }
}
