package com.sitp.arequipa.domain.services

import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.domain.entities.SearchResult

/**
 * Interfaz del servicio de optimización de rutas con algoritmo propio (HU-16/18).
 * Criterios: "tiempo" | "costo" | "transbordos"
 * La implementación concreta vive en infrastructure/algorithm.
 */
interface RouteOptimizerService {
    suspend fun findOptimalRoute(
        origin: LatLng,
        destination: LatLng,
        criterio: String
    ): SearchResult?
}
