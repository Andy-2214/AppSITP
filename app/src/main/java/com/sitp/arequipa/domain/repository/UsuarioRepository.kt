package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.UsuarioPerfil

interface UsuarioRepository {
    suspend fun obtenerPerfil(uid: String): UsuarioPerfil
    suspend fun actualizarNombre(uid: String, nuevoNombre: String)
}
