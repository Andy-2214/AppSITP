package com.sitp.arequipa.infrastructure.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.domain.entities.User
import com.sitp.arequipa.domain.repositories.UserRepository
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firebase de UserRepository.
 * Lee y actualiza el perfil del ciudadano en Firestore.
 */
class FirebaseUserRepository : UserRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun getUser(uid: String): User? {
        val doc = db.collection("usuarios").document(uid).get().await()
        if (!doc.exists()) return null
        return User(
            uid = uid,
            nombre = doc.getString("nombre") ?: "",
            email = doc.getString("email") ?: "",
            genero = doc.getString("genero") ?: "",
            edad = doc.getLong("edad")?.toInt() ?: 0,
            distrito = doc.getString("distrito") ?: "",
            fechaRegistro = doc.getTimestamp("fechaRegistro")?.seconds ?: 0L
        )
    }

    override suspend fun updateNombre(uid: String, nombre: String) {
        db.collection("usuarios").document(uid).update("nombre", nombre).await()
    }
}
