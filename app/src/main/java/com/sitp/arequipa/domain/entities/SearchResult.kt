package com.sitp.arequipa.domain.entities

/**
 * Entidad: Resultado completo de una búsqueda de ruta óptima.
 * Contiene todos los segmentos del viaje y el resumen total.
 */
data class SearchResult(
    val segmentos: List<RouteSegment> = emptyList(),
    val tiempoTotalMinutos: Int = 0,
    val costoTotal: Double = 0.0,
    val transbordos: Int = 0,
    val origenDescripcion: String = "",
    val destinoDescripcion: String = "",
    val generadoPorIA: Boolean = false
)
