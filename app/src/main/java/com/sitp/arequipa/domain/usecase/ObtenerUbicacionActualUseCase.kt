package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.repository.UbicacionRepository

/** Ubicación actual del teléfono (para centrar el mapa con el botón "Mi ubicación"). */
class ObtenerUbicacionActualUseCase(private val ubicacionRepository: UbicacionRepository) {
    suspend operator fun invoke(): Coordenada? = ubicacionRepository.ubicacionActual()
}
