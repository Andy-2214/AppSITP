package com.sitp.arequipa.domain.model

data class Ruta(
    val codigo: String,
    val nombre: String,
    val empresa: String,
    val avenidas: String,
    val avenidaVuelta: String,
    val color: String,
    val etiqueta: String = "",
    val frecuencia: String = "",
    val coordsIda: List<Pair<Double, Double>>,
    val coordsVuelta: List<Pair<Double, Double>>
)
