package com.sitp.arequipa.application.usecases.routes

import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.domain.entities.SearchResult
import com.sitp.arequipa.domain.services.RouteOptimizerService

/**
 * Caso de uso: Buscar ruta óptima con algoritmo propio (HU-16/18).
 * Criterios disponibles: "tiempo" | "costo" | "transbordos"
 */
class FindOptimalRouteUseCase(private val optimizer: RouteOptimizerService) {
    suspend operator fun invoke(
        origin: LatLng,
        destination: LatLng,
        criterio: String = "tiempo"
    ): SearchResult? {
        return optimizer.findOptimalRoute(origin, destination, criterio)
    }
}
