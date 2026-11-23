package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.local.ubicacion.UbicacionDispositivoSource
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.repository.UbicacionRepository
import kotlinx.coroutines.flow.Flow

/** Implementación del puerto [UbicacionRepository] con el GPS del teléfono (sin internet). */
class UbicacionRepositoryImpl(
    private val dispositivo: UbicacionDispositivoSource
) : UbicacionRepository {

    override suspend fun ubicacionActual(): Coordenada? = dispositivo.ubicacionActual()

    override fun seguirUbicacion(): Flow<Coordenada> = dispositivo.seguirUbicacion()
}
