package com.sitp.arequipa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.service.IAService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await

class BusquedaViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val iaService = IAService()

    private val _busquedaState = MutableStateFlow<BusquedaState>(BusquedaState.Idle)
    val busquedaState: StateFlow<BusquedaState> = _busquedaState

    // Cache to avoid fetching from Firestore on every search
    private var cacheRutasSnapshot: com.google.firebase.firestore.QuerySnapshot? = null

    init {
        // Pre-fetch routes in the background during initialization
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                cacheRutasSnapshot = db.collection("rutas").get().await()
                println("DEBUG BusquedaViewModel: Rutas pre-cargadas en caché con éxito.")
            } catch (e: Exception) {
                println("DEBUG BusquedaViewModel: Error al pre-cargar rutas: ${e.message}")
            }
        }
    }

    fun buscarRuta(origen: String, destino: String, preferencia: String) {
        viewModelScope.launch {
            try {
                _busquedaState.value = BusquedaState.Loading

                val snapshot = db.collection("rutas").get().await()
                val rutas = snapshot.documents.map { doc ->
                    mapOf(
                        "codigo"        to (doc.getString("codigo")        ?: ""),
                        "nombre"        to (doc.getString("nombre")        ?: ""),
                        "empresa"       to (doc.getString("empresa")       ?: ""),
                        "avenidas"      to (doc.getString("avenidas")      ?: "").take(80),
                        "avenidaVuelta" to (doc.getString("avenidaVuelta") ?: "").take(80),
                        "color"         to (doc.getString("color")         ?: "")
                    )
                }

                val respuesta = iaService.recomendarRuta(
                    origen      = origen,
                    destino     = destino,
                    preferencia = preferencia,
                    rutas       = rutas
                )

                _busquedaState.value = BusquedaState.Success(respuesta)

            } catch (e: Exception) {
                _busquedaState.value = BusquedaState.Error("Error: ${e.message}")
            }
        }
    }

    fun buscarRutaPorCoordenadas(
        origenLat: Double,
        origenLng: Double,
        destinoLat: Double,
        destinoLng: Double,
        preferencia: String,
        consultaExtra: String = ""
    ) {
        if (_busquedaState.value is BusquedaState.Loading) return
        viewModelScope.launch {
            try {
                _busquedaState.value = BusquedaState.Loading

                // 1. Obtener la base de datos de rutas (desde la caché pre-cargada o Firestore)
                val snapshot = cacheRutasSnapshot ?: db.collection("rutas").get().await().also { cacheRutasSnapshot = it }

                // 2. Resolver el enrutamiento localmente de forma inmediata (usando nombres temporales)
                val localResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    // Cargar todas las rutas estructuradas y pre-parsear puntos/bounding boxes
                    val todasLasRutas = snapshot.documents.mapNotNull { doc ->
                        val codigo = doc.getString("codigo") ?: return@mapNotNull null
                        val nombre = doc.getString("nombre") ?: ""
                        val empresa = doc.getString("empresa") ?: ""
                        val avenidas = doc.getString("avenidas") ?: ""
                        val avenidaVuelta = doc.getString("avenidaVuelta") ?: ""

                        @Suppress("UNCHECKED_CAST")
                        val coordsIda = doc.get("coordenadas")
                                as? List<Map<String, Any>> ?: emptyList()

                        @Suppress("UNCHECKED_CAST")
                        val coordsVuelta = doc.get("coordenadasVuelta")
                                as? List<Map<String, Any>> ?: emptyList()

                        val ptsIda = coordsIda.mapNotNull {
                            val lat = it["lat"] as? Double ?: return@mapNotNull null
                            val lng = it["lng"] as? Double ?: return@mapNotNull null
                            Pair(lat, lng)
                        }
                        val ptsVuelta = coordsVuelta.mapNotNull {
                            val lat = it["lat"] as? Double ?: return@mapNotNull null
                            val lng = it["lng"] as? Double ?: return@mapNotNull null
                            Pair(lat, lng)
                        }
                        val todosPts = ptsIda + ptsVuelta
                        if (todosPts.isEmpty()) return@mapNotNull null

                        val minLat = todosPts.minOf { it.first }
                        val maxLat = todosPts.maxOf { it.first }
                        val minLng = todosPts.minOf { it.second }
                        val maxLng = todosPts.maxOf { it.second }

                        var minDistOrigen = Double.MAX_VALUE
                        var minDistDestino = Double.MAX_VALUE

                        for (p in todosPts) {
                            val dO = distanciaMetros(origenLat, origenLng, p.first, p.second)
                            if (dO < minDistOrigen) minDistOrigen = dO

                            val dD = distanciaMetros(destinoLat, destinoLng, p.first, p.second)
                            if (dD < minDistDestino) minDistDestino = dD
                        }

                        mapOf(
                            "codigo" to codigo,
                            "nombre" to nombre,
                            "empresa" to empresa,
                            "avenidas" to avenidas,
                            "avenidaVuelta" to avenidaVuelta,
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

                    // Filtrar rutas por cercanía (1000m inicial, fallback 2000m)
                    val radio = 1000.0
                    val radioFallback = 2000.0

                    var rutasOrigenFiltradas = todasLasRutas.filter { (it["minDistOrigen"] as Double) <= radio }
                    var rutasDestinoFiltradas = todasLasRutas.filter { (it["minDistDestino"] as Double) <= radio }

                    if (rutasOrigenFiltradas.isEmpty() && rutasDestinoFiltradas.isEmpty()) {
                        rutasOrigenFiltradas = todasLasRutas.filter { (it["minDistOrigen"] as Double) <= radioFallback }
                        rutasDestinoFiltradas = todasLasRutas.filter { (it["minDistDestino"] as Double) <= radioFallback }
                    }

                    // Ordenar por cercanía (closest first)
                    val rutasOrigenOrdenadas = rutasOrigenFiltradas.sortedBy { it["minDistOrigen"] as Double }
                    val rutasDestinoOrdenadas = rutasDestinoFiltradas.sortedBy { it["minDistDestino"] as Double }

                    val codigosOrigen = rutasOrigenOrdenadas.map { it["codigo"].toString() }.toSet()
                    val codigosDestino = rutasDestinoOrdenadas.map { it["codigo"].toString() }.toSet()

                    // Función optimizada para determinar si dos rutas se cruzan/intersectan para transbordo
                    fun intersectanRutas(r1: Map<String, Any>, r2: Map<String, Any>): Boolean {
                        @Suppress("UNCHECKED_CAST")
                        val pts1 = r1["todosPts"] as List<Pair<Double, Double>>
                        @Suppress("UNCHECKED_CAST")
                        val pts2 = r2["todosPts"] as List<Pair<Double, Double>>
                        
                        val minLat1 = r1["minLat"] as Double; val maxLat1 = r1["maxLat"] as Double
                        val minLng1 = r1["minLng"] as Double; val maxLng1 = r1["maxLng"] as Double
                        val minLat2 = r2["minLat"] as Double; val maxLat2 = r2["maxLat"] as Double
                        val minLng2 = r2["minLng"] as Double; val maxLng2 = r2["maxLng"] as Double
                        
                        val margin = 0.0045 // ~500 metros
                        if (maxLat1 < minLat2 - margin || minLat1 > maxLat2 + margin ||
                            maxLng1 < minLng2 - margin || minLng1 > maxLng2 + margin) {
                            return false
                        }
                        
                        val limitSq = 0.0027 * 0.0027 // ~300 metros
                        for (p1 in pts1) {
                            if (p1.first < minLat2 - margin || p1.first > maxLat2 + margin ||
                                p1.second < minLng2 - margin || p1.second > maxLng2 + margin) {
                                continue
                            }
                            for (p2 in pts2) {
                                val dlat = p1.first - p2.first
                                val dlng = p1.second - p2.second
                                if (dlat * dlat + dlng * dlng <= limitSq) return true
                            }
                        }
                        return false
                    }

                    // Memoization map to prevent redundant intersection checks
                    val intersectanMemo = mutableMapOf<Pair<String, String>, Boolean>()
                    fun cachedIntersectan(r1: Map<String, Any>, r2: Map<String, Any>): Boolean {
                        val c1 = r1["codigo"].toString()
                        val c2 = r2["codigo"].toString()
                        val key = if (c1 < c2) Pair(c1, c2) else Pair(c2, c1)
                        return intersectanMemo.getOrPut(key) { intersectanRutas(r1, r2) }
                    }

                    // Función para obtener el punto físico donde dos rutas se cruzan
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
                        
                        val limitSq = 0.0027 * 0.0027 // ~300 metros
                        for (p1 in pts1) {
                            if (p1.first < minLat2 - margin || p1.first > maxLat2 + margin ||
                                p1.second < minLng2 - margin || p1.second > maxLng2 + margin) {
                                continue
                            }
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

                    fun calcularDistanciaTotalRutas(
                        origenLat: Double, origenLng: Double,
                        destinoLat: Double, destinoLng: Double,
                        path: List<Map<String, Any>>
                    ): Double {
                        if (path.isEmpty()) return Double.MAX_VALUE
                        if (path.size == 1) {
                            return distanciaMetros(origenLat, origenLng, destinoLat, destinoLng)
                        }
                        if (path.size == 2) {
                            val i = obtenerPuntoInterseccion(path[0], path[1]) ?: return Double.MAX_VALUE
                            return distanciaMetros(origenLat, origenLng, i.first, i.second) +
                                   distanciaMetros(i.first, i.second, destinoLat, destinoLng)
                        }
                        if (path.size == 3) {
                            val i1 = obtenerPuntoInterseccion(path[0], path[1]) ?: return Double.MAX_VALUE
                            val i2 = obtenerPuntoInterseccion(path[1], path[2]) ?: return Double.MAX_VALUE
                            return distanciaMetros(origenLat, origenLng, i1.first, i1.second) +
                                   distanciaMetros(i1.first, i1.second, i2.first, i2.second) +
                                   distanciaMetros(i2.first, i2.second, destinoLat, destinoLng)
                        }
                        return Double.MAX_VALUE
                    }

                    // Encontrar rutas intermedias (de conexión)
                    val rutasMedias = todasLasRutas.filter { r ->
                        val cod = r["codigo"].toString()
                        if (cod in codigosOrigen || cod in codigosDestino) false
                        else {
                            rutasOrigenOrdenadas.any { o -> cachedIntersectan(r, o) } &&
                            rutasDestinoOrdenadas.any { d -> cachedIntersectan(r, d) }
                        }
                    }

                    // Rutas directas: pasan cerca de AMBOS puntos
                    val rutasDirectas = rutasOrigenOrdenadas.filter { it["codigo"].toString() in codigosDestino }

                    // Estructura de datos para evaluación de combinaciones locales
                    data class RutaCandidata(
                        val path: List<Map<String, Any>>,
                        val distanciaTotal: Double,
                        val tiempoEstimado: Int,
                        val costoTotal: Double
                    )

                    // 1. Directas (1 ruta)
                    val paths1 = mutableListOf<List<Map<String, Any>>>()
                    for (r in rutasOrigenOrdenadas) {
                        if (r["codigo"].toString() in codigosDestino) {
                            paths1.add(listOf(r))
                        }
                    }

                    // 2. 1 transbordo (2 rutas)
                    val paths2 = mutableListOf<List<Map<String, Any>>>()
                    for (r1 in rutasOrigenOrdenadas) {
                        val cod1 = r1["codigo"].toString()
                        for (r2 in rutasDestinoOrdenadas) {
                            val cod2 = r2["codigo"].toString()
                            if (cod1 != cod2 && cachedIntersectan(r1, r2)) {
                                paths2.add(listOf(r1, r2))
                            }
                        }
                    }

                    // 3. 2 transbordos (3 rutas)
                    val paths3 = mutableListOf<List<Map<String, Any>>>()
                    for (r1 in rutasOrigenOrdenadas) {
                        val cod1 = r1["codigo"].toString()
                        for (r2 in rutasDestinoOrdenadas) {
                            val cod2 = r2["codigo"].toString()
                            if (cod1 == cod2) continue
                            for (rm in todasLasRutas) {
                                val codM = rm["codigo"].toString()
                                if (codM == cod1 || codM == cod2) continue
                                if (cachedIntersectan(r1, rm) && cachedIntersectan(rm, r2)) {
                                    paths3.add(listOf(r1, rm, r2))
                                }
                            }
                        }
                    }

                    // Compilar y evaluar candidatas
                    val candidatas = (paths1 + paths2 + paths3).mapNotNull { path ->
                        val dist = calcularDistanciaTotalRutas(origenLat, origenLng, destinoLat, destinoLng, path)
                        if (dist == Double.MAX_VALUE) null
                        else {
                            val tiempo = ((dist / 1000.0) * 3.5 + 6.0 + (8.0 * (path.size - 1))).toInt().coerceIn(10, 90)
                            val costo = 1.30 * path.size
                            RutaCandidata(path, dist, tiempo, costo)
                        }
                    }

                    val elegida = if (preferencia == "costo") {
                        candidatas.sortedWith(compareBy({ it.path.size }, { it.tiempoEstimado })).firstOrNull()
                    } else {
                        candidatas.minByOrNull { it.tiempoEstimado }
                    }

                    if (elegida != null && consultaExtra.isBlank()) {
                        val codigos = elegida.path.map { it["codigo"].toString() }
                        val codigosStr = codigos.joinToString(", ")
                        
                        val pasos = StringBuilder()
                        for (i in elegida.path.indices) {
                            val r = elegida.path[i]
                            val cod = r["codigo"].toString()
                            val num = i + 1
                            if (i == 0 && elegida.path.size == 1) {
                                pasos.append("$num. Tome la combi $cod en el origen, cerca de Punto de origen, y bájese el destino, cerca de Punto de destino\n")
                            } else if (i == 0) {
                                pasos.append("$num. Tome la combi $cod en el origen, cerca de Punto de origen, y bájese el punto de transbordo 1\n")
                            } else if (i == elegida.path.size - 1) {
                                pasos.append("$num. Tome la combi $cod en el punto de transbordo $i, y bájese el destino, cerca de Punto de destino\n")
                            } else {
                                val nextNum = i + 1
                                pasos.append("$num. Tome la combi $cod en el punto de transbordo $i, y bájese el punto de transbordo $nextNum\n")
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
                            
                            ESTIMACION: ${elegida.tiempoEstimado} minutos aproximadamente
                            COSTO TOTAL: $costoTexto
                        """.trimIndent()
                        
                        mapOf(
                            "type" to "local",
                            "respuesta" to respuestaLocal
                        )
                    } else {
                        // Si no hay solución local o hay consultaExtra, pasamos los datos pre-filtrados para la IA
                        val candidatasUnion = (rutasOrigenOrdenadas + rutasDestinoOrdenadas + rutasMedias).distinctBy { it["codigo"] }
                        val rutasFinales = candidatasUnion.map { r ->
                            mapOf<String, Any>(
                                "codigo"        to r["codigo"].toString(),
                                "nombre"        to r["nombre"].toString(),
                                "empresa"       to r["empresa"].toString(),
                                "avenidas"      to r["avenidas"].toString().take(80),
                                "avenidaVuelta" to r["avenidaVuelta"].toString().take(80)
                            )
                        }
                        
                        mapOf(
                            "type" to "ia",
                            "rutasFinales" to rutasFinales,
                            "rutasOrigen" to rutasOrigenOrdenadas.map { it["codigo"].toString() },
                            "rutasDestino" to rutasDestinoOrdenadas.map { it["codigo"].toString() },
                            "esDirecta" to (preferencia == "costo" && rutasDirectas.isNotEmpty())
                        )
                    }
                }

                if (localResult["type"] == "local") {
                    val respuestaLocal = localResult["respuesta"] as String
                    
                    // a) Pintamos el resultado localmente de forma INSTANTÁNEA en 10ms
                    _busquedaState.value = BusquedaState.Success(respuestaLocal)

                    // b) En paralelo y sin demorar al usuario, cargamos los nombres reales de las calles
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val origenDeferred = async { iaService.coordenadasANombre(origenLat, origenLng) }
                            val destinoDeferred = async { iaService.coordenadasANombre(destinoLat, destinoLng) }
                            
                            val origenNombre = origenDeferred.await()
                            val destinoNombre = destinoDeferred.await()
                            
                            val respuestaFinal = respuestaLocal
                                .replace("Punto de origen", origenNombre)
                                .replace("Punto de destino", destinoNombre)
                                
                            _busquedaState.value = BusquedaState.Success(respuestaFinal)
                        } catch (e: Exception) {
                            println("DEBUG BusquedaViewModel: geocoding en segundo plano falló: ${e.message}")
                        }
                    }
                } else {
                    // Fallback a Groq: requiere esperar la geocodificación primero
                    val origenDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                        iaService.coordenadasANombre(origenLat, origenLng)
                    }
                    val destinoDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                        iaService.coordenadasANombre(destinoLat, destinoLng)
                    }
                    
                    val origenNombre = origenDeferred.await()
                    val destinoNombre = destinoDeferred.await()
                    
                    @Suppress("UNCHECKED_CAST")
                    val rutasFinales = localResult["rutasFinales"] as List<Map<String, Any>>
                    @Suppress("UNCHECKED_CAST")
                    val rutasOrigen = localResult["rutasOrigen"] as List<String>
                    @Suppress("UNCHECKED_CAST")
                    val rutasDestino = localResult["rutasDestino"] as List<String>
                    val esDirecta = localResult["esDirecta"] as Boolean

                    val respuesta = iaService.recomendarRuta(
                        origen        = origenNombre,
                        destino       = destinoNombre,
                        preferencia   = preferencia,
                        rutas         = rutasFinales,
                        rutasOrigen   = rutasOrigen,
                        rutasDestino  = rutasDestino,
                        esDirecta     = esDirecta,
                        consultaExtra = consultaExtra
                    )
                    _busquedaState.value = BusquedaState.Success(respuesta)
                }

            } catch (e: Exception) {
                _busquedaState.value = BusquedaState.Error("Hubo un problema: ${e.localizedMessage}")
            }
        }
    }

    // ── Elige el itinerario (avenidas) del sentido que mejor sirve al viaje ──
    // Compara el primer punto de IDA vs el primer punto de VUELTA con el origen.
    // Si no hay coordenadas de vuelta, devuelve siempre avenidas (IDA).
    private fun elegirAvenidaParaIA(
        coordsIda: List<Map<String, Any>>,
        coordsVuelta: List<Map<String, Any>>,
        avenidas: String,
        avenidaVuelta: String,
        origenLat: Double,
        origenLng: Double,
        destinoLat: Double,
        destinoLng: Double
    ): String {
        if (coordsVuelta.isEmpty() || avenidaVuelta.isBlank()) return avenidas

        val idaPrimero = coordsIda.firstOrNull()
        val vueltaPrimero = coordsVuelta.firstOrNull()

        if (idaPrimero == null || vueltaPrimero == null) return avenidas

        val latIda = (idaPrimero["lat"] as? Double) ?: return avenidas
        val lngIda = (idaPrimero["lng"] as? Double) ?: return avenidas
        val latVuelta = (vueltaPrimero["lat"] as? Double) ?: return avenidas
        val lngVuelta = (vueltaPrimero["lng"] as? Double) ?: return avenidas

        val idaUltimo    = coordsIda.lastOrNull()
        val vueltaUltimo = coordsVuelta.lastOrNull()

        val latIdaFin    = (idaUltimo?.get("lat")    as? Double) ?: latIda
        val lngIdaFin    = (idaUltimo?.get("lng")    as? Double) ?: lngIda
        val latVueltaFin = (vueltaUltimo?.get("lat") as? Double) ?: latVuelta
        val lngVueltaFin = (vueltaUltimo?.get("lng") as? Double) ?: lngVuelta

        // Score = distancia inicio al origen + distancia fin al destino
        val scoreIda    = distanciaMetros(latIda,    lngIda,    origenLat, origenLng) +
                distanciaMetros(latIdaFin, lngIdaFin, destinoLat, destinoLng)
        val scoreVuelta = distanciaMetros(latVuelta,    lngVuelta,    origenLat, origenLng) +
                distanciaMetros(latVueltaFin, lngVueltaFin, destinoLat, destinoLng)

        return if (scoreVuelta < scoreIda) avenidaVuelta else avenidas
    }

    // Fórmula de Haversine — distancia real en metros
    private fun distanciaMetros(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    fun resetState() {
        _busquedaState.value = BusquedaState.Idle
    }
}

sealed class BusquedaState {
    object Idle : BusquedaState()
    object Loading : BusquedaState()
    data class Success(val respuesta: String) : BusquedaState()
    data class Error(val mensaje: String) : BusquedaState()
}