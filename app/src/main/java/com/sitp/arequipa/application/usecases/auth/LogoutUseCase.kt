package com.sitp.arequipa.application.usecases.auth

import com.sitp.arequipa.domain.repositories.AuthRepository

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() {
        authRepository.logout()
    }
}
