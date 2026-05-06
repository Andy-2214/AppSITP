package com.sitp.arequipa.application.usecases.routes

import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.domain.entities.SearchResult
import com.sitp.arequipa.domain.services.AIRouteService

/**
 * Caso de uso: Buscar ruta con consulta en lenguaje natural via Gemini AI (HU-17/18).
 */
class QueryRouteWithAIUseCase(private val aiService: AIRouteService) {
    suspend operator fun invoke(
        query: String,
        origin: LatLng,
        destination: LatLng,
        criterio: String = "tiempo"
    ): SearchResult? {
        return aiService.queryWithNaturalLanguage(query, origin, destination, criterio)
    }
}
