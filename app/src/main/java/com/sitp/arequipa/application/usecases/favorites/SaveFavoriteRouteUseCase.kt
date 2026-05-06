package com.sitp.arequipa.application.usecases.favorites

import com.sitp.arequipa.domain.entities.FavoriteRoute
import com.sitp.arequipa.domain.repositories.FavoriteRepository

/**
 * Caso de uso: Guardar ruta favorita (manual o automático por frecuencia) (HU-20).
 * Si el contador de usos llega a 3, se guarda automáticamente.
 */
class SaveFavoriteRouteUseCase(private val repository: FavoriteRepository) {
    suspend operator fun invoke(favorite: FavoriteRoute) {
        repository.saveFavorite(favorite)
    }

    /**
     * Incrementa el contador y devuelve el nuevo valor.
     * Si devuelve >= 3, el caller debe guardar como favorita.
     */
    suspend fun incrementAndCheck(
        userId: String,
        origenDescripcion: String,
        destinoDescripcion: String
    ): Int {
        return repository.incrementUsageCount(userId, origenDescripcion, destinoDescripcion)
    }
}
