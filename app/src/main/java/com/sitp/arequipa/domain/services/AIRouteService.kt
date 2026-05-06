package com.sitp.arequipa.domain.services

import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.domain.entities.SearchResult

/**
 * Interfaz del servicio de consultas en lenguaje natural con Gemini AI (HU-17/18).
 * La implementación concreta vive en infrastructure/ai.
 */
interface AIRouteService {
    suspend fun queryWithNaturalLanguage(
        query: String,
        origin: LatLng,
        destination: LatLng,
        criterio: String
    ): SearchResult?
}
