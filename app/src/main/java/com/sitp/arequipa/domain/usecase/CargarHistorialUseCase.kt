package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.BusquedaHistorial
import com.sitp.arequipa.domain.repository.HistorialRepository

/** Carga el historial de búsquedas del usuario desde Firestore (HU-22). */
class CargarHistorialUseCase(
    private val historialRepository: HistorialRepository
) {
    suspend operator fun invoke(uid: String): List<BusquedaHistorial> =
        historialRepository.cargarHistorial(uid)
}
