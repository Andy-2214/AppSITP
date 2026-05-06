package com.sitp.arequipa.application.usecases.search

import com.sitp.arequipa.domain.entities.SearchHistory
import com.sitp.arequipa.domain.repositories.SearchHistoryRepository

/**
 * Caso de uso: Obtener historial de búsquedas del usuario (HU-22).
 */
class GetSearchHistoryUseCase(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke(userId: String): List<SearchHistory> {
        return repository.getHistory(userId)
    }
}
