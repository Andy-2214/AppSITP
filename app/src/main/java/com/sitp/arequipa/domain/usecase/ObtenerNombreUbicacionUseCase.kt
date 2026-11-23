package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.LugaresRepository

/** Convierte coordenadas en un nombre legible de calle/lugar y distrito. */
class ObtenerNombreUbicacionUseCase(private val lugaresRepository: LugaresRepository) {
    suspend operator fun invoke(lat: Double, lng: Double): String =
        lugaresRepository.coordenadasANombre(lat, lng)
}
