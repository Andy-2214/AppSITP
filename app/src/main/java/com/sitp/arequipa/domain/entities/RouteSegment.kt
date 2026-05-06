package com.sitp.arequipa.domain.entities

/**
 * Entidad: Tramo de un viaje dentro de una ruta óptima.
 * Representa el recorrido en una sola línea de combi.
 */
data class RouteSegment(
    val route: Route,
    val paraderoAbordaje: String = "",
    val paraderoDescenso: String = "",
    val paradasIntermedias: Int = 0,
    val duracionEstimadaMinutos: Int = 0
)
