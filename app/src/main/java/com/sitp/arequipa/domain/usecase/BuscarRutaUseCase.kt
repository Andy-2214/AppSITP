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
            val coordsIda = ptsIda.map { mapOf("lat" to it.first, "lng" to it.second) }
            val coordsVuelta = ptsVuelta.map { mapOf("lat" to it.first, "lng" to it.second) }
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
                "nombre" to r.nombre,
                "empresa" to r.empresa,
                "avenidas" to r.avenidas,
                "avenidaVuelta" to r.avenidaVuelta,
                "coordsIda" to coordsIda,
                "coordsVuelta" to coordsVuelta,
                "ptsIda" to ptsIda,
                "ptsVuelta" to ptsVuelta,
                "todosPts" to todosPts,
                "minLat" to minLat,
                "maxLat" to maxLat,
                "minLng" to minLng,
                "maxLng" to maxLng,
                "minDistOrigen" to minDistOrigen,
                "minDistDestino" to minDistDestino
            )
        }

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

        val intersectanMemo = mutableMapOf<Pair<String, String>, Boolean>()

        fun intersectanRutas(r1: Map<String, Any>, r2: Map<String, Any>): Boolean {
            @Suppress("UNCHECKED_CAST")
            val pts1 = r1["todosPts"] as List<Pair<Double, Double>>
            @Suppress("UNCHECKED_CAST")
            val pts2 = r2["todosPts"] as List<Pair<Double, Double>>
            val minLat1 = r1["minLat"] as Double; val maxLat1 = r1["maxLat"] as Double
            val minLng1 = r1["minLng"] as Double; val maxLng1 = r1["maxLng"] as Double
            val minLat2 = r2["minLat"] as Double; val maxLat2 = r2["maxLat"] as Double
            val minLng2 = r2["minLng"] as Double; val maxLng2 = r2["maxLng"] as Double
            val margin = 0.0045
            if (maxLat1 < minLat2 - margin || minLat1 > maxLat2 + margin ||
                maxLng1 < minLng2 - margin || minLng1 > maxLng2 + margin) return false
            val limitSq = 0.0027 * 0.0027
            for (p1 in pts1) {
                if (p1.first < minLat2 - margin || p1.first > maxLat2 + margin ||
                    p1.second < minLng2 - margin || p1.second > maxLng2 + margin) continue
                for (p2 in pts2) {
                    val dlat = p1.first - p2.first
                    val dlng = p1.second - p2.second
                    if (dlat * dlat + dlng * dlng <= limitSq) return true
                }
            }
            return false
        }

        fun cachedIntersectan(r1: Map<String, Any>, r2: Map<String, Any>): Boolean {
            val c1 = r1["codigo"].toString()
            val c2 = r2["codigo"].toString()
            val key = if (c1 < c2) Pair(c1, c2) else Pair(c2, c1)
            return intersectanMemo.getOrPut(key) { intersectanRutas(r1, r2) }
        }

        fun obtenerPuntoInterseccion(r1: Map<String, Any>, r2: Map<String, Any>): Pair<Double, Double>? {
            @Suppress("UNCHECKED_CAST")
            val pts1 = r1["todosPts"] as List<Pair<Double, Double>>
            @Suppress("UNCHECKED_CAST")
            val pts2 = r2["todosPts"] as List<Pair<Double, Double>>
            val minLat2 = r2["minLat"] as Double; val maxLat2 = r2["maxLat"] as Double
            val minLng2 = r2["minLng"] as Double; val maxLng2 = r2["maxLng"] as Double
            val margin = 0.0045
            var bestPt: Pair<Double, Double>? = null
            var minDistSq = Double.MAX_VALUE
            val limitSq = 0.0027 * 0.0027
            for (p1 in pts1) {
                if (p1.first < minLat2 - margin || p1.first > maxLat2 + margin ||
                    p1.second < minLng2 - margin || p1.second > maxLng2 + margin) continue
                for (p2 in pts2) {
                    val dlat = p1.first - p2.first
                    val dlng = p1.second - p2.second
                    val distSq = dlat * dlat + dlng * dlng
                    if (distSq <= limitSq && distSq < minDistSq) {
                        minDistSq = distSq
                        bestPt = p1
                    }
                }
            }
            return bestPt
        }

        fun calcularDistanciaTotal(path: List<Map<String, Any>>): Double {
            if (path.isEmpty()) return Double.MAX_VALUE
            val dOrigen = path.first()["minDistOrigen"] as Double
            val dDestino = path.last()["minDistDestino"] as Double
            val distVehiculo = when (path.size) {
                1 -> haversine(origenLat, origenLng, destinoLat, destinoLng)
                2 -> {
                    val i = obtenerPuntoInterseccion(path[0], path[1]) ?: return Double.MAX_VALUE
                    haversine(origenLat, origenLng, i.first, i.second) +
                            haversine(i.first, i.second, destinoLat, destinoLng)
                }
                3 -> {
                    val i1 = obtenerPuntoInterseccion(path[0], path[1]) ?: return Double.MAX_VALUE
                    val i2 = obtenerPuntoInterseccion(path[1], path[2]) ?: return Double.MAX_VALUE
                    haversine(origenLat, origenLng, i1.first, i1.second) +
                            haversine(i1.first, i1.second, i2.first, i2.second) +
                            haversine(i2.first, i2.second, destinoLat, destinoLng)
                }
                else -> Double.MAX_VALUE
            }
            if (distVehiculo == Double.MAX_VALUE) return Double.MAX_VALUE
            return distVehiculo + (dOrigen + dDestino) * 3.0
        }

        // Buscar paths de 1 ruta (directas)
        val paths1 = rutasOrigenOrdenadas
            .filter { it["codigo"].toString() in codigosDestino }
            .map { listOf(it) }

        // Buscar paths de 2 rutas (1 transbordo)
        val paths2 = mutableListOf<List<Map<String, Any>>>()
        for (r1 in rutasOrigenOrdenadas) {
            for (r2 in rutasDestinoOrdenadas) {
                if (r1["codigo"] != r2["codigo"] && cachedIntersectan(r1, r2)) {
                    paths2.add(listOf(r1, r2))
                }
            }
        }

        // Buscar paths de 3 rutas (2 transbordos)
        val paths3 = mutableListOf<List<Map<String, Any>>>()
        for (r1 in rutasOrigenOrdenadas) {
            for (r2 in rutasDestinoOrdenadas) {
                if (r1["codigo"] == r2["codigo"]) continue
                for (rm in todasLasRutas) {
                    val codM = rm["codigo"].toString()
                    if (codM == r1["codigo"].toString() || codM == r2["codigo"].toString()) continue
                    if (cachedIntersectan(r1, rm) && cachedIntersectan(rm, r2)) {
                        paths3.add(listOf(r1, rm, r2))
                    }
                }
            }
        }

        data class RutaCandidata(
            val path: List<Map<String, Any>>,
            val distanciaTotal: Double,
            val tiempoEstimado: Int,
            val costoTotal: Double
        )

        val candidatas = (paths1 + paths2 + paths3).mapNotNull { path ->
            val dist = calcularDistanciaTotal(path)
            if (dist == Double.MAX_VALUE) null
            else {
                val tiempo = ((dist / 1000.0) * 3.5 + 6.0 + (8.0 * (path.size - 1))).toInt().coerceIn(10, 90)
                val costo = 1.30 * path.size
                RutaCandidata(path, dist, tiempo, costo)
            }
        }

        val exactThreshold = 15.0
        val elegida = if (preferencia == "costo") {
            candidatas.sortedWith(
                compareByDescending<RutaCandidata> {
                    (it.path.first()["minDistOrigen"] as Double) <= exactThreshold &&
                            (it.path.last()["minDistDestino"] as Double) <= exactThreshold
                }.thenBy { it.path.size }.thenBy { it.tiempoEstimado }
            ).firstOrNull()
        } else {
            candidatas.sortedWith(
                compareByDescending<RutaCandidata> {
                    (it.path.first()["minDistOrigen"] as Double) <= exactThreshold &&
                            (it.path.last()["minDistDestino"] as Double) <= exactThreshold
                }.thenBy { it.tiempoEstimado }
            ).firstOrNull()
        }

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
                "nombre" to r["nombre"].toString(),
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
