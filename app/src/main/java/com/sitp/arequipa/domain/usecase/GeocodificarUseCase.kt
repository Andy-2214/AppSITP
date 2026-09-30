package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.GeocodingRepository

/** Convierte coordenadas GPS en nombre de calle/distrito legible para humanos. */
class GeocodificarUseCase(private val geocodingRepository: GeocodingRepository) {
    suspend operator fun invoke(lat: Double, lng: Double): String =
        geocodingRepository.coordenadasANombre(lat, lng)
}