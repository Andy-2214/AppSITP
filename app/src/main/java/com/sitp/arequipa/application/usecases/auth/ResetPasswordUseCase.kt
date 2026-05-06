package com.sitp.arequipa.application.usecases.auth

import com.sitp.arequipa.domain.repositories.AuthRepository

class ResetPasswordUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String) {
        authRepository.resetPassword(email)
    }
}
