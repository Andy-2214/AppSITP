package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.RutaFavorita
import com.sitp.arequipa.domain.repository.HistorialRepository

/** Carga los favoritos del usuario (HU-20). */
class CargarFavoritosUseCase(
    private val historialRepository: HistorialRepository
) {
    suspend operator fun invoke(uid: String): List<RutaFavorita> =
        historialRepository.cargarFavoritos(uid)
}
