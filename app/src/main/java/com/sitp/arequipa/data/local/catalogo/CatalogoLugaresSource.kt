package com.sitp.arequipa.data.local.catalogo

import com.sitp.arequipa.data.local.catalogo.entity.LugarEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adaptador de datos de lugares basado en el catálogo local (catalogo.db): convierte coordenadas en un
 * texto legible ("Avenida Ejército, Cayma") sin internet y sin costo por consulta (ADR-002).
 * No depende del proveedor de los datos: la fuente y su crédito vienen en la tabla catalogo_info.
 *
 * Orden de decisión:
 *  1. Calle con nombre a ≤ [RADIO_CALLE_M] m (si el nombre no es genérico, p. ej. "Calle 1").
 *  2. Lugar de referencia a ≤ [RADIO_LUGAR_M] m → "cerca de Mercado San Camilo".
 *  3. La calle genérica, si era lo único cercano.
 *  4. Solo el distrito.
 *  5. [ZONA_POR_DEFECTO] (fuera de la provincia o si algo falla).
 * El distrito se agrega al final cuando se conoce: "<texto>, <distrito>".
 *
 * Las mismas reglas están en scripts/catalogo/generar_casos_prueba.py (implementación de referencia
 * que genera los casos de la prueba instrumentada). Si cambias una regla, cambia ambas.
 *
 * Consulta Room de forma síncrona: llamar desde un hilo de fondo (LugaresRepositoryImpl usa Dispatchers.IO).
 */
class CatalogoLugaresSource(private val dao: CatalogoDao) {

    private class Distrito(
        val nombre: String,
        val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double,
        val borde: List<Segmento>
    )

    private val mutex = Mutex()
    private var distritos: List<Distrito>? = null
    private var info: Map<String, String>? = null

    suspend fun coordenadasANombre(lat: Double, lng: Double): String = try {
        val distrito = distritoDe(lat, lng)
        val calle = calleCercana(lat, lng)
        val texto = when {
            calle != null && !esNombreGenerico(calle) -> calle
            else -> lugarCercano(lat, lng)?.let { "cerca de $it" } ?: calle
        }
        when {
            texto != null && distrito != null -> "$texto, $distrito"
            texto != null -> texto
            distrito != null -> distrito
            else -> ZONA_POR_DEFECTO
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("DEBUG CatalogoLugares: error consultando el catálogo (${e.message})")
        ZONA_POR_DEFECTO
    }

    /** Nombre de la calle más cercana a ≤ RADIO_CALLE_M metros, o null. */
    private suspend fun calleCercana(lat: Double, lng: Double): String? {
        // Los tramos miden ≤ 50 m: si un tramo pasa a ≤ 100 m del punto, su inicio está a ≤ 150 m.
        val area = GeoUtils.areaAlrededor(lat, lng, RADIO_CALLE_M + MARGEN_TRAMO_M)
        return dao.segmentosEnArea(area.minLatE6, area.maxLatE6, area.minLngE6, area.maxLngE6)
            .map {
                val tramo = Segmento(
                    GeoUtils.desdeE6(it.lat1E6), GeoUtils.desdeE6(it.lng1E6),
                    GeoUtils.desdeE6(it.lat2E6), GeoUtils.desdeE6(it.lng2E6)
                )
                it.nombre to GeoUtils.distanciaPuntoSegmento(lat, lng, tramo)
            }
            .filter { it.second <= RADIO_CALLE_M }
            .minWithOrNull(compareBy<Pair<String, Double>> { it.second }.thenBy { it.first })
            ?.first
    }

    /** Lugar a ≤ RADIO_LUGAR_M metros; prefiere los tipos de [TIPOS_REFERENCIA]. */
    private suspend fun lugarCercano(lat: Double, lng: Double): String? {
        val area = GeoUtils.areaAlrededor(lat, lng, RADIO_LUGAR_M)
        val cercanos = dao.lugaresEnArea(area.minLatE6, area.maxLatE6, area.minLngE6, area.maxLngE6)
            .map { it to GeoUtils.distanciaMetros(lat, lng, GeoUtils.desdeE6(it.latE6), GeoUtils.desdeE6(it.lngE6)) }
            .filter { it.second <= RADIO_LUGAR_M }
        val orden = compareBy<Pair<LugarEntity, Double>> { it.second }.thenBy { it.first.nombre }
        val referencia = cercanos.filter { it.first.tipo in TIPOS_REFERENCIA }.minWithOrNull(orden)
        return (referencia ?: cercanos.minWithOrNull(orden))?.first?.nombre
    }

    private suspend fun distritoDe(lat: Double, lng: Double): String? =
        cargarDistritos().firstOrNull {
            lat in it.minLat..it.maxLat && lng in it.minLng..it.maxLng &&
                GeoUtils.dentroDePoligono(lat, lng, it.borde)
        }?.nombre

    /**
     * Datos del catálogo (clave → valor): fuente, crédito de la licencia, fecha de los datos...
     * Se leen una sola vez. Si falla la lectura devuelve un mapa vacío (la app sigue funcionando).
     */
    suspend fun info(): Map<String, String> = mutex.withLock {
        info ?: try {
            dao.info().associate { it.clave to it.valor }.also { info = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("DEBUG CatalogoLugares: no se pudo leer catalogo_info (${e.message})")
            emptyMap()
        }
    }

    /** Los bordes (~7 000 tramos) se cargan una sola vez en memoria. */
    private suspend fun cargarDistritos(): List<Distrito> = mutex.withLock {
        distritos ?: run {
            val bordes = dao.segmentosDeDistritos().groupBy({ it.distritoId }) {
                Segmento(
                    GeoUtils.desdeE6(it.lat1E6), GeoUtils.desdeE6(it.lng1E6),
                    GeoUtils.desdeE6(it.lat2E6), GeoUtils.desdeE6(it.lng2E6)
                )
            }
            dao.distritos().map {
                Distrito(
                    it.nombre,
                    GeoUtils.desdeE6(it.minLatE6), GeoUtils.desdeE6(it.maxLatE6),
                    GeoUtils.desdeE6(it.minLngE6), GeoUtils.desdeE6(it.maxLngE6),
                    bordes[it.id].orEmpty()
                )
            }.also { distritos = it }
        }
    }

    companion object {
        const val RADIO_CALLE_M = 100.0
        const val RADIO_LUGAR_M = 200.0
        /** Largo máximo de un tramo guardado (50 m) + holgura por redondeo. */
        const val MARGEN_TRAMO_M = 55.0
        const val ZONA_POR_DEFECTO = "zona de Arequipa"

        /** "Calle 1", "Pasaje B", "Av. 3A"...: nombres que no orientan a nadie. */
        private val NOMBRE_GENERICO = Regex(
            "^(calle|pasaje|psje\\.?|pje\\.?|jir[oó]n|jr\\.?|avenida|av\\.?) +([0-9]+[a-z]?|[a-z])$",
            RegexOption.IGNORE_CASE
        )

        fun esNombreGenerico(nombre: String): Boolean = NOMBRE_GENERICO.matches(nombre.trim())

        /** Tipos de lugar ("clave=valor" del catálogo) que la gente usa como referencia para ubicarse. */
        val TIPOS_REFERENCIA = setOf(
            "amenity=marketplace", "shop=mall", "shop=supermarket", "amenity=university", "amenity=college",
            "amenity=hospital", "amenity=clinic", "amenity=school", "amenity=bus_station", "amenity=townhall",
            "amenity=place_of_worship", "amenity=police", "amenity=fire_station", "amenity=library",
            "amenity=cinema", "amenity=theatre", "leisure=park", "leisure=stadium", "leisure=sports_centre",
            "place=square", "place=neighbourhood", "place=suburb", "place=quarter", "tourism=museum",
            "tourism=attraction", "tourism=viewpoint", "public_transport=station", "historic=monument"
        )
    }
}
