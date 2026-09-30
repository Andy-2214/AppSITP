package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.HistorialRepository

/** Elimina una búsqueda del historial del usuario (HU-22). */
class EliminarBusquedaUseCase(
    private val historialRepository: HistorialRepository
) {
    suspend operator fun invoke(uid: String, busquedaId: String) =
        historialRepository.eliminarBusqueda(uid, busquedaId)
}
