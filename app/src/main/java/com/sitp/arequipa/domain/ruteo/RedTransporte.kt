package com.sitp.arequipa.domain.ruteo

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.ParametrosBusqueda
import com.sitp.arequipa.domain.model.Ruta
import com.sitp.arequipa.domain.model.Sentido

/**
 * Red de combis lista para buscar rutas (ADR-010). Se arma una sola vez por versión de las rutas.
 *
 * - Cada ruta es una **línea**: la secuencia ordenada ida → vuelta. Solo se avanza hacia adelante,
 *   así nunca se indica una combi que va en sentido contrario. Si la vuelta empieza lejos del final
 *   de la ida (datos cortados), ida y vuelta quedan como dos líneas separadas.
 * - Sobre cada línea hay **paraderos virtuales** cada [ParametrosBusqueda.separacionParaderosM] metros,
 *   con su distancia acumulada desde el inicio (el viaje se mide sobre el recorrido real).
 * - Dos paraderos de líneas distintas a ≤ [ParametrosBusqueda.radioTransbordoM] forman un transbordo a pie.
 *
 * Los paraderos tienen un número global; los arreglos `p*` guardan sus datos (estructura de arreglos
 * para no crear miles de objetos en el teléfono).
 */
class RedTransporte private constructor(
    val lineas: List<Linea>,
    internal val pLat: DoubleArray,
    internal val pLng: DoubleArray,
    /** Metros desde el inicio de su línea. */
    internal val pAcum: DoubleArray,
    internal val pLinea: IntArray,
    /** Posición del paradero dentro de su línea (0, 1, 2…). */
    internal val pPos: IntArray,
    private val radioTransbordoM: Double
) {
    /** Una línea de la red: una ruta (o su ida o su vuelta, si están separadas). */
    class Linea(
        val indice: Int,
        val codigo: String,
        val intervaloMin: Double,
        internal val primerParadero: Int,
        internal val cantidad: Int,
        internal val vLat: DoubleArray,
        internal val vLng: DoubleArray,
        internal val vAcum: DoubleArray,
        /** Metros a partir de los cuales la combi va de VUELTA (MAX_VALUE si la línea es solo ida o solo vuelta). */
        internal val acumInicioVuelta: Double,
        internal val sentidoFijo: Sentido?
    ) {
        fun sentidoEn(acum: Double): Sentido =
            sentidoFijo ?: if (acum >= acumInicioVuelta) Sentido.VUELTA else Sentido.IDA
    }

    val numParaderos: Int get() = pLat.size
    internal val grilla = GrillaPuntos(pLat, pLng)

    private val transbordos = arrayOfNulls<IntArray>(pLat.size)
    private val transbordosMetros = arrayOfNulls<DoubleArray>(pLat.size)

    fun coordenada(paradero: Int) = Coordenada(pLat[paradero], pLng[paradero])
    fun lineaDe(paradero: Int): Linea = lineas[pLinea[paradero]]
    fun sentidoEn(paradero: Int): Sentido = lineaDe(paradero).sentidoEn(pAcum[paradero])

    /**
     * Paraderos a los que se puede caminar desde [p] para cambiar de combi: el más cercano de cada otra
     * línea a ≤ radioTransbordoM. Se calcula la primera vez que se necesita y se guarda.
     */
    internal fun transbordosDe(p: Int): Pair<IntArray, DoubleArray> {
        transbordos[p]?.let { return it to transbordosMetros[p]!! }
        synchronized(this) {
            transbordos[p]?.let { return it to transbordosMetros[p]!! }
            val miLinea = pLinea[p]
            val mejor = HashMap<Int, Pair<Int, Double>>()
            grilla.visitar(pLat[p], pLng[p], radioTransbordoM) { q, d ->
                val l = pLinea[q]
                if (l != miLinea) {
                    val actual = mejor[l]
                    if (actual == null || d < actual.second) mejor[l] = q to d
                }
            }
            val destinos = IntArray(mejor.size)
            val metros = DoubleArray(mejor.size)
            var i = 0
            for ((q, d) in mejor.values) { destinos[i] = q; metros[i] = d; i++ }
            transbordosMetros[p] = metros
            transbordos[p] = destinos
            return destinos to metros
        }
    }

    /** Puntos por donde pasa la combi entre dos paraderos de la misma línea (para dibujar la guía). */
    fun recorrido(desde: Int, hasta: Int): List<Coordenada> {
        val linea = lineaDe(desde)
        val a0 = pAcum[desde]
        val a1 = pAcum[hasta]
        val puntos = ArrayList<Coordenada>()
        puntos.add(coordenada(desde))
        for (i in linea.vAcum.indices) {
            val a = linea.vAcum[i]
            if (a > a0 && a < a1) puntos.add(Coordenada(linea.vLat[i], linea.vLng[i]))
        }
        puntos.add(coordenada(hasta))
        return puntos
    }

    companion object {
        fun construir(rutas: List<Ruta>, parametros: ParametrosBusqueda = ParametrosBusqueda()): RedTransporte {
            val lineas = ArrayList<Linea>()
            val lat = ArrayList<Double>()
            val lng = ArrayList<Double>()
            val acum = ArrayList<Double>()
            val linea = ArrayList<Int>()
            val pos = ArrayList<Int>()

            fun agregarLinea(codigo: String, intervalo: Double, puntos: List<Pair<Double, Double>>, inicioVuelta: Int, sentidoFijo: Sentido?) {
                if (puntos.size < 2) return
                val vLat = DoubleArray(puntos.size) { puntos[it].first }
                val vLng = DoubleArray(puntos.size) { puntos[it].second }
                val vAcum = DoubleArray(puntos.size)
                for (i in 1 until puntos.size) vAcum[i] = vAcum[i - 1] + Geo.metros(vLat[i - 1], vLng[i - 1], vLat[i], vLng[i])
                val largo = vAcum.last()
                if (largo <= 0.0) return
                val acumVuelta = if (inicioVuelta in 1 until puntos.size) vAcum[inicioVuelta] else Double.MAX_VALUE

                val indice = lineas.size
                val primero = lat.size
                // Paraderos cada `separacion` metros, más uno al final del recorrido
                val separacion = parametros.separacionParaderosM
                var objetivo = 0.0
                var tramo = 1
                var n = 0
                while (objetivo <= largo) {
                    while (tramo < puntos.size - 1 && vAcum[tramo] < objetivo) tramo++
                    val a = vAcum[tramo - 1]
                    val b = vAcum[tramo]
                    val f = if (b > a) ((objetivo - a) / (b - a)).coerceIn(0.0, 1.0) else 0.0
                    lat.add(vLat[tramo - 1] + (vLat[tramo] - vLat[tramo - 1]) * f)
                    lng.add(vLng[tramo - 1] + (vLng[tramo] - vLng[tramo - 1]) * f)
                    acum.add(objetivo); linea.add(indice); pos.add(n); n++
                    objetivo += separacion
                }
                if (largo - acum.last() > 1.0) {
                    lat.add(vLat.last()); lng.add(vLng.last())
                    acum.add(largo); linea.add(indice); pos.add(n); n++
                }
                lineas.add(Linea(indice, codigo, intervalo, primero, n, vLat, vLng, vAcum, acumVuelta, sentidoFijo))
            }

            for (r in rutas.sortedBy { it.codigo }) {
                val intervalo = Frecuencia.minutos(r.frecuencia) ?: parametros.intervaloPorDefectoMin
                val ida = r.coordsIda
                val vuelta = r.coordsVuelta
                when {
                    ida.size >= 2 && vuelta.size >= 2 -> {
                        val salto = Geo.metros(ida.last().first, ida.last().second, vuelta.first().first, vuelta.first().second)
                        if (salto <= parametros.saltoMaximoIdaVueltaM) {
                            agregarLinea(r.codigo, intervalo, ida + vuelta, ida.size, null)
                        } else {
                            agregarLinea(r.codigo, intervalo, ida, -1, Sentido.IDA)
                            agregarLinea(r.codigo, intervalo, vuelta, -1, Sentido.VUELTA)
                        }
                    }
                    ida.size >= 2 -> agregarLinea(r.codigo, intervalo, ida, -1, Sentido.IDA)
                    vuelta.size >= 2 -> agregarLinea(r.codigo, intervalo, vuelta, -1, Sentido.VUELTA)
                }
            }

            return RedTransporte(
                lineas,
                lat.toDoubleArray(), lng.toDoubleArray(), acum.toDoubleArray(),
                linea.toIntArray(), pos.toIntArray(),
                parametros.radioTransbordoM
            )
        }
    }
}
