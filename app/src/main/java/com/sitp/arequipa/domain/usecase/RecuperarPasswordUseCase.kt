package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.AuthRepository

class RecuperarPasswordUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String) = authRepository.recuperarPassword(email)
}