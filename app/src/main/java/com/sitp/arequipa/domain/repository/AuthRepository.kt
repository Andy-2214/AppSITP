package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.LoginResult
import com.sitp.arequipa.domain.model.SesionActual

interface AuthRepository {
    suspend fun login(email: String, password: String): LoginResult
    suspend fun register(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    )
    suspend fun recuperarPassword(email: String)
    suspend fun reenviarVerificacion()
    fun logout()
    fun sesionActual(): SesionActual
}