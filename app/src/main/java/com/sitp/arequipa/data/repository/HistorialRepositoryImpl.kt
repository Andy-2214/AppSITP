package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.remote.FirestoreHistorialSource
import com.sitp.arequipa.domain.model.BusquedaHistorial
import com.sitp.arequipa.domain.model.RutaFavorita
import com.sitp.arequipa.domain.repository.HistorialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistorialRepositoryImpl(
    private val remoteSource: FirestoreHistorialSource = FirestoreHistorialSource()
) : HistorialRepository {

    override suspend fun guardarBusqueda(
        uid: String,
        origen: String,
        destino: String,
        preferencia: String,
        respuestaIA: String
    ) = withContext(Dispatchers.IO) {
        remoteSource.guardarBusqueda(uid, origen, destino, preferencia, respuestaIA)
    }

    override suspend fun verificarFavoritoAutomatico(uid: String, origen: String, destino: String): Boolean =
        withContext(Dispatchers.IO) { remoteSource.verificarYActualizarFavorito(uid, origen, destino) }

    override suspend fun cargarHistorial(uid: String): List<BusquedaHistorial> =
        withContext(Dispatchers.IO) { remoteSource.cargarHistorial(uid) }

    override suspend fun eliminarBusqueda(uid: String, busquedaId: String) =
        withContext(Dispatchers.IO) { remoteSource.eliminarBusqueda(uid, busquedaId) }

    override suspend fun cargarFavoritos(uid: String): List<RutaFavorita> =
        withContext(Dispatchers.IO) { remoteSource.cargarFavoritos(uid) }

    override suspend fun guardarFavoritoManual(uid: String, origen: String, destino: String, nombre: String) =
        withContext(Dispatchers.IO) { remoteSource.guardarFavoritoManual(uid, origen, destino, nombre) }

    override suspend fun eliminarFavorito(uid: String, favoritoId: String) =
        withContext(Dispatchers.IO) { remoteSource.eliminarFavorito(uid, favoritoId) }
}