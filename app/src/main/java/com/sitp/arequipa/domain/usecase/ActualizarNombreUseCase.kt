package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.UsuarioRepository

class ActualizarNombreUseCase(private val repository: UsuarioRepository) {
    suspend operator fun invoke(uid: String, nuevoNombre: String) = repository.actualizarNombre(uid, nuevoNombre)
}
