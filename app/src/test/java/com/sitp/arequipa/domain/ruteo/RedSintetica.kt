package com.sitp.arequipa.domain.ruteo

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.Ruta
import kotlin.math.cos

/** Ayuda para armar redes de prueba en metros alrededor de un punto de Arequipa. */
object RedSintetica {
    const val LAT0 = -16.40
    const val LNG0 = -71.53
    private val METROS_POR_GRADO_LNG = Geo.METROS_POR_GRADO * cos(LAT0 * Math.PI / 180)

    /** Punto a [este] y [norte] metros del punto base. */
    fun p(este: Double, norte: Double) = Pair(LAT0 + norte / Geo.METROS_POR_GRADO, LNG0 + este / METROS_POR_GRADO_LNG)
    fun c(este: Double, norte: Double) = p(este, norte).let { Coordenada(it.first, it.second) }

    /** Recorrido que pasa por los vértices dados, con un punto cada 50 m (como un trazado real). */
    fun recorrido(vararg vertices: Pair<Double, Double>): List<Pair<Double, Double>> {
        val puntos = ArrayList<Pair<Double, Double>>()
        for (i in 0 until vertices.size - 1) {
            val (x0, y0) = vertices[i]
            val (x1, y1) = vertices[i + 1]
            val largo = Math.hypot(x1 - x0, y1 - y0)
            val pasos = (largo / 50).toInt().coerceAtLeast(1)
            for (k in 0 until pasos) {
                val f = k.toDouble() / pasos
                puntos.add(p(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f))
            }
        }
        val (xf, yf) = vertices.last()
        puntos.add(p(xf, yf))
        return puntos
    }

    fun ruta(codigo: String, ida: List<Pair<Double, Double>>, vuelta: List<Pair<Double, Double>> = emptyList(), frecuencia: String = "") =
        Ruta(
            codigo = codigo, codigoCombi = codigo, cuenca = "C", empresa = "E", avenidas = "", avenidaVuelta = "",
            color = "#000000", frecuencia = frecuencia, coordsIda = ida, coordsVuelta = vuelta
        )
}
