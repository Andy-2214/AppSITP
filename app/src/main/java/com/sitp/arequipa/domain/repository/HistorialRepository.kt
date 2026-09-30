package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.BusquedaHistorial
import com.sitp.arequipa.domain.model.RutaFavorita

interface HistorialRepository {
    suspend fun guardarBusqueda(
        uid: String,
        origen: String,
        destino: String,
        preferencia: String,
        respuestaIA: String
    )
    suspend fun verificarFavoritoAutomatico(uid: String, origen: String, destino: String): Boolean
    suspend fun cargarHistorial(uid: String): List<BusquedaHistorial>
    suspend fun eliminarBusqueda(uid: String, busquedaId: String)
    suspend fun cargarFavoritos(uid: String): List<RutaFavorita>
    suspend fun guardarFavoritoManual(uid: String, origen: String, destino: String, nombre: String)
    suspend fun eliminarFavorito(uid: String, favoritoId: String)
}
