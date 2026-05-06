package com.sitp.arequipa.application.usecases.search

import com.sitp.arequipa.domain.entities.SearchHistory
import com.sitp.arequipa.domain.repositories.SearchHistoryRepository

/**
 * Caso de uso: Guardar búsqueda en el historial automáticamente (HU-18).
 */
class SaveSearchHistoryUseCase(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke(entry: SearchHistory) {
        repository.saveSearch(entry)
    }
}
