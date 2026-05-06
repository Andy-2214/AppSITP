package com.sitp.arequipa.domain.entities

/**
 * Entidad: Ruta oficial del sistema de transporte (MPA Arequipa).
 * Representa una línea de combi con sus paraderos y metadatos.
 */
data class Route(
    val id: String = "",
    val codigo: String = "",
    val nombre: String = "",
    val empresa: String = "",
    val color: String = "#FF0000",
    val costo: Double = 0.0,
    val frecuenciaMinutos: Int = 0,
    val coordenadas: List<Map<String, Double>> = emptyList(),
    val paraderos: List<String> = emptyList()
)
