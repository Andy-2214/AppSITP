package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.AuthRepository

class VerificarSesionUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Boolean {
        val sesion = authRepository.sesionActual()
        return sesion.uid != null && sesion.emailVerificado
    }
}