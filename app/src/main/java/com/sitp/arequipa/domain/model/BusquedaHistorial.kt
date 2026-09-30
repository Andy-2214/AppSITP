package com.sitp.arequipa.domain.model

data class BusquedaHistorial(
    val id: String = "",
    val origen: String = "",
    val destino: String = "",
    val preferencia: String = "",
    val respuestaIA: String = "",
    val fecha: Long? = null
)