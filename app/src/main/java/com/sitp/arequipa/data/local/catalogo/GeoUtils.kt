package com.sitp.arequipa.data.local.catalogo

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Tramo recto entre dos coordenadas (grados). */
data class Segmento(val lat1: Double, val lng1: Double, val lat2: Double, val lng2: Double)

/** Rectángulo de búsqueda en millonésimas de grado (como se guarda en el catálogo). */
data class AreaE6(val minLatE6: Int, val maxLatE6: Int, val minLngE6: Int, val maxLngE6: Int)

/**
 * Geometría plana local (equirectangular) para distancias cortas (< 1–2 km), suficiente
 * para decidir "qué calle está a menos de 100 m". Funciones puras, sin Android.
 * Debe coincidir con scripts/catalogo (mismo M_POR_GRADO).
 */
object GeoUtils {

    /** Metros por grado de latitud con R = 6 371 000 m. */
    const val M_POR_GRADO = 111_195.0

    fun gradosLat(metros: Double): Double = metros / M_POR_GRADO

    fun gradosLng(metros: Double, lat: Double): Double =
        metros / (M_POR_GRADO * cos(Math.toRadians(lat)))

    /** Millonésimas de grado (formato del catálogo) → grados. */
    fun desdeE6(e6: Int): Double = e6 / 1_000_000.0

    /** Rectángulo que contiene el círculo de [radioM] metros alrededor del punto (redondeado hacia afuera). */
    fun areaAlrededor(lat: Double, lng: Double, radioM: Double): AreaE6 {
        val dLat = gradosLat(radioM)
        val dLng = gradosLng(radioM, lat)
        return AreaE6(
            minLatE6 = floor((lat - dLat) * 1_000_000).toInt(),
            maxLatE6 = ceil((lat + dLat) * 1_000_000).toInt(),
            minLngE6 = floor((lng - dLng) * 1_000_000).toInt(),
            maxLngE6 = ceil((lng + dLng) * 1_000_000).toInt()
        )
    }

    /** Distancia en metros entre dos coordenadas cercanas. */
    fun distanciaMetros(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val kx = M_POR_GRADO * cos(Math.toRadians(lat1))
        return hypot((lng2 - lng1) * kx, (lat2 - lat1) * M_POR_GRADO)
    }

    /** Distancia en metros desde el punto (lat, lng) al tramo [s] (al punto más cercano del tramo). */
    fun distanciaPuntoSegmento(lat: Double, lng: Double, s: Segmento): Double {
        val kx = M_POR_GRADO * cos(Math.toRadians(lat))
        // Coordenadas planas con el punto como origen
        val ax = (s.lng1 - lng) * kx
        val ay = (s.lat1 - lat) * M_POR_GRADO
        val bx = (s.lng2 - lng) * kx
        val by = (s.lat2 - lat) * M_POR_GRADO
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / largo2))
        return hypot(ax + t * dx, ay + t * dy)
    }

    /**
     * Regla par-impar: un rayo hacia el este desde el punto cruza el borde un número impar de veces
     * si el punto está dentro. Funciona con tramos desordenados y con huecos (anillos interiores).
     */
    fun dentroDePoligono(lat: Double, lng: Double, borde: List<Segmento>): Boolean {
        var dentro = false
        for (s in borde) {
            if ((s.lat1 > lat) != (s.lat2 > lat)) {
                val lngCruce = s.lng1 + (lat - s.lat1) * (s.lng2 - s.lng1) / (s.lat2 - s.lat1)
                if (lng < lngCruce) dentro = !dentro
            }
        }
        return dentro
    }
}
