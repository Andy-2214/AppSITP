package com.sitp.arequipa.presentation.map

import androidx.lifecycle.ViewModel
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.usecase.ObtenerUbicacionActualUseCase
import com.sitp.arequipa.domain.usecase.SeguirUbicacionUseCase
import kotlinx.coroutines.flow.Flow

/** Ubicación del teléfono para el mapa (botón "Mi ubicación" y punto azul). Solo usa casos de uso. */
class MapaViewModel(
    private val obtenerUbicacionActual: ObtenerUbicacionActualUseCase,
    private val seguirUbicacionUseCase: SeguirUbicacionUseCase
) : ViewModel() {

    suspend fun ubicacionActual(): Coordenada? = obtenerUbicacionActual()

    /** Se escucha solo mientras la pantalla está visible (la UI usa repeatOnLifecycle). */
    fun seguirUbicacion(): Flow<Coordenada> = seguirUbicacionUseCase()
}
