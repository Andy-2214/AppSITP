package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.UsuarioPerfil
import com.sitp.arequipa.domain.repository.UsuarioRepository

class ObtenerPerfilUseCase(private val repository: UsuarioRepository) {
    suspend operator fun invoke(uid: String): UsuarioPerfil = repository.obtenerPerfil(uid)
}
