package com.sitp.arequipa.infrastructure.ai

import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.domain.entities.SearchResult
import com.sitp.arequipa.domain.services.AIRouteService

/**
 * Implementación del servicio de IA con Gemini (HU-17/18).
 *
 * TODO: Integrar con la API de Gemini usando el prompt cargado desde Firestore.
 *       Ver HU-17-02: "Integrar API de Gemini AI con el prompt base cargado desde Firestore"
 *       y HU-17-03: "Construir payload de consulta combinando origen, destino, criterio y texto natural"
 */
class GeminiRouteService : AIRouteService {

    override suspend fun queryWithNaturalLanguage(
        query: String,
        origin: LatLng,
        destination: LatLng,
        criterio: String
    ): SearchResult? {
        // Validación: rechazar consultas no relacionadas con transporte (HU-17-05)
        if (!esConsultaDeTransporte(query)) {
            return null // El caller debe mostrar el mensaje de rechazo
        }

        // TODO: Implementar llamada real a Gemini API
        // 1. Cargar prompt base desde Firestore (colección "prompt_ia")
        // 2. Construir payload: prompt + origen + destino + criterio + query del usuario
        // 3. Llamar a Gemini API y parsear la respuesta
        // 4. Convertir respuesta a SearchResult

        return null
    }

    /** Verifica que la consulta sea sobre transporte en Arequipa */
    private fun esConsultaDeTransporte(query: String): Boolean {
        val palabrasTransporte = listOf(
            "ruta", "combi", "bus", "paradero", "transporte",
            "llegar", "ir a", "cómo llego", "línea", "pasaje"
        )
        val queryLower = query.lowercase()
        return palabrasTransporte.any { queryLower.contains(it) }
    }
}
