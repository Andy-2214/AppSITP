package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.repository.UbicacionRepository
import kotlinx.coroutines.flow.Flow

/** Ubicaciones del teléfono mientras el mapa está visible (punto azul). */
class SeguirUbicacionUseCase(private val ubicacionRepository: UbicacionRepository) {
    operator fun invoke(): Flow<Coordenada> = ubicacionRepository.seguirUbicacion()
}
