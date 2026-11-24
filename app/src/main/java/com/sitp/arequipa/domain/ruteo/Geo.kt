package com.sitp.arequipa.domain.ruteo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Cálculos de distancia en metros sobre coordenadas GPS (grados decimales). */
object Geo {
    private const val RADIO_TIERRA_M = 6_371_000.0
    private const val RAD = Math.PI / 180.0
    /** Metros por grado de latitud. */
    const val METROS_POR_GRADO = RADIO_TIERRA_M * Math.PI / 180.0

    /** Fórmula de Haversine: distancia real en metros entre dos puntos. */
    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = (lat2 - lat1) * RAD
        val dLng = (lng2 - lng1) * RAD
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * RAD) * cos(lat2 * RAD) * sin(dLng / 2) * sin(dLng / 2)
        return RADIO_TIERRA_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Aproximación equirectangular: en distancias de pocos kilómetros difiere de Haversine en menos
     * de 0,1 % y es varias veces más rápida. Se usa dentro del motor, donde se calculan millones.
     */
    fun metros(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val x = (lng2 - lng1) * cos((lat1 + lat2) * 0.5 * RAD)
        val y = lat2 - lat1
        return sqrt(x * x + y * y) * METROS_POR_GRADO
    }
}

/**
 * Índice espacial de puntos en celdas de [tamCeldaM] metros. Para buscar los puntos a menos de r metros
 * solo se revisan las celdas que tocan ese círculo, en vez de todos los puntos.
 */
internal class GrillaPuntos(
    private val lat: DoubleArray,
    private val lng: DoubleArray,
    tamCeldaM: Double = 300.0
) {
    private val celdaGrados = tamCeldaM / Geo.METROS_POR_GRADO
    private val celdas: HashMap<Long, IntArray>

    init {
        val temporal = HashMap<Long, MutableList<Int>>()
        for (i in lat.indices) temporal.getOrPut(clave(fila(lat[i]), columna(lng[i]))) { mutableListOf() }.add(i)
        celdas = HashMap(temporal.size * 2)
        for ((k, v) in temporal) celdas[k] = v.toIntArray()
    }

    private fun fila(la: Double) = floor(la / celdaGrados).toInt()
    private fun columna(ln: Double) = floor(ln / celdaGrados).toInt()
    private fun clave(f: Int, c: Int): Long = (f.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)

    /** Llama a [accion] con cada punto a ≤ [radioM] metros de (la, ln) y su distancia. */
    inline fun visitar(la: Double, ln: Double, radioM: Double, accion: (indice: Int, metros: Double) -> Unit) {
        val margenFilas = (radioM / Geo.METROS_POR_GRADO / celdaGradosPublica()).toInt() + 1
        val cosLat = cos(la * Math.PI / 180.0).coerceAtLeast(0.2)
        val margenColumnas = (radioM / (Geo.METROS_POR_GRADO * cosLat) / celdaGradosPublica()).toInt() + 1
        val f0 = filaPublica(la)
        val c0 = columnaPublica(ln)
        for (f in f0 - margenFilas..f0 + margenFilas) {
            for (c in c0 - margenColumnas..c0 + margenColumnas) {
                val indices = celdaPublica(f, c) ?: continue
                for (i in indices) {
                    val d = Geo.metros(la, ln, latDe(i), lngDe(i))
                    if (d <= radioM) accion(i, d)
                }
            }
        }
    }

    // Accesos para la función inline (no puede usar miembros privados)
    @PublishedApi internal fun celdaGradosPublica() = celdaGrados
    @PublishedApi internal fun filaPublica(la: Double) = fila(la)
    @PublishedApi internal fun columnaPublica(ln: Double) = columna(ln)
    @PublishedApi internal fun celdaPublica(f: Int, c: Int): IntArray? = celdas[clave(f, c)]
    @PublishedApi internal fun latDe(i: Int) = lat[i]
    @PublishedApi internal fun lngDe(i: Int) = lng[i]
}
