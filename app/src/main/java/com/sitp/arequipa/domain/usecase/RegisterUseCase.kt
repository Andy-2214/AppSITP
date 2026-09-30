package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.AuthRepository

/** Registra un nuevo usuario con perfil completo (HU-02 auth). */
class RegisterUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    ) = authRepository.register(nombre, email, password, genero, edad, distrito)
}