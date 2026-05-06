package com.sitp.arequipa.infrastructure.algorithm

import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.domain.entities.Route
import com.sitp.arequipa.domain.entities.RouteSegment
import com.sitp.arequipa.domain.entities.SearchResult
import com.sitp.arequipa.domain.repositories.RouteRepository
import com.sitp.arequipa.domain.services.RouteOptimizerService
import kotlin.math.*

/**
 * Implementación del algoritmo propio de optimización de rutas (HU-16/18).
 * Usa heurística de distancia geográfica para encontrar rutas que pasen
 * cerca del origen y destino indicados.
 *
 * TODO: Implementar Dijkstra/BFS completo sobre el grafo de paraderos
 *       cuando los datos de paraderos estén completos en Firestore.
 */
class GraphRouteOptimizer(
    private val routeRepository: RouteRepository
) : RouteOptimizerService {

    override suspend fun findOptimalRoute(
        origin: LatLng,
        destination: LatLng,
        criterio: String
    ): SearchResult? {
        val allRoutes = routeRepository.getRoutes()
        if (allRoutes.isEmpty()) return null

        // Filtrar rutas que tengan coordenadas y pasen cerca de origen y destino
        val candidatas = allRoutes.filter { route ->
            val puntos = route.coordenadas.mapNotNull { coord ->
                val lat = coord["lat"] ?: return@mapNotNull null
                val lng = coord["lng"] ?: return@mapNotNull null
                LatLng(lat, lng)
            }
            if (puntos.size < 2) return@filter false
            val cercaOrigen = puntos.any { distanciaKm(it, origin) < 0.5 }
            val cercaDestino = puntos.any { distanciaKm(it, destination) < 0.5 }
            cercaOrigen && cercaDestino
        }

        if (candidatas.isEmpty()) return null

        // Ordenar según criterio
        val rutaOrdenada = when (criterio) {
            "costo" -> candidatas.minByOrNull { it.costo }
            "transbordos" -> candidatas.first() // ruta directa = 0 transbordos
            else -> candidatas.minByOrNull { estimarTiempo(it, origin, destination) }
        } ?: return null

        val segmento = RouteSegment(
            route = rutaOrdenada,
            paraderoAbordaje = "Paradero más cercano al origen",
            paraderoDescenso = "Paradero más cercano al destino",
            paradasIntermedias = 0,
            duracionEstimadaMinutos = estimarTiempo(rutaOrdenada, origin, destination)
        )

        return SearchResult(
            segmentos = listOf(segmento),
            tiempoTotalMinutos = segmento.duracionEstimadaMinutos,
            costoTotal = rutaOrdenada.costo,
            transbordos = 0,
            origenDescripcion = "Origen seleccionado",
            destinoDescripcion = "Destino seleccionado",
            generadoPorIA = false
        )
    }

    /** Distancia en km entre dos puntos (fórmula Haversine) */
    private fun distanciaKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val x = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) *
                cos(Math.toRadians(b.latitude)) *
                sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(x), sqrt(1 - x))
    }

    /** Estimación simple de tiempo basada en distancia total de la ruta */
    private fun estimarTiempo(route: Route, origin: LatLng, destination: LatLng): Int {
        val distancia = distanciaKm(origin, destination)
        val velocidadKmh = 20.0 // velocidad media combi urbana
        return ((distancia / velocidadKmh) * 60).toInt().coerceAtLeast(5)
    }
}
