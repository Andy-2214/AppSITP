package com.sitp.arequipa.application.usecases.auth

import com.sitp.arequipa.domain.repositories.AuthRepository

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) {
        authRepository.login(email, password)
    }
}
