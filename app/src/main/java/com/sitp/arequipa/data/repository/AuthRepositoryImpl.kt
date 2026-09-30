package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.remote.FirebaseAuthSource
import com.sitp.arequipa.domain.model.LoginResult
import com.sitp.arequipa.domain.model.SesionActual
import com.sitp.arequipa.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val authSource: FirebaseAuthSource = FirebaseAuthSource()
) : AuthRepository {

    override suspend fun login(email: String, password: String): LoginResult =
        withContext(Dispatchers.IO) { authSource.login(email, password) }

    override suspend fun register(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    ) = withContext(Dispatchers.IO) {
        authSource.register(nombre, email, password, genero, edad, distrito)
    }

    override suspend fun recuperarPassword(email: String) =
        withContext(Dispatchers.IO) { authSource.recuperarPassword(email) }

    override suspend fun reenviarVerificacion() =
        withContext(Dispatchers.IO) { authSource.reenviarVerificacion() }

    override fun logout() = authSource.logout()

    override fun sesionActual(): SesionActual = SesionActual(
        uid = authSource.obtenerUidActual(),
        email = authSource.obtenerEmailActual(),
        emailVerificado = authSource.obtenerEmailVerificado()
    )
}