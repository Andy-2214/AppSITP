package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.RouteRepository

/** Sincroniza las rutas desde Firestore hacia la base de datos local Room. */
class SincronizarRutasUseCase(
    private val routeRepository: RouteRepository
) {
    suspend operator fun invoke() = routeRepository.sincronizarConServidor()
}
