package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.remote.GroqAISource
import com.sitp.arequipa.domain.repository.RecomendacionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecomendacionRepositoryImpl(
    private val groqAISource: GroqAISource = GroqAISource()
) : RecomendacionRepository {

    override suspend fun recomendarRuta(
        origen: String,
        destino: String,
        preferencia: String,
        rutas: List<Map<String, Any>>,
        consultaExtra: String,
        rutasOrigen: List<String>,
        rutasDestino: List<String>,
        esDirecta: Boolean
    ): String = withContext(Dispatchers.IO) {
        groqAISource.recomendarRuta(
            origen = origen,
            destino = destino,
            preferencia = preferencia,
            rutas = rutas,
            rutasOrigen = rutasOrigen,
            rutasDestino = rutasDestino,
            esDirecta = esDirecta,
            consultaExtra = consultaExtra
        )
    }
}