package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.HistorialRepository

/** Guarda manualmente un favorito del usuario (HU-20). */
class GuardarFavoritoUseCase(
    private val historialRepository: HistorialRepository
) {
    suspend operator fun invoke(uid: String, origen: String, destino: String, nombre: String) =
        historialRepository.guardarFavoritoManual(uid, origen, destino, nombre)
}
