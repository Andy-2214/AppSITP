package com.sitp.arequipa.infrastructure.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.domain.repositories.AuthRepository
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firebase de AuthRepository.
 * Maneja autenticación y creación del perfil del ciudadano en Firestore.
 */
class FirebaseAuthRepository : AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun register(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    ) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val usuario = hashMapOf(
            "nombre" to nombre,
            "email" to email,
            "genero" to genero,
            "edad" to edad,
            "distrito" to distrito,
            "fechaRegistro" to com.google.firebase.Timestamp.now()
        )
        db.collection("usuarios")
            .document(result.user!!.uid)
            .set(usuario)
            .await()
        result.user!!.sendEmailVerification().await()
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override fun getCurrentUserEmail(): String? = auth.currentUser?.email
}
