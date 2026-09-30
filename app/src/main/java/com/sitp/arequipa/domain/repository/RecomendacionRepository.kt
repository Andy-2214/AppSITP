package com.sitp.arequipa.domain.repository

interface RecomendacionRepository {
    suspend fun recomendarRuta(
        origen: String,
        destino: String,
        preferencia: String,
        rutas: List<Map<String, Any>>,
        consultaExtra: String = "",
        rutasOrigen: List<String> = emptyList(),
        rutasDestino: List<String> = emptyList(),
        esDirecta: Boolean = false
    ): String
}