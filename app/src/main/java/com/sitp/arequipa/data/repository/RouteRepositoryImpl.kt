package com.sitp.arequipa.data.repository

import android.content.Context
import com.sitp.arequipa.data.local.db.AppDatabase
import com.sitp.arequipa.data.local.entity.CoordenadaEntity
import com.sitp.arequipa.data.local.entity.RutaEntity
import com.sitp.arequipa.data.remote.FirestoreRouteSource
import com.sitp.arequipa.domain.model.Ruta
import com.sitp.arequipa.domain.repository.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RouteRepositoryImpl private constructor(
    private val database: AppDatabase,
    private val remoteSource: FirestoreRouteSource
) : RouteRepository {

    private val rutaDao = database.rutaDao()

    override fun obtenerRutas(): Flow<List<Ruta>> {
        return rutaDao.obtenerTodasLasRutasConCoordenadas().map { lista ->
            lista.map { r ->
                Ruta(
                    codigo = r.ruta.codigo,
                    nombre = r.ruta.nombre,
                    empresa = r.ruta.empresa,
                    avenidas = r.ruta.avenidas,
                    avenidaVuelta = r.ruta.avenidaVuelta,
                    color = r.ruta.color,
                    etiqueta = r.ruta.etiqueta,
                    frecuencia = r.ruta.frecuencia,
                    coordsIda = r.coordenadas
                        .filter { it.tipo == "IDA" }
                        .sortedBy { it.orden }
                        .map { Pair(it.lat, it.lng) },
                    coordsVuelta = r.coordenadas
                        .filter { it.tipo == "VUELTA" }
                        .sortedBy { it.orden }
                        .map { Pair(it.lat, it.lng) }
                )
            }
        }
    }

    override suspend fun sincronizarConServidor(): Unit = withContext(Dispatchers.IO) {
        try {
            val documents = remoteSource.descargarRutas()
            if (documents.isEmpty()) return@withContext

            // 1. Verificar si las versiones locales coinciden con las remotas
            val rutasLocales = rutaDao.obtenerRutasSync().associate { it.codigo to it.version }
            val hayCambios = rutasLocales.isEmpty() ||
                rutasLocales.size != documents.size ||
                documents.any { doc ->
                    val codigo = doc.getString("codigo") ?: return@any true
                    val versionRemota = doc.getLong("version") ?: 1L
                    rutasLocales[codigo] != versionRemota
                }

            if (!hayCambios) {
                println("DEBUG RouteRepository: Rutas locales ya sincronizadas (versiones coinciden). Omitiendo reescritura de Room.")
                return@withContext
            }

            println("DEBUG RouteRepository: Actualización de rutas detectada. Sincronizando con Room SQLite...")

            val rutasEntities = mutableListOf<RutaEntity>()
            val coordenadasEntities = mutableListOf<CoordenadaEntity>()

            for (doc in documents) {
                val codigo = doc.getString("codigo") ?: continue
                val nombre = doc.getString("nombre") ?: ""
                val empresa = doc.getString("empresa") ?: ""
                val avenidas = doc.getString("avenidas") ?: ""
                val avenidaVuelta = doc.getString("avenidaVuelta") ?: ""
                val color = doc.getString("color") ?: ""
                val etiqueta = doc.getString("etiqueta") ?: doc.getString("etiquetas") ?: ""
                val frecuencia = doc.getString("frecuencia") ?: ""
                val version = doc.getLong("version") ?: 1L

                rutasEntities.add(
                    RutaEntity(
                        codigo = codigo,
                        nombre = nombre,
                        empresa = empresa,
                        avenidas = avenidas,
                        avenidaVuelta = avenidaVuelta,
                        color = color,
                        etiqueta = etiqueta,
                        frecuencia = frecuencia,
                        version = version
                    )
                )

                @Suppress("UNCHECKED_CAST")
                val coordsIdaRaw = doc.get("coordenadas") as? List<Map<String, Any>> ?: emptyList()
                coordsIdaRaw.forEachIndexed { index, map ->
                    val lat = map["lat"] as? Double
                    val lng = map["lng"] as? Double
                    if (lat != null && lng != null) {
                        coordenadasEntities.add(
                            CoordenadaEntity(
                                rutaCodigo = codigo,
                                lat = lat,
                                lng = lng,
                                tipo = "IDA",
                                orden = index
                            )
                        )
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val coordsVueltaRaw = doc.get("coordenadasVuelta") as? List<Map<String, Any>> ?: emptyList()
                coordsVueltaRaw.forEachIndexed { index, map ->
                    val lat = map["lat"] as? Double
                    val lng = map["lng"] as? Double
                    if (lat != null && lng != null) {
                        coordenadasEntities.add(
                            CoordenadaEntity(
                                rutaCodigo = codigo,
                                lat = lat,
                                lng = lng,
                                tipo = "VUELTA",
                                orden = index
                            )
                        )
                    }
                }
            }

            if (rutasEntities.isNotEmpty()) {
                rutaDao.actualizarCache(rutasEntities, coordenadasEntities)
                println("DEBUG RouteRepository: Room SQLite actualizado con ${rutasEntities.size} rutas y ${coordenadasEntities.size} coordenadas.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: RouteRepositoryImpl? = null

        fun getInstance(context: Context): RouteRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val remote = FirestoreRouteSource()
                val instance = RouteRepositoryImpl(db, remote)
                INSTANCE = instance
                instance
            }
        }
    }
}
