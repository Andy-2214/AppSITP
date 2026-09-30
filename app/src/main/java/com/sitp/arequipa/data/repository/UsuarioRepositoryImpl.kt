package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.remote.FirebaseAuthSource
import com.sitp.arequipa.domain.model.UsuarioPerfil
import com.sitp.arequipa.domain.repository.UsuarioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsuarioRepositoryImpl(
    private val authSource: FirebaseAuthSource = FirebaseAuthSource()
) : UsuarioRepository {
    override suspend fun obtenerPerfil(uid: String): UsuarioPerfil =
        withContext(Dispatchers.IO) { authSource.obtenerPerfil(uid) }

    override suspend fun actualizarNombre(uid: String, nuevoNombre: String) =
        withContext(Dispatchers.IO) { authSource.actualizarNombre(uid, nuevoNombre) }
}