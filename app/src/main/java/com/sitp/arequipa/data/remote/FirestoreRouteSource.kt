package com.sitp.arequipa.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRouteSource {

    private val db = FirebaseFirestore.getInstance()

    suspend fun descargarRutas(): List<DocumentSnapshot> {
        val snapshot = db.collection("rutas").get().await()
        return snapshot.documents
    }

    /**
     * Lee el contador metadatos/version (1 sola lectura).
     * Devuelve null si el documento no existe o no tiene el campo "rutas".
     */
    suspend fun leerVersionRutas(): Long? {
        val doc = db.collection("metadatos").document("version").get().await()
        return if (doc.exists()) doc.getLong("rutas") else null
    }

    suspend fun descargarCuencas(): List<DocumentSnapshot> {
        val snapshot = db.collection("cuencas").get().await()
        return snapshot.documents
    }
}
