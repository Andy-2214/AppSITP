package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.AuthRepository

class ReenviarVerificacionUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() = authRepository.reenviarVerificacion()
}