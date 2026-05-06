package com.sitp.arequipa.domain.repositories

import com.sitp.arequipa.domain.entities.FavoriteRoute

/**
 * Contrato del repositorio de rutas favoritas (HU-20).
 * La implementación concreta vive en infrastructure/firebase.
 */
interface FavoriteRepository {
    suspend fun saveFavorite(favorite: FavoriteRoute)
    suspend fun getFavorites(userId: String): List<FavoriteRoute>
    suspend fun deleteFavorite(id: String)
    suspend fun incrementUsageCount(userId: String, origenDescripcion: String, destinoDescripcion: String): Int
}
