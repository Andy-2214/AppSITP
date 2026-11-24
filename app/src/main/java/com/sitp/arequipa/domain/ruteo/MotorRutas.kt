package com.sitp.arequipa.domain.ruteo

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.Itinerario
import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.model.ParametrosBusqueda
import com.sitp.arequipa.domain.model.TipoTramo
import com.sitp.arequipa.domain.model.Tramo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.PriorityQueue

/**
 * Búsqueda por rondas basada en frecuencias con líneas atractivas (ADR-010).
 *
 * 1. **Rondas** (idea de RAPTOR, sin horarios): la ronda k calcula el mejor tiempo para llegar a cada
 *    paradero usando exactamente k combis. Como las combis no tienen horario, al subir se suma la espera
 *    esperada α × intervalo (Spiess y Florian, 1989; Nguyen y Pallottino, 1988).
 * 2. **Alternativas** (idea de Yen): se repite la búsqueda prohibiendo una ruta ya usada. Cada corrida da
 *    el mejor viaje con 1, 2 y 3 combis, así sirve para los dos modos a la vez.
 * 3. **Líneas atractivas**: en cada tramo se buscan otras rutas que hacen el mismo viaje; la espera se
 *    calcula con la frecuencia combinada («sube a la que pase primero»).
 *
 * Una instancia por búsqueda (reutiliza sus arreglos entre corridas; no es segura entre hilos).
 */
class MotorRutas(
    private val red: RedTransporte,
    private val p: ParametrosBusqueda = ParametrosBusqueda()
) {
    private val n = red.numParaderos
    private val k = p.maxCombis
    private val numLineas = red.lineas.size

    // Por ronda r y paradero s:
    private val llegada = Array(k + 1) { DoubleArray(n) }      // minutos al estar listo para subir en s
    private val llegadaBus = Array(k + 1) { DoubleArray(n) }   // minutos al bajar de una combi en s
    private val lineaPadre = Array(k + 1) { IntArray(n) }      // línea con la que se llegó en combi
    private val subidaPadre = Array(k + 1) { IntArray(n) }     // paradero donde se subió a esa combi
    private val caminoPadre = Array(k + 1) { IntArray(n) }     // paradero desde el que se caminó (−1: no se caminó)

    private class Acceso(val paradero: Int, val metros: Double)
    /** Viaje encontrado en una corrida: ronda y paradero donde se baja para caminar al destino. */
    private class Viaje(val ronda: Int, val paraderoFinal: Int, val metrosFinal: Double)

    private fun caminar(metros: Double) = metros * p.factorRodeo / p.metrosPorMinutoCaminando
    private fun viajar(desde: Int, hasta: Int) = (red.pAcum[hasta] - red.pAcum[desde]) / 1000.0 * p.minutosPorKmEnCombi
    private fun intervalo(linea: Int) = red.lineas[linea].intervaloMin

    private fun accesos(punto: Coordenada, radio: Double): List<Acceso> {
        val lista = ArrayList<Acceso>()
        red.grilla.visitar(punto.lat, punto.lng, radio) { i, d -> lista.add(Acceso(i, d)) }
        return lista
    }

    /**
     * Busca las opciones entre [origen] y [destino]. Llama a [alAvanzar] cuando ya hay
     * [ParametrosBusqueda.primerasOpciones] del [modo] elegido y cada vez que aparecen opciones nuevas;
     * la última llamada lleva `completa = true`.
     */
    suspend fun buscar(
        origen: Coordenada,
        destino: Coordenada,
        modo: ModoBusqueda,
        alAvanzar: suspend (OpcionesBusqueda) -> Unit = {}
    ): OpcionesBusqueda {
        var accesosOrigen = accesos(origen, p.radioAccesoM)
        var accesosDestino = accesos(destino, p.radioAccesoM)
        if (accesosOrigen.isEmpty() || accesosDestino.isEmpty()) {
            accesosOrigen = accesos(origen, p.radioAccesoAmpliadoM)
            accesosDestino = accesos(destino, p.radioAccesoAmpliadoM)
        }
        if (accesosOrigen.isEmpty() || accesosDestino.isEmpty()) {
            return OpcionesBusqueda.VACIA_COMPLETA.also { alAvanzar(it) }
        }
        val egreso = HashMap<Int, Double>(accesosDestino.size * 2)
        for (a in accesosDestino) egreso[a.paradero] = a.metros

        val encontrados = LinkedHashMap<String, Itinerario>()
        val vistos = HashSet<Set<Int>>()
        val cola = PriorityQueue<Pair<Double, Set<Int>>>(compareBy { it.first })
        cola.add(0.0 to emptySet()); vistos.add(emptySet())
        var corridas = 0
        var ultimaEmitida: OpcionesBusqueda? = null

        while (cola.isNotEmpty() && corridas < p.maxCorridas) {
            currentCoroutineContext().ensureActive()
            val prohibidas = cola.poll()!!.second
            val viajes = correr(accesosOrigen, egreso, prohibidas)
            corridas++
            for (v in viajes) {
                val (itinerario, lineasUsadas) = construir(v, origen, destino, prohibidas) ?: continue
                val previo = encontrados[itinerario.clave]
                if (previo == null || itinerario.minutosEstimado < previo.minutosEstimado) encontrados[itinerario.clave] = itinerario
                for (l in lineasUsadas) {
                    val nuevo = prohibidas + l
                    if (vistos.add(nuevo)) cola.add(itinerario.minutosEstimado to nuevo)
                }
            }
            val opciones = seleccionar(encontrados.values, completa = false)
            val listo = opciones.porTiempo.size >= p.maxOpciones && opciones.porCosto.size >= p.maxOpciones
            if (!listo) {
                val yaHayPrimeras = opciones.de(modo).size >= p.primerasOpciones
                if (yaHayPrimeras && opciones != ultimaEmitida) {
                    alAvanzar(opciones)
                    ultimaEmitida = opciones
                }
            } else break
        }
        val final = seleccionar(encontrados.values, completa = true)
        alAvanzar(final)
        return final
    }

    /** Una corrida de rondas sin las líneas [prohibidas]. Devuelve el mejor viaje con 1, 2… k combis. */
    private fun correr(accesosOrigen: List<Acceso>, egreso: Map<Int, Double>, prohibidas: Set<Int>): List<Viaje> {
        for (r in 0..k) {
            llegada[r].fill(INF); llegadaBus[r].fill(INF)
            lineaPadre[r].fill(-1); subidaPadre[r].fill(-1); caminoPadre[r].fill(-1)
        }
        var marcados = ArrayList<Int>()
        for (a in accesosOrigen) {
            val t = caminar(a.metros)
            if (t < llegada[0][a.paradero]) {
                if (llegada[0][a.paradero] == INF) marcados.add(a.paradero)
                llegada[0][a.paradero] = t
            }
        }
        val inicioLinea = IntArray(numLineas)
        val resultado = ArrayList<Viaje>()

        for (r in 1..k) {
            if (marcados.isEmpty()) break
            inicioLinea.fill(Int.MAX_VALUE)
            for (s in marcados) {
                val l = red.pLinea[s]
                if (l in prohibidas) continue
                if (red.pPos[s] < inicioLinea[l]) inicioLinea[l] = red.pPos[s]
            }
            val enRonda = BooleanArray(n)
            val nuevos = ArrayList<Int>()
            val penalidad = if (r > 1) p.penalidadTransbordoMin else 0.0

            // 1) Recorrer cada línea hacia adelante desde el primer paradero alcanzado
            for (l in 0 until numLineas) {
                val desde = inicioLinea[l]
                if (desde == Int.MAX_VALUE) continue
                val linea = red.lineas[l]
                val espera = p.alfaEspera * linea.intervaloMin
                var enCombi = INF
                var subida = -1
                var anterior = -1
                for (pos in desde until linea.cantidad) {
                    val s = linea.primerParadero + pos
                    if (subida >= 0) {
                        enCombi += viajar(anterior, s)
                        if (enCombi < llegadaBus[r][s]) {
                            llegadaBus[r][s] = enCombi
                            lineaPadre[r][s] = l
                            subidaPadre[r][s] = subida
                            if (!enRonda[s]) { enRonda[s] = true; nuevos.add(s) }
                        }
                    }
                    val listo = llegada[r - 1][s]
                    if (listo < INF) {
                        val alSubir = listo + espera + penalidad
                        if (alSubir < enCombi) { enCombi = alSubir; subida = s }
                    }
                    anterior = s
                }
            }

            // 2) Quien bajó de una combi camina a un paradero de OTRA línea (los paraderos son por línea,
            //    así que el transbordo «en el mismo sitio» es una caminata de pocos metros). No se deja
            //    volver a subir a la misma línea: ocuparía la ronda con viajes que luego se descartan.
            val siguientes = ArrayList<Int>(nuevos.size * 2)
            val marcadoSiguiente = BooleanArray(n)
            if (r < k) {
                for (s in nuevos) {
                    val (destinos, metros) = red.transbordosDe(s)
                    val base = llegadaBus[r][s]
                    for (i in destinos.indices) {
                        val q = destinos[i]
                        val t = base + caminar(metros[i])
                        if (t < llegada[r][q]) {
                            llegada[r][q] = t; caminoPadre[r][q] = s
                            if (!marcadoSiguiente[q]) { marcadoSiguiente[q] = true; siguientes.add(q) }
                        }
                    }
                }
            }
            marcados = siguientes

            // 3) Mejor llegada al destino con r combis
            var mejor = INF
            var mejorParadero = -1
            var mejorMetros = 0.0
            for ((s, m) in egreso) {
                val t = llegadaBus[r][s]
                if (t == INF) continue
                val total = t + caminar(m)
                if (total < mejor) { mejor = total; mejorParadero = s; mejorMetros = m }
            }
            if (mejorParadero >= 0) resultado.add(Viaje(r, mejorParadero, mejorMetros))
        }
        return resultado
    }

    /** Arma el itinerario de un viaje (de atrás hacia adelante) con sus tiempos y líneas alternativas. */
    private fun construir(v: Viaje, origen: Coordenada, destino: Coordenada, prohibidas: Set<Int>): Pair<Itinerario, List<Int>>? {
        // Tramos en orden inverso: (línea, subida, bajada) y caminatas entre paraderos
        class Combi(val linea: Int, val subida: Int, val bajada: Int)
        val combis = ArrayList<Combi>()
        val caminatasIntermedias = HashMap<Int, Int>() // índice de combi (en orden inverso) → paradero desde el que se caminó
        var actual = v.paraderoFinal
        var r = v.ronda
        while (r >= 1) {
            val l = lineaPadre[r][actual]
            val b = subidaPadre[r][actual]
            if (l < 0 || b < 0) return null
            combis.add(Combi(l, b, actual))
            if (r - 1 == 0) break
            val w = caminoPadre[r - 1][b]
            if (w >= 0) { caminatasIntermedias[combis.size - 1] = w; actual = w } else actual = b
            r--
        }
        combis.reverse()
        val nCombis = combis.size
        // La misma ruta dos veces en un viaje no tiene sentido para el usuario
        if (combis.map { red.lineas[it.linea].codigo }.toSet().size != nCombis) return null

        val tramos = ArrayList<Tramo>()
        var total = 0.0
        var sumaEsperas = 0.0
        var sumaIntervalos = 0.0

        fun agregarCaminata(desde: Coordenada, hasta: Coordenada, metros: Double) {
            if (metros < 1.0) return
            val min = caminar(metros)
            tramos.add(Tramo(TipoTramo.CAMINATA, desde, hasta, min, metros))
            total += min
        }

        val primeraSubida = combis.first().subida
        agregarCaminata(origen, red.coordenada(primeraSubida), Geo.metros(origen.lat, origen.lng, red.pLat[primeraSubida], red.pLng[primeraSubida]))
        for ((i, c) in combis.withIndex()) {
            if (i > 0) {
                // ¿se caminó para llegar a esta subida? (las caminatas se guardaron con el índice inverso)
                val w = caminatasIntermedias[nCombis - 1 - i]
                if (w != null) agregarCaminata(red.coordenada(w), red.coordenada(c.subida), Geo.metros(red.pLat[w], red.pLng[w], red.pLat[c.subida], red.pLng[c.subida]))
                total += p.penalidadTransbordoMin
            }
            val viaje = viajar(c.subida, c.bajada)
            val alternativas = lineasAtractivas(c.linea, c.subida, c.bajada, viaje, prohibidas)
            val frecuenciaTotal = 1.0 / intervalo(c.linea) + alternativas.sumOf { 1.0 / intervalo(it) }
            val intervaloComb = 1.0 / frecuenciaTotal
            val espera = p.alfaEspera * intervaloComb
            val linea = red.lineas[c.linea]
            tramos.add(
                Tramo(
                    tipo = TipoTramo.COMBI,
                    desde = red.coordenada(c.subida),
                    hasta = red.coordenada(c.bajada),
                    minutos = viaje,
                    metros = red.pAcum[c.bajada] - red.pAcum[c.subida],
                    codigoRuta = linea.codigo,
                    sentido = red.sentidoEn(c.subida),
                    esperaMin = espera,
                    intervaloMin = intervaloComb,
                    alternativas = alternativas.map { red.lineas[it].codigo }.distinct(),
                    recorrido = red.recorrido(c.subida, c.bajada)
                )
            )
            total += espera + viaje
            sumaEsperas += espera
            sumaIntervalos += intervaloComb
        }
        agregarCaminata(red.coordenada(v.paraderoFinal), destino, v.metrosFinal)

        val minimo = total - sumaEsperas
        return Itinerario(
            tramos = tramos,
            minutosEstimado = total,
            minutosMinimo = minimo,
            minutosMaximo = minimo + sumaIntervalos,
            costo = p.pasajeSoles * nCombis
        ) to combis.map { it.linea }
    }

    /**
     * Otras líneas que suben cerca de [subida] y bajan cerca de [bajada] con un viaje como mucho
     * [ParametrosBusqueda.toleranciaLineasAtractivas] más largo (hiperrutas de Nguyen y Pallottino).
     */
    private fun lineasAtractivas(linea: Int, subida: Int, bajada: Int, viaje: Double, prohibidas: Set<Int>): List<Int> {
        val codigo = red.lineas[linea].codigo
        val radio = p.radioLineasAtractivasM
        val cercaSubida = HashMap<Int, Int>()
        val distSubida = HashMap<Int, Double>()
        red.grilla.visitar(red.pLat[subida], red.pLng[subida], radio) { q, d ->
            val l = red.pLinea[q]
            if (l != linea && l !in prohibidas && red.lineas[l].codigo != codigo) {
                if (d < (distSubida[l] ?: Double.MAX_VALUE)) { distSubida[l] = d; cercaSubida[l] = q }
            }
        }
        val resultado = ArrayList<Int>()
        for ((l, q) in cercaSubida) {
            val otra = red.lineas[l]
            var mejorBajada = -1
            var mejorDist = radio
            for (pos in red.pPos[q] + 1 until otra.cantidad) {
                val s = otra.primerParadero + pos
                val d = Geo.metros(red.pLat[s], red.pLng[s], red.pLat[bajada], red.pLng[bajada])
                if (d <= mejorDist) { mejorDist = d; mejorBajada = s }
            }
            if (mejorBajada >= 0 && viajar(q, mejorBajada) <= viaje * (1 + p.toleranciaLineasAtractivas)) resultado.add(l)
        }
        return resultado.sortedBy { red.lineas[it].codigo }
    }

    /** Ordena y recorta las opciones de cada modo (ver ADR-010: filtro de 1,5 × y mínimo de 3). */
    private fun seleccionar(todas: Collection<Itinerario>, completa: Boolean): OpcionesBusqueda {
        if (todas.isEmpty()) return OpcionesBusqueda(emptyList(), emptyList(), completa)

        val porTiempoOrden = todas.sortedWith(compareBy<Itinerario> { it.minutosEstimado }.thenBy { it.numeroCombis })
        val mejorTiempo = porTiempoOrden.first().minutosEstimado
        val porTiempo = completar(porTiempoOrden.filter { it.minutosEstimado <= mejorTiempo * p.factorMaximoTiempo }, porTiempoOrden)

        val porCostoOrden = todas.sortedWith(compareBy<Itinerario> { it.numeroCombis }.thenBy { it.minutosEstimado })
        val mejorPorCombis = porCostoOrden.groupBy { it.numeroCombis }.mapValues { (_, l) -> l.minOf { it.minutosEstimado } }
        val porCosto = completar(
            porCostoOrden.filter { it.minutosEstimado <= mejorPorCombis.getValue(it.numeroCombis) * p.factorMaximoTiempo },
            porCostoOrden
        )
        return OpcionesBusqueda(porTiempo, porCosto, completa)
    }

    /** Hasta maxOpciones; si el filtro dejó menos de minOpciones, se completa con las siguientes que existan. */
    private fun completar(filtradas: List<Itinerario>, todas: List<Itinerario>): List<Itinerario> {
        if (filtradas.size >= p.minOpciones) return filtradas.take(p.maxOpciones)
        val resultado = filtradas.toMutableList()
        for (it in todas) {
            if (resultado.size >= p.minOpciones) break
            if (it !in resultado) resultado.add(it)
        }
        return resultado
    }

    private companion object {
        const val INF = Double.MAX_VALUE
    }
}
