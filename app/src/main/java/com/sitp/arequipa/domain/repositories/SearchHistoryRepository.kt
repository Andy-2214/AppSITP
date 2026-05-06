package com.sitp.arequipa.domain.repositories

import com.sitp.arequipa.domain.entities.SearchHistory

/**
 * Contrato del repositorio de historial de búsquedas (HU-22).
 * La implementación concreta vive en infrastructure/firebase.
 */
interface SearchHistoryRepository {
    suspend fun saveSearch(entry: SearchHistory)
    suspend fun getHistory(userId: String): List<SearchHistory>
    suspend fun deleteEntry(id: String)
    suspend fun clearHistory(userId: String)
}
