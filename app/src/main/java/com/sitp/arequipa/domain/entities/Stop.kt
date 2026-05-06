package com.sitp.arequipa.domain.entities

/**
 * Entidad: Paradero del sistema de transporte.
 * Punto físico donde los pasajeros suben o bajan.
 */
data class Stop(
    val id: String = "",
    val nombre: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val routeIds: List<String> = emptyList()
)
