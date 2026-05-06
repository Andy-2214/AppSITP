package com.sitp.arequipa.domain.repositories

import com.sitp.arequipa.domain.entities.User

/**
 * Contrato del repositorio de datos del ciudadano.
 * La implementación concreta vive en infrastructure/firebase.
 */
interface UserRepository {
    suspend fun getUser(uid: String): User?
    suspend fun updateNombre(uid: String, nombre: String)
}
