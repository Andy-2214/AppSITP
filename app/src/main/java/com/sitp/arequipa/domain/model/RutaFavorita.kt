package com.sitp.arequipa.domain.model

data class RutaFavorita(
    val id: String = "",
    val origen: String = "",
    val destino: String = "",
    val nombre: String = "",
    val frecuencia: Int = 0,
    val fechaUltimoUso: Long? = null
)