package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.HistorialRepository

/**
 * Guarda una búsqueda en el historial del usuario y verifica si debe
 * agregarse como favorito automático (HU-22 + HU-20).
 * Retorna true si se creó un favorito automático nuevo.
 */
class GuardarBusquedaUseCase(
    private val historialRepository: HistorialRepository
) {
    suspend operator fun invoke(
        uid: String,
        origen: String,
        destino: String,
        preferencia: String,
        respuestaIA: String
    ): Boolean {
        historialRepository.guardarBusqueda(uid, origen, destino, preferencia, respuestaIA)
        return historialRepository.verificarFavoritoAutomatico(uid, origen, destino)
    }
}