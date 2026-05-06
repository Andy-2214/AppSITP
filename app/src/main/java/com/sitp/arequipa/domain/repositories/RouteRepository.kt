package com.sitp.arequipa.domain.repositories

import com.sitp.arequipa.domain.entities.Route

/**
 * Contrato del repositorio de rutas oficiales MPA.
 * La implementación concreta vive en infrastructure/firebase.
 */
interface RouteRepository {
    suspend fun getRoutes(): List<Route>
    suspend fun getRouteById(id: String): Route?
}
