package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.HistorialRepository

/** Elimina un favorito del usuario (HU-20). */
class EliminarFavoritoUseCase(
    private val historialRepository: HistorialRepository
) {
    suspend operator fun invoke(uid: String, favoritoId: String) =
        historialRepository.eliminarFavorito(uid, favoritoId)
}
