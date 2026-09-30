package com.sitp.arequipa.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.domain.model.UsuarioPerfil
import com.sitp.arequipa.domain.model.LoginResult
import kotlinx.coroutines.tasks.await

/**
 * Fuente de datos para autenticación con Firebase Auth y creación del perfil en Firestore.
 * Extrae la lógica de Firebase de AuthViewModel para cumplir con Clean Architecture.
 */
class FirebaseAuthSource {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun login(email: String, password: String): LoginResult {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        println("DEBUG isEmailVerified: ${result.user?.isEmailVerified}")
        return if (result.user?.isEmailVerified == false) {
            auth.signOut()
            LoginResult.EmailNoVerificado
        } else {
            LoginResult.Exito
        }
    }

    suspend fun register(
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
            "fechaRegistro" to Timestamp.now()
        )
        db.collection("usuarios")
            .document(result.user!!.uid)
            .set(usuario)
            .await()
        result.user!!.sendEmailVerification().await()
    }

    suspend fun recuperarPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun reenviarVerificacion() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    fun logout() {
        auth.signOut()
    }

    fun obtenerUidActual(): String? = auth.currentUser?.uid

    fun obtenerEmailActual(): String? = auth.currentUser?.email

    fun obtenerEmailVerificado(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun obtenerPerfil(uid: String): UsuarioPerfil {
        val doc = db.collection("usuarios").document(uid).get().await()
        val comentariosSnap = db.collection("comentarios")
            .whereEqualTo("usuarioId", uid)
            .get()
            .await()

        return UsuarioPerfil(
            uid = uid,
            nombre = doc.getString("nombre") ?: "",
            email = doc.getString("email") ?: auth.currentUser?.email ?: "",
            genero = doc.getString("genero") ?: "",
            edad = doc.getLong("edad")?.toString() ?: "",
            distrito = doc.getString("distrito") ?: "",
            comentariosCount = comentariosSnap.size()
        )
    }

    suspend fun actualizarNombre(uid: String, nuevoNombre: String) {
        db.collection("usuarios").document(uid)
            .update("nombre", nuevoNombre)
            .await()
    }
}