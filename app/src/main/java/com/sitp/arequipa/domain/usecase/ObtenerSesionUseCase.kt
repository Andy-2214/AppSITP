package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.SesionActual
import com.sitp.arequipa.domain.repository.AuthRepository

class ObtenerSesionUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): SesionActual = authRepository.sesionActual()
}