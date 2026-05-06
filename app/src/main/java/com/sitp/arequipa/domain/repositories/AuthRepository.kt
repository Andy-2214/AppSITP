package com.sitp.arequipa.domain.repositories

import com.sitp.arequipa.domain.entities.User

/**
 * Contrato del repositorio de autenticación.
 * La implementación concreta vive en infrastructure/firebase.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String)
    suspend fun register(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    )
    suspend fun logout()
    suspend fun resetPassword(email: String)
    fun getCurrentUserId(): String?
    fun getCurrentUserEmail(): String?
}
