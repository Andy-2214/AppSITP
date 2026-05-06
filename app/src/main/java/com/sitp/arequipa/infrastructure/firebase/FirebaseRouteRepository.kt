package com.sitp.arequipa.infrastructure.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.domain.entities.Route
import com.sitp.arequipa.domain.repositories.RouteRepository
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firebase de RouteRepository.
 * Lee las rutas oficiales MPA desde la colección "rutas" de Firestore.
 */
class FirebaseRouteRepository : RouteRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun getRoutes(): List<Route> {
        val snapshot = db.collection("rutas").get().await()
        return snapshot.documents.map { doc ->
            Route(
                id = doc.id,
                codigo = doc.getString("codigo") ?: "",
                nombre = doc.getString("nombre") ?: "",
                empresa = doc.getString("empresa") ?: "Sin empresa",
                color = doc.getString("color") ?: "#FF0000",
                costo = doc.getDouble("costo") ?: 0.0,
                frecuenciaMinutos = doc.getLong("frecuencia")?.toInt() ?: 0,
                coordenadas = doc.get("coordenadas") as? List<Map<String, Double>> ?: emptyList(),
                paraderos = doc.get("paraderos") as? List<String> ?: emptyList()
            )
        }
    }

    override suspend fun getRouteById(id: String): Route? {
        val doc = db.collection("rutas").document(id).get().await()
        if (!doc.exists()) return null
        return Route(
            id = doc.id,
            codigo = doc.getString("codigo") ?: "",
            nombre = doc.getString("nombre") ?: "",
            empresa = doc.getString("empresa") ?: "Sin empresa",
            color = doc.getString("color") ?: "#FF0000",
            costo = doc.getDouble("costo") ?: 0.0,
            frecuenciaMinutos = doc.getLong("frecuencia")?.toInt() ?: 0,
            coordenadas = doc.get("coordenadas") as? List<Map<String, Double>> ?: emptyList(),
            paraderos = doc.get("paraderos") as? List<String> ?: emptyList()
        )
    }
}
