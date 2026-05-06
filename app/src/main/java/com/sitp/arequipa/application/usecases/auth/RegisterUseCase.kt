package com.sitp.arequipa.application.usecases.auth

import com.sitp.arequipa.domain.repositories.AuthRepository

class RegisterUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    ) {
        authRepository.register(nombre, email, password, genero, edad, distrito)
    }
}
