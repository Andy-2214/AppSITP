package com.sitp.arequipa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.service.IAService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BusquedaViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val iaService = IAService()

    private val _busquedaState = MutableStateFlow<BusquedaState>(BusquedaState.Idle)
    val busquedaState: StateFlow<BusquedaState> = _busquedaState

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
                        "avenidas"      to (doc.getString("avenidas")      ?: ""),
                        "avenidaVuelta" to (doc.getString("avenidaVuelta") ?: ""),
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

                val origenNombre  = iaService.coordenadasANombre(origenLat,  origenLng)
                val destinoNombre = iaService.coordenadasANombre(destinoLat, destinoLng)
                println("DEBUG origen: $origenNombre")
                println("DEBUG destino: $destinoNombre")

                val snapshot = db.collection("rutas").get().await()

                val RADIO_METROS = 600.0

                val rutasCercaOrigen  = mutableListOf<Map<String, Any>>()
                val rutasCercaDestino = mutableListOf<Map<String, Any>>()

                for (doc in snapshot.documents) {
                    val codigo        = doc.getString("codigo")        ?: continue
                    val nombre        = doc.getString("nombre")        ?: ""
                    val empresa       = doc.getString("empresa")       ?: ""
                    val avenidas      = doc.getString("avenidas")      ?: ""
                    val avenidaVuelta = doc.getString("avenidaVuelta") ?: ""

                    // ── Coordenadas IDA ───────────────────────────────────
                    @Suppress("UNCHECKED_CAST")
                    val coordsIda = doc.get("coordenadas")
                            as? List<Map<String, Any>> ?: emptyList()

                    // ── Coordenadas VUELTA ────────────────────────────────
                    @Suppress("UNCHECKED_CAST")
                    val coordsVuelta = doc.get("coordenadasVuelta")
                            as? List<Map<String, Any>> ?: emptyList()

                    // Combinar ambos sentidos para la detección de proximidad.
                    // Si la ruta pasa cerca en IDA O en VUELTA, se considera candidata.
                    val todosPuntos = coordsIda + coordsVuelta

                    var cercaOrigen  = false
                    var cercaDestino = false

                    for (punto in todosPuntos) {
                        val lat = (punto["lat"] as? Double) ?: continue
                        val lng = (punto["lng"] as? Double) ?: continue

                        if (!cercaOrigen  && distanciaMetros(origenLat,  origenLng,  lat, lng) <= RADIO_METROS)
                            cercaOrigen = true
                        if (!cercaDestino && distanciaMetros(destinoLat, destinoLng, lat, lng) <= RADIO_METROS)
                            cercaDestino = true

                        if (cercaOrigen && cercaDestino) break
                    }

                    // ── Determinar cuál sentido sirve para este viaje ─────
                    // La IA recibe el itinerario del sentido cuyo inicio
                    // está más cerca del origen del usuario.
                    val avenidaParaIA = elegirAvenidaParaIA(
                        coordsIda       = coordsIda,
                        coordsVuelta    = coordsVuelta,
                        avenidas        = avenidas,
                        avenidaVuelta   = avenidaVuelta,
                        origenLat       = origenLat,
                        origenLng       = origenLng,
                        destinoLat      = destinoLat,
                        destinoLng      = destinoLng
                    )

                    val rutaInfo = mapOf<String, Any>(
                        "codigo"   to codigo,
                        "nombre"   to nombre,
                        "empresa"  to empresa,
                        // Itinerario del sentido correcto, truncado para ahorrar tokens
                        "avenidas" to avenidaParaIA.take(300)
                    )

                    if (cercaOrigen)  rutasCercaOrigen.add(rutaInfo)
                    if (cercaDestino) rutasCercaDestino.add(rutaInfo)
                }

                println("DEBUG rutas cerca origen  (${rutasCercaOrigen.size}): ${rutasCercaOrigen.map { it["codigo"] }}")
                println("DEBUG rutas cerca destino (${rutasCercaDestino.size}): ${rutasCercaDestino.map { it["codigo"] }}")

                // Rutas directas: pasan cerca de AMBOS puntos
                val codigosDestino = rutasCercaDestino.map { it["codigo"] }.toSet()
                val rutasDirectas  = rutasCercaOrigen.filter { it["codigo"] in codigosDestino }

                val rutasParaIA: List<Map<String, Any>> = if (rutasDirectas.isNotEmpty()) {
                    println("DEBUG rutas directas: ${rutasDirectas.map { it["codigo"] }}")
                    rutasDirectas
                } else {
                    val combinadas = (rutasCercaOrigen + rutasCercaDestino).distinctBy { it["codigo"] }
                    println("DEBUG sin directa, combinando ${combinadas.size} candidatas")
                    combinadas
                }

                // Fallback: ampliar a 1200m si no hay nada en 600m
                val rutasFinales: List<Map<String, Any>> = if (rutasParaIA.isEmpty()) {
                    println("DEBUG sin rutas en 600m, ampliando a 1200m")
                    snapshot.documents.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        val coordsIda    = doc.get("coordenadas")
                                as? List<Map<String, Any>> ?: emptyList()
                        @Suppress("UNCHECKED_CAST")
                        val coordsVuelta = doc.get("coordenadasVuelta")
                                as? List<Map<String, Any>> ?: emptyList()
                        val todosPuntos  = coordsIda + coordsVuelta

                        val cercaOrigen  = todosPuntos.any { p ->
                            distanciaMetros(origenLat,  origenLng,
                                (p["lat"] as? Double) ?: 0.0, (p["lng"] as? Double) ?: 0.0) <= 1200.0
                        }
                        val cercaDestino = todosPuntos.any { p ->
                            distanciaMetros(destinoLat, destinoLng,
                                (p["lat"] as? Double) ?: 0.0, (p["lng"] as? Double) ?: 0.0) <= 1200.0
                        }
                        if (!cercaOrigen && !cercaDestino) return@mapNotNull null

                        val avenidas      = doc.getString("avenidas")      ?: ""
                        val avenidaVuelta = doc.getString("avenidaVuelta") ?: ""
                        val avenidaParaIA = elegirAvenidaParaIA(
                            coordsIda     = coordsIda,
                            coordsVuelta  = coordsVuelta,
                            avenidas      = avenidas,
                            avenidaVuelta = avenidaVuelta,
                            origenLat     = origenLat,
                            origenLng     = origenLng,
                            destinoLat    = destinoLat,
                            destinoLng    = destinoLng
                        )
                        mapOf<String, Any>(
                            "codigo"   to (doc.getString("codigo")  ?: ""),
                            "nombre"   to (doc.getString("nombre")  ?: ""),
                            "empresa"  to (doc.getString("empresa") ?: ""),
                            "avenidas" to avenidaParaIA.take(300)
                        )
                    }
                } else rutasParaIA

                val respuesta = iaService.recomendarRuta(
                    origen        = origenNombre,
                    destino       = destinoNombre,
                    preferencia   = preferencia,
                    rutas         = rutasFinales,
                    rutasOrigen   = rutasCercaOrigen.map  { it["codigo"].toString() },
                    rutasDestino  = rutasCercaDestino.map { it["codigo"].toString() },
                    esDirecta     = rutasDirectas.isNotEmpty(),
                    consultaExtra = consultaExtra
                )

                _busquedaState.value = BusquedaState.Success(respuesta)

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