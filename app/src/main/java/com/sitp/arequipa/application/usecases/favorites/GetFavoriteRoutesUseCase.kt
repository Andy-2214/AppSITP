package com.sitp.arequipa.application.usecases.favorites

import com.sitp.arequipa.domain.entities.FavoriteRoute
import com.sitp.arequipa.domain.repositories.FavoriteRepository

/**
 * Caso de uso: Obtener lista de rutas favoritas del usuario (HU-20).
 */
class GetFavoriteRoutesUseCase(private val repository: FavoriteRepository) {
    suspend operator fun invoke(userId: String): List<FavoriteRoute> {
        return repository.getFavorites(userId)
    }
}
