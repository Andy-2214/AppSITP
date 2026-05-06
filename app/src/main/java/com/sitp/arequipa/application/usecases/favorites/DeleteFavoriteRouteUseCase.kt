package com.sitp.arequipa.application.usecases.favorites

import com.sitp.arequipa.domain.repositories.FavoriteRepository

/**
 * Caso de uso: Eliminar una ruta favorita (HU-20).
 */
class DeleteFavoriteRouteUseCase(private val repository: FavoriteRepository) {
    suspend operator fun invoke(favoriteId: String) {
        repository.deleteFavorite(favoriteId)
    }
}
