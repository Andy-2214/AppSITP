package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.Coordenada
import kotlinx.coroutines.flow.Flow

/**
 * Puerto de dominio para la ubicación del propio teléfono. No depende de ningún proveedor:
 * hoy lo implementa el GPS de Android, que funciona sin internet (ADR-006).
 * Si la app no tiene permiso de ubicación, no lanza excepción: devuelve null o un flujo vacío.
 */
interface UbicacionRepository {

    /** Última ubicación conocida o una lectura nueva; null si no hay permiso o no se pudo obtener. */
    suspend fun ubicacionActual(): Coordenada?

    /** Ubicaciones mientras alguien escuche (para el punto azul del mapa). */
    fun seguirUbicacion(): Flow<Coordenada>
}
