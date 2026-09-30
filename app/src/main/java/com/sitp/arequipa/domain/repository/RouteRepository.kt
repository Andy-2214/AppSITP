package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.Ruta
import kotlinx.coroutines.flow.Flow

interface RouteRepository {
    fun obtenerRutas(): Flow<List<Ruta>>
    suspend fun sincronizarConServidor()
}
