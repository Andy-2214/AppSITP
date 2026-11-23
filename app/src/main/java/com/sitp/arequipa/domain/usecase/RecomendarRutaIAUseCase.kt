package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.repository.LugaresRepository
import com.sitp.arequipa.domain.repository.RecomendacionRepository

/**
 * Coordina la obtención de nombres de lugares y la llamada a la IA de Groq
 * para obtener una recomendación de ruta cuando el algoritmo local no encuentra solución.
 */
class RecomendarRutaIAUseCase(
    private val recomendacionRepository: RecomendacionRepository,
    private val lugaresRepository: LugaresRepository
) {

    suspend fun obtenerNombreOrigen(lat: Double, lng: Double): String =
        lugaresRepository.coordenadasANombre(lat, lng)

    suspend fun obtenerNombreDestino(lat: Double, lng: Double): String =
        lugaresRepository.coordenadasANombre(lat, lng)

    suspend fun recomendar(
        origenNombre: String,
        destinoNombre: String,
        preferencia: String,
        rutasFinales: List<Map<String, Any>>,
        rutasOrigen: List<String>,
        rutasDestino: List<String>,
        esDirecta: Boolean,
        consultaExtra: String = ""
    ): String = recomendacionRepository.recomendarRuta(
        origen = origenNombre,
        destino = destinoNombre,
        preferencia = preferencia,
        rutas = rutasFinales,
        rutasOrigen = rutasOrigen,
        rutasDestino = rutasDestino,
        esDirecta = esDirecta,
        consultaExtra = consultaExtra
    )
}