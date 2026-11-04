package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Ruta

/**
 * HU-01 / Algoritmo central de la tesis.
 *
 * Dado origen y destino en coordenadas y la lista de rutas disponibles,
 * devuelve el mejor itinerario usando solo Kotlin puro (sin dependencias Android).
 * Esto permite ejecutar Unit Tests desde src/test/ sin emulador.
 *
 * Algoritmo:
 * 1. Pre-calcula distancias de cada ruta al origen y destino (bounding box + Haversine).
 * 2. Filtra rutas dentro de 1000m (fallback 2000m).
 * 3. Busca paths de 1, 2 y 3 rutas evaluando intersecciones.
 * 4. Elige el mejor path según la preferencia (tiempo o costo).
 * 5. Si no hay solución local, retorna NecesitaIA con las rutas pre-filtradas.
 */
class BuscarRutaUseCase {

    suspend operator fun invoke(
        rutas: List<Ruta>,
        origenLat: Double,
        origenLng: Double,
        destinoLat: Double,
        destinoLng: Double,
        preferencia: String,
        consultaExtra: String = ""
    ): ResultadoBusqueda = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {

        val todasLasRutas = rutas.mapNotNull { r ->
            val ptsIda = r.coordsIda
            val ptsVuelta = r.coordsVuelta
            val todosPts = ptsIda + ptsVuelta
            if (todosPts.isEmpty()) return@mapNotNull null

            val minLat = todosPts.minOf { it.first }
            val maxLat = todosPts.maxOf { it.first }
            val minLng = todosPts.minOf { it.second }
            val maxLng = todosPts.maxOf { it.second }

            var minDistOrigen = Double.MAX_VALUE
            var minDistDestino = Double.MAX_VALUE
            for (p in todosPts) {
                val dO = haversine(origenLat, origenLng, p.first, p.second)
                if (dO < minDistOrigen) minDistOrigen = dO
                val dD = haversine(destinoLat, destinoLng, p.first, p.second)
                if (dD < minDistDestino) minDistDestino = dD
            }

            mapOf(
                "codigo" to r.codigo,
                "codigoCombi" to r.codigoCombi,
                "cuenca" to r.cuenca,
                "empresa" to r.empresa,
                "avenidas" to r.avenidas,
                "avenidaVuelta" to r.avenidaVuelta,
                "ptsIda" to ptsIda,
                "ptsVuelta" to ptsVuelta,
                "todosPts" to todosPts,
                "grilla" to GrillaRuta(todosPts),
                "minLat" to minLat,
                "maxLat" to maxLat,
                "minLng" to minLng,
                "maxLng" to maxLng,
                "minDistOrigen" to minDistOrigen,
                "minDistDestino" to minDistDestino
            )
        }.mapIndexed { i, ruta -> ruta + ("idx" to i) }

        val radio = 1000.0
        val radioFallback = 2000.0

        var rutasOrigenFiltradas = todasLasRutas.filter { (it["minDistOrigen"] as Double) <= radio }
        var rutasDestinoFiltradas = todasLasRutas.filter { (it["minDistDestino"] as Double) <= radio }

        if (rutasOrigenFiltradas.isEmpty() && rutasDestinoFiltradas.isEmpty()) {
            rutasOrigenFiltradas = todasLasRutas.filter { (it["minDistOrigen"] as Double) <= radioFallback }
            rutasDestinoFiltradas = todasLasRutas.filter { (it["minDistDestino"] as Double) <= radioFallback }
        }

        val rutasOrigenOrdenadas = rutasOrigenFiltradas.sortedBy { it["minDistOrigen"] as Double }
        val rutasDestinoOrdenadas = rutasDestinoFiltradas.sortedBy { it["minDistDestino"] as Double }

        val codigosOrigen = rutasOrigenOrdenadas.map { it["codigo"].toString() }.toSet()
        val codigosDestino = rutasDestinoOrdenadas.map { it["codigo"].toString() }.toSet()

        val nRutas = todasLasRutas.size
        val cruceMemo = ByteArray(nRutas * nRutas) // 0 = sin calcular, 1 = se cruzan, 2 = no se cruzan

        fun intersectanRutas(r1: Map<String, Any>, r2: Map<String, Any>): Boolean {
            val minLat1 = r1["minLat"] as Double; val maxLat1 = r1["maxLat"] as Double
            val minLng1 = r1["minLng"] as Double; val maxLng1 = r1["maxLng"] as Double
            val minLat2 = r2["minLat"] as Double; val maxLat2 = r2["maxLat"] as Double
            val minLng2 = r2["minLng"] as Double; val maxLng2 = r2["maxLng"] as Double
            val margin = 0.0045
            if (maxLat1 < minLat2 - margin || minLat1 > maxLat2 + margin ||
                maxLng1 < minLng2 - margin || minLng1 > maxLng2 + margin) return false
            return GrillaRuta.hayCruce(r1["grilla"] as GrillaRuta, r2["grilla"] as GrillaRuta)
        }

        fun cachedIntersectan(r1: Map<String, Any>, r2: Map<String, Any>): Boolean {
            val i = r1["idx"] as Int
            val j = r2["idx"] as Int
            val guardado = cruceMemo[i * nRutas + j].toInt()
            if (guardado != 0) return guardado == 1
            val seCruzan = intersectanRutas(r1, r2)
            val marca: Byte = if (seCruzan) 1 else 2
            cruceMemo[i * nRutas + j] = marca
            cruceMemo[j * nRutas + i] = marca
            return seCruzan
        }

        val puntoMemo = arrayOfNulls<Pair<Double, Double>>(nRutas * nRutas)
        val puntoListo = BooleanArray(nRutas * nRutas)

        fun obtenerPuntoInterseccion(r1: Map<String, Any>, r2: Map<String, Any>): Pair<Double, Double>? {
            val k = (r1["idx"] as Int) * nRutas + (r2["idx"] as Int)
            if (puntoListo[k]) return puntoMemo[k]
            val punto = GrillaRuta.puntoDeCruce(r1["grilla"] as GrillaRuta, r2["grilla"] as GrillaRuta)
            puntoMemo[k] = punto
            puntoListo[k] = true
            return punto
        }

        // haversine(origen, cruce) y haversine(cruce, destino) dependen solo del par de rutas: se guardan
        val havOrigen = DoubleArray(nRutas * nRutas) { Double.NaN }
        val havDestino = DoubleArray(nRutas * nRutas) { Double.NaN }

        fun havDesdeOrigen(a: Map<String, Any>, b: Map<String, Any>, cruce: Pair<Double, Double>): Double {
            val k = (a["idx"] as Int) * nRutas + (b["idx"] as Int)
            var v = havOrigen[k]
            if (v.isNaN()) {
                v = haversine(origenLat, origenLng, cruce.first, cruce.second)
                havOrigen[k] = v
            }
            return v
        }

        fun havHastaDestino(a: Map<String, Any>, b: Map<String, Any>, cruce: Pair<Double, Double>): Double {
            val k = (a["idx"] as Int) * nRutas + (b["idx"] as Int)
            var v = havDestino[k]
            if (v.isNaN()) {
                v = haversine(cruce.first, cruce.second, destinoLat, destinoLng)
                havDestino[k] = v
            }
            return v
        }

        fun calcularDistanciaTotal(path: List<Map<String, Any>>): Double {
            if (path.isEmpty()) return Double.MAX_VALUE
            val dOrigen = path.first()["minDistOrigen"] as Double
            val dDestino = path.last()["minDistDestino"] as Double
            val distVehiculo = when (path.size) {
                1 -> haversine(origenLat, origenLng, destinoLat, destinoLng)
                2 -> {
                    val i = obtenerPuntoInterseccion(path[0], path[1]) ?: return Double.MAX_VALUE
                    havDesdeOrigen(path[0], path[1], i) + havHastaDestino(path[0], path[1], i)
                }
                3 -> {
                    val i1 = obtenerPuntoInterseccion(path[0], path[1]) ?: return Double.MAX_VALUE
                    val i2 = obtenerPuntoInterseccion(path[1], path[2]) ?: return Double.MAX_VALUE
                    havDesdeOrigen(path[0], path[1], i1) +
                            haversine(i1.first, i1.second, i2.first, i2.second) +
                            havHastaDestino(path[1], path[2], i2)
                }
                else -> Double.MAX_VALUE
            }
            if (distVehiculo == Double.MAX_VALUE) return Double.MAX_VALUE
            return distVehiculo + (dOrigen + dDestino) * 3.0
        }

        data class RutaCandidata(
            val path: List<Map<String, Any>>,
            val distanciaTotal: Double,
            val tiempoEstimado: Int,
            val costoTotal: Double
        )

        val exactThreshold = 15.0
        val comparador: Comparator<RutaCandidata> = if (preferencia == "costo") {
            compareByDescending<RutaCandidata> {
                (it.path.first()["minDistOrigen"] as Double) <= exactThreshold &&
                        (it.path.last()["minDistDestino"] as Double) <= exactThreshold
            }.thenBy { it.path.size }.thenBy { it.tiempoEstimado }
        } else {
            compareByDescending<RutaCandidata> {
                (it.path.first()["minDistOrigen"] as Double) <= exactThreshold &&
                        (it.path.last()["minDistDestino"] as Double) <= exactThreshold
            }.thenBy { it.tiempoEstimado }
        }

        // Cada camino se evalúa al vuelo y solo se conserva el mejor (en empate, el primero, igual que
        // sortedWith().firstOrNull()). Con rutas densas hay decenas de miles de caminos de 3 rutas:
        // guardarlos todos agotaba el tiempo y la memoria del teléfono.
        var mejorCandidata: RutaCandidata? = null
        fun considerar(path: List<Map<String, Any>>) {
            val dist = calcularDistanciaTotal(path)
            if (dist == Double.MAX_VALUE) return
            val tiempo = ((dist / 1000.0) * 3.5 + 6.0 + (8.0 * (path.size - 1))).toInt().coerceIn(10, 90)
            val candidata = RutaCandidata(path, dist, tiempo, 1.30 * path.size)
            val actual = mejorCandidata
            if (actual == null || comparador.compare(candidata, actual) < 0) mejorCandidata = candidata
        }

        // Caminos de 1 ruta (directas)
        for (r in rutasOrigenOrdenadas) {
            if (r["codigo"].toString() in codigosDestino) considerar(listOf(r))
        }

        // Caminos de 2 rutas (1 transbordo)
        for (r1 in rutasOrigenOrdenadas) {
            for (r2 in rutasDestinoOrdenadas) {
                if (r1["codigo"] != r2["codigo"] && cachedIntersectan(r1, r2)) {
                    considerar(listOf(r1, r2))
                }
            }
        }

        // Caminos de 3 rutas (2 transbordos). Para cada r1 se calcula una sola vez qué rutas la cruzan,
        // conservando el orden original de la lista para que los empates se resuelvan igual que antes.
        for (r1 in rutasOrigenOrdenadas) {
            val i1 = r1["idx"] as Int
            val vecinasDeR1 = todasLasRutas.filter { (it["idx"] as Int) != i1 && cachedIntersectan(r1, it) }
            if (vecinasDeR1.isEmpty()) continue
            for (r2 in rutasDestinoOrdenadas) {
                val i2 = r2["idx"] as Int
                if (i1 == i2) continue
                for (rm in vecinasDeR1) {
                    if ((rm["idx"] as Int) == i2) continue
                    if (cachedIntersectan(rm, r2)) {
                        considerar(listOf(r1, rm, r2))
                    }
                }
            }
        }

        val elegida = mejorCandidata

        if (elegida != null && consultaExtra.isBlank()) {
            val codigos = elegida.path.map { it["codigo"].toString() }
            val codigosStr = codigos.joinToString(", ")
            val pasos = StringBuilder()
            for (i in elegida.path.indices) {
                val r = elegida.path[i]
                val cod = r["codigo"].toString()
                val num = i + 1
                when {
                    i == 0 && elegida.path.size == 1 ->
                        pasos.append("$num. Tome la combi $cod en el origen, cerca de Punto de origen, y bájese en el destino, cerca de Punto de destino\n")
                    i == 0 ->
                        pasos.append("$num. Tome la combi $cod en el origen, cerca de Punto de origen, y bájese en el punto de transbordo 1\n")
                    i == elegida.path.size - 1 ->
                        pasos.append("$num. Tome la combi $cod en el punto de transbordo $i, y bájese en el destino, cerca de Punto de destino\n")
                    else ->
                        pasos.append("$num. Tome la combi $cod en el punto de transbordo $i, y bájese en el punto de transbordo ${i + 1}\n")
                }
            }
            val costoTexto = if (codigos.size == 1) {
                "S/1.30 (1 combi x S/1.30)"
            } else {
                "S/${String.format(java.util.Locale.US, "%.2f", elegida.costoTotal)} (${codigos.size} combis x S/1.30)"
            }
            val respuestaLocal = """
                RUTAS: [$codigosStr]
                
                ${pasos.toString().trim()}
                
                ESTIMACIÓN: ${elegida.tiempoEstimado} minutos aproximadamente
                COSTO TOTAL: $costoTexto
            """.trimIndent()

            return@withContext ResultadoBusqueda.Local(respuestaLocal, codigos)
        }

        // Sin solución local → preparar datos para IA
        val rutasMedias = todasLasRutas.filter { r ->
            val cod = r["codigo"].toString()
            if (cod in codigosOrigen || cod in codigosDestino) false
            else rutasOrigenOrdenadas.any { o -> cachedIntersectan(r, o) } &&
                    rutasDestinoOrdenadas.any { d -> cachedIntersectan(r, d) }
        }
        val rutasDirectas = rutasOrigenOrdenadas.filter { it["codigo"].toString() in codigosDestino }
        val candidatasUnion = (rutasOrigenOrdenadas + rutasDestinoOrdenadas + rutasMedias).distinctBy { it["codigo"] }
        val rutasFinales = candidatasUnion.map { r ->
            mapOf<String, Any>(
                "codigo" to r["codigo"].toString(),
                "codigoCombi" to r["codigoCombi"].toString(),
                "cuenca" to r["cuenca"].toString(),
                "empresa" to r["empresa"].toString(),
                "avenidas" to r["avenidas"].toString().take(80),
                "avenidaVuelta" to r["avenidaVuelta"].toString().take(80)
            )
        }

        ResultadoBusqueda.NecesitaIA(
            rutasFinales = rutasFinales,
            rutasOrigen = rutasOrigenOrdenadas.map { r ->
                val cod = r["codigo"].toString()
                val dist = r["minDistOrigen"] as Double
                if (dist <= exactThreshold) "$cod (COINCIDENCIA EXACTA)" else cod
            },
            rutasDestino = rutasDestinoOrdenadas.map { r ->
                val cod = r["codigo"].toString()
                val dist = r["minDistDestino"] as Double
                if (dist <= exactThreshold) "$cod (COINCIDENCIA EXACTA)" else cod
            },
            esDirecta = (preferencia == "costo" && rutasDirectas.isNotEmpty())
        )
    }

    /** Fórmula de Haversine — distancia real en metros entre dos coordenadas GPS */
    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

/** Resultado del algoritmo de búsqueda local */
sealed class ResultadoBusqueda {
    /** Se encontró itinerario completo sin necesidad de IA */
    data class Local(
        val respuesta: String,
        val codigos: List<String>
    ) : ResultadoBusqueda()

    /** No se encontró itinerario local — se deben enviar los datos a Groq */
    data class NecesitaIA(
        val rutasFinales: List<Map<String, Any>>,
        val rutasOrigen: List<String>,
        val rutasDestino: List<String>,
        val esDirecta: Boolean
    ) : ResultadoBusqueda()
}

/**
 * Índice espacial de una ruta: agrupa sus puntos en celdas cuadradas un poco mayores que el radio de
 * cruce (≈300 m). Dos puntos a menos de ese radio siempre caen en la misma celda o en celdas vecinas,
 * así que para buscar cruces solo se comparan los puntos de 9 celdas en vez de todos contra todos.
 * Las celdas se guardan en una tabla hash con claves primitivas para no crear objetos en cada consulta.
 */
private class GrillaRuta(puntos: List<Pair<Double, Double>>) {
    val lat = DoubleArray(puntos.size) { puntos[it].first }
    val lng = DoubleArray(puntos.size) { puntos[it].second }
    val nCeldas: Int
    val claveDeCelda: LongArray
    val desde: IntArray   // rango [desde, hasta) dentro de `orden` con los puntos de cada celda
    val hasta: IntArray
    val orden: IntArray   // índices de los puntos agrupados por celda (dentro de cada celda, por latitud creciente)
    val latO: DoubleArray // latitud de cada posición de `orden`
    val lngO: DoubleArray // longitud de cada posición de `orden`
    private val tabla: IntArray
    private val mascara: Int

    init {
        val porCelda = LinkedHashMap<Long, MutableList<Int>>()
        for (i in lat.indices) {
            porCelda.getOrPut(clave(celda(lat[i]), celda(lng[i]))) { mutableListOf() }.add(i)
        }
        nCeldas = porCelda.size
        claveDeCelda = LongArray(nCeldas)
        desde = IntArray(nCeldas)
        hasta = IntArray(nCeldas)
        orden = IntArray(lat.size)
        latO = DoubleArray(lat.size)
        lngO = DoubleArray(lat.size)
        var capacidad = 16
        while (capacidad < nCeldas * 2) capacidad = capacidad shl 1
        mascara = capacidad - 1
        tabla = IntArray(capacidad) { -1 }
        var pos = 0
        var c = 0
        for ((k, lista) in porCelda) {
            claveDeCelda[c] = k
            desde[c] = pos
            lista.sortBy { lat[it] }
            for (i in lista) {
                orden[pos] = i
                latO[pos] = lat[i]
                lngO[pos] = lng[i]
                pos++
            }
            hasta[c] = pos
            var h = mezclar(k) and mascara
            while (tabla[h] != -1) h = (h + 1) and mascara
            tabla[h] = c
            c++
        }
    }

    /** Índice de la celda con esa clave, o -1 si la ruta no tiene puntos ahí. */
    fun buscarCelda(k: Long): Int {
        var h = mezclar(k) and mascara
        while (true) {
            val c = tabla[h]
            if (c == -1) return -1
            if (claveDeCelda[c] == k) return c
            h = (h + 1) and mascara
        }
    }

    companion object {
        const val RADIO_GRADOS = 0.0027
        const val LIMITE_SQ = RADIO_GRADOS * RADIO_GRADOS
        private const val TAM_CELDA = RADIO_GRADOS * 1.01

        private fun celda(v: Double): Int = Math.floor(v / TAM_CELDA).toInt()
        private fun clave(ix: Int, iy: Int): Long = (ix.toLong() shl 32) or (iy.toLong() and 0xFFFFFFFFL)
        private fun mezclar(k: Long): Int = ((k * -7046029254386353131L) ushr 32).toInt()

        /** ¿Hay algún par de puntos, uno de cada ruta, a distancia ≤ radio? */
        fun hayCruce(a: GrillaRuta, b: GrillaRuta): Boolean {
            for (ca in 0 until a.nCeldas) {
                val k = a.claveDeCelda[ca]
                val ix = (k shr 32).toInt()
                val iy = k.toInt()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val cb = b.buscarCelda(clave(ix + dx, iy + dy))
                        if (cb < 0) continue
                        for (pa in a.desde[ca] until a.hasta[ca]) {
                            val la = a.latO[pa]
                            val lo = a.lngO[pa]
                            for (pb in b.desde[cb] until b.hasta[cb]) {
                                val dlat = la - b.latO[pb]
                                val dlng = lo - b.lngO[pb]
                                if (dlat * dlat + dlng * dlng <= LIMITE_SQ) return true
                            }
                        }
                    }
                }
            }
            return false
        }

        /**
         * Punto de [a] más cercano a [b] (a distancia ≤ radio). Si varios empatan, el primero de [a].
         * Devuelve null si las rutas no se cruzan.
         */
        fun puntoDeCruce(a: GrillaRuta, b: GrillaRuta): Pair<Double, Double>? {
            var mejorD = Double.MAX_VALUE
            var mejorI = -1
            for (ca in 0 until a.nCeldas) {
                val k = a.claveDeCelda[ca]
                val ix = (k shr 32).toInt()
                val iy = k.toInt()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val cb = b.buscarCelda(clave(ix + dx, iy + dy))
                        if (cb < 0) continue
                        val b0 = b.desde[cb]
                        val b1 = b.hasta[cb]
                        for (pa in a.desde[ca] until a.hasta[ca]) {
                            val la = a.latO[pa]
                            val lo = a.lngO[pa]
                            val i = a.orden[pa]
                            // Solo pueden servir los puntos con (dlat)^2 <= min(mejorD, LIMITE_SQ).
                            val cota = if (mejorD < LIMITE_SQ) mejorD else LIMITE_SQ
                            val corte = la - Math.sqrt(cota) - 1e-9
                            var lo2 = b0
                            var hi2 = b1
                            while (lo2 < hi2) {
                                val m = (lo2 + hi2) ushr 1
                                if (b.latO[m] < corte) lo2 = m + 1 else hi2 = m
                            }
                            var pb = lo2
                            while (pb < b1) {
                                val dlat = la - b.latO[pb]
                                val dl2 = dlat * dlat
                                val c2 = if (mejorD < LIMITE_SQ) mejorD else LIMITE_SQ
                                if (dlat < 0.0 && dl2 > c2) break
                                val dlng = lo - b.lngO[pb]
                                val d = dl2 + dlng * dlng
                                if (d <= LIMITE_SQ && (d < mejorD || (d == mejorD && i < mejorI))) {
                                    mejorD = d
                                    mejorI = i
                                }
                                pb++
                            }
                        }
                    }
                }
            }
            return if (mejorI < 0) null else Pair(a.lat[mejorI], a.lng[mejorI])
        }
    }
}
