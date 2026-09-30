package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.LoginResult
import com.sitp.arequipa.domain.repository.AuthRepository

/** Autentica al usuario con email y contraseña (HU-01 auth). */
class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): LoginResult =
        authRepository.login(email, password)
}