package com.sitp.arequipa.data.repository

import android.content.Context
import com.sitp.arequipa.data.local.db.AppDatabase
import com.sitp.arequipa.data.local.entity.CoordenadaEntity
import com.sitp.arequipa.data.local.entity.CuencaEntity
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
                // Aqui se arma la vista desnormalizada: empresa y color vienen de la cuenca (join)
                Ruta(
                    codigo = r.ruta.codigo,
                    codigoCombi = r.ruta.codigoCombi.ifBlank { r.ruta.codigo },
                    cuenca = r.ruta.cuenca,
                    empresa = r.cuencaInfo?.empresa.orEmpty(),
                    avenidas = r.ruta.avenidas,
                    avenidaVuelta = r.ruta.avenidaVuelta,
                    color = r.cuencaInfo?.color?.ifBlank { null } ?: "#FF0000",
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

            // Las cuencas (empresa y color) viven en su propia coleccion
            val cuencasRemotas = remoteSource.descargarCuencas().map { doc ->
                CuencaEntity(
                    cuenca = doc.getString("cuenca") ?: doc.id,
                    empresa = doc.getString("empresa") ?: "",
                    color = doc.getString("color") ?: ""
                )
            }

            // 1. Verificar si lo local coincide con lo remoto (rutas por version, cuencas por contenido)
            val rutasLocales = rutaDao.obtenerRutasSync().associate { it.codigo to it.version }
            val cuencasLocales = rutaDao.obtenerCuencasSync().associateBy { it.cuenca }
            val rutasCambiaron = rutasLocales.isEmpty() ||
                rutasLocales.size != documents.size ||
                documents.any { doc ->
                    val codigo = doc.getString("codigo") ?: return@any true
                    val versionRemota = doc.getLong("version") ?: 1L
                    rutasLocales[codigo] != versionRemota
                }
            val cuencasCambiaron = cuencasLocales.size != cuencasRemotas.size ||
                cuencasRemotas.any { cuencasLocales[it.cuenca] != it }

            if (!rutasCambiaron && !cuencasCambiaron) {
                println("DEBUG RouteRepository: Rutas y cuencas locales ya sincronizadas. Omitiendo reescritura de Room.")
                return@withContext
            }

            println("DEBUG RouteRepository: Actualización detectada. Sincronizando con Room SQLite...")

            val cuencasValidas = cuencasRemotas.map { it.cuenca }.toSet()
            val rutasEntities = mutableListOf<RutaEntity>()
            val coordenadasEntities = mutableListOf<CoordenadaEntity>()

            for (doc in documents) {
                val codigo = doc.getString("codigo") ?: continue
                val cuenca = doc.getString("cuenca") ?: ""
                // Si la cuenca no existe, la llave foranea haria fallar toda la transaccion
                if (cuenca !in cuencasValidas) {
                    println("DEBUG RouteRepository: Ruta $codigo descartada, su cuenca '$cuenca' no existe en 'cuencas'.")
                    continue
                }
                val codigoCombi = doc.getString("codigoCombi") ?: ""
                val avenidas = doc.getString("avenidas") ?: ""
                val avenidaVuelta = doc.getString("avenidaVuelta") ?: ""
                val etiqueta = doc.getString("etiqueta") ?: doc.getString("etiquetas") ?: ""
                val frecuencia = doc.getString("frecuencia") ?: ""
                val version = doc.getLong("version") ?: 1L

                rutasEntities.add(
                    RutaEntity(
                        codigo = codigo,
                        codigoCombi = codigoCombi,
                        cuenca = cuenca,
                        avenidas = avenidas,
                        avenidaVuelta = avenidaVuelta,
                        etiqueta = etiqueta,
                        frecuencia = frecuencia,
                        version = version
                    )
                )

                @Suppress("UNCHECKED_CAST")
                val coordsIdaRaw = doc.get("coordenadas") as? List<Map<String, Any>> ?: emptyList()
                coordsIdaRaw.forEachIndexed { index, map ->
                    val lat = (map["lat"] as? Number)?.toDouble()
                    val lng = (map["lng"] as? Number)?.toDouble()
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
                    val lat = (map["lat"] as? Number)?.toDouble()
                    val lng = (map["lng"] as? Number)?.toDouble()
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
                rutaDao.actualizarCache(cuencasRemotas, rutasEntities, coordenadasEntities)
                println("DEBUG RouteRepository: Room SQLite actualizado con ${cuencasRemotas.size} cuencas, ${rutasEntities.size} rutas y ${coordenadasEntities.size} coordenadas.")
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
