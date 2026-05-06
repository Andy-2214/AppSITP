package com.sitp.arequipa.application.usecases.routes

import com.sitp.arequipa.domain.entities.Route
import com.sitp.arequipa.domain.repositories.RouteRepository

/**
 * Caso de uso: Obtener listado de rutas oficiales MPA (HU-14).
 */
class GetOfficialRoutesUseCase(private val routeRepository: RouteRepository) {
    suspend operator fun invoke(): List<Route> {
        return routeRepository.getRoutes()
    }
}
