package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.AuthRepository

class LogoutUseCase(private val authRepository: AuthRepository) {
    operator fun invoke() = authRepository.logout()
}