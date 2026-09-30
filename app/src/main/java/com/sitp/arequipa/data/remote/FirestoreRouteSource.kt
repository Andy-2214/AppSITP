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
}
