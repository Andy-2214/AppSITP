package com.sitp.arequipa.ui.map

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import com.sitp.arequipa.ui.busqueda.BusquedaSheet
import com.sitp.arequipa.ui.comentarios.ComentariosSheet
import com.sitp.arequipa.ui.historial.HistorialScreen
import com.sitp.arequipa.ui.perfil.PerfilScreen
import com.sitp.arequipa.viewmodel.ComentarioState
import com.sitp.arequipa.viewmodel.ComentarioViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

// ── Paleta ──────────────────────────────────────────────────────────────────
private val NavRed       = Color(0xFFC62828)
private val NavSecondary = Color(0xFF424242)
private val NavNeutral   = Color(0xFFF5F5F5)
private val NavGray      = Color(0xFF9E9E9E)

// ── Modelo — ahora incluye campos de VUELTA ───────────────────────────────────
data class RutaMapa(
    val id: String = "",
    val nombre: String = "",
    val codigo: String = "",
    val empresa: String = "",
    val color: String = "#FF0000",
    val etiqueta: String = "",
    val avenidas: String = "",           // recorrido IDA
    val avenidaVuelta: String = "",      // recorrido VUELTA
    val frecuencia: String = "",
    val coordenadas: List<Map<String, Double>> = emptyList(),       // polyline IDA
    val coordenadasVuelta: List<Map<String, Double>> = emptyList()  // polyline VUELTA
)

// ── Guía visual GPS ───────────────────────────────────────────────────────────
data class TramoGuia(
    val puntos: List<LatLng>,
    val color: Color,
    val esCaminata: Boolean = false
)

data class MarkerRutaGuia(
    val posicion: LatLng,
    val codigo: String,
    val color: String
)

// ── Selecciona coordenadas IDA o VUELTA según la dirección del viaje ──────────
// Devuelve el listado de coordenadas cuyo primer punto esté más cerca del origen.
// Si la ruta solo tiene coordenadas de IDA (vuelta vacía), devuelve las de IDA.
fun elegirDireccion(
    ruta: RutaMapa,
    origenLatLng: LatLng,
    destinoLatLng: LatLng
): List<Map<String, Double>> {
    if (ruta.coordenadasVuelta.isEmpty()) return ruta.coordenadas  // sin vuelta → IDA siempre

    fun distSq(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dl = lat1 - lat2
        val dn = lng1 - lng2
        return dl * dl + dn * dn
    }

    // Para IDA
    val ptsIda = ruta.coordenadas
    var idxOIda = 0
    var idxDIda = 0
    var minDistOIda = Double.MAX_VALUE
    var minDistDIda = Double.MAX_VALUE
    ptsIda.forEachIndexed { idx, pt ->
        val lat = pt["lat"] ?: 0.0
        val lng = pt["lng"] ?: 0.0
        val dO = distSq(lat, lng, origenLatLng.latitude, origenLatLng.longitude)
        if (dO < minDistOIda) {
            minDistOIda = dO
            idxOIda = idx
        }
        val dD = distSq(lat, lng, destinoLatLng.latitude, destinoLatLng.longitude)
        if (dD < minDistDIda) {
            minDistDIda = dD
            idxDIda = idx
        }
    }
    val isForwardIda = idxOIda <= idxDIda
    val distSumIda = minDistOIda + minDistDIda

    // Para VUELTA
    val ptsVuelta = ruta.coordenadasVuelta
    var idxOVuelta = 0
    var idxDVuelta = 0
    var minDistOVuelta = Double.MAX_VALUE
    var minDistDVuelta = Double.MAX_VALUE
    ptsVuelta.forEachIndexed { idx, pt ->
        val lat = pt["lat"] ?: 0.0
        val lng = pt["lng"] ?: 0.0
        val dO = distSq(lat, lng, origenLatLng.latitude, origenLatLng.longitude)
        if (dO < minDistOVuelta) {
            minDistOVuelta = dO
            idxOVuelta = idx
        }
        val dD = distSq(lat, lng, destinoLatLng.latitude, destinoLatLng.longitude)
        if (dD < minDistDVuelta) {
            minDistDVuelta = dD
            idxDVuelta = idx
        }
    }
    val isForwardVuelta = idxOVuelta <= idxDVuelta
    val distSumVuelta = minDistOVuelta + minDistDVuelta

    // Decidir:
    return when {
        isForwardIda && !isForwardVuelta -> ruta.coordenadas
        isForwardVuelta && !isForwardIda -> ruta.coordenadasVuelta
        else -> {
            // Si ambos son forward o ambos son backward, elegimos el que pase más cerca del origen/destino
            if (distSumVuelta < distSumIda) ruta.coordenadasVuelta else ruta.coordenadas
        }
    }
}

fun calcularTramo(
    coordenadas: List<Map<String, Double>>,
    desdeLatLng: LatLng,
    hastaLatLng: LatLng
): List<LatLng> {
    val puntos = coordenadas.mapNotNull { coord ->
        val lat = coord["lat"] ?: return@mapNotNull null
        val lng = coord["lng"] ?: return@mapNotNull null
        LatLng(lat, lng)
    }
    if (puntos.size < 2) return emptyList()

    fun distMetros(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val x = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
                Math.sin(dLng/2) * Math.sin(dLng/2)
        return R * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1-x))
    }

    val idxOrigen  = puntos.indices.minByOrNull { distMetros(puntos[it], desdeLatLng)  } ?: 0
    val idxDestino = puntos.indices.minByOrNull { distMetros(puntos[it], hastaLatLng) } ?: puntos.lastIndex

    val (inicio, fin) = if (idxOrigen <= idxDestino)
        Pair(idxOrigen, idxDestino)
    else
        Pair(idxDestino, idxOrigen)

    val subtramo = puntos.subList(inicio, fin + 1)
        .let { if (idxOrigen > idxDestino) it.reversed() else it }

    println("  calcularTramo: idxOrigen=$idxOrigen idxDestino=$idxDestino total=${puntos.size} subtramo=${subtramo.size}")
    // Anclar exactamente al pin de origen y destino
    return listOf(desdeLatLng) + subtramo + listOf(hastaLatLng)
}

fun crearTramos(
    segmento: List<LatLng>,
    colorBus: Color,
    agregarInicio: Boolean = true,
    agregarFin: Boolean = true
): List<TramoGuia> {
    if (segmento.size < 3) return listOf(TramoGuia(segmento, colorBus, false))
    val tramos = mutableListOf<TramoGuia>()
    val pInicio = segmento.first()
    val pBusInicio = segmento[1]
    val pBusFin = segmento[segmento.size - 2]
    val pFin = segmento.last()
    val busSegment = segmento.subList(1, segmento.size - 1)
    if (agregarInicio && pInicio != pBusInicio) {
        tramos.add(TramoGuia(listOf(pInicio, pBusInicio), Color.Gray, true))
    }
    if (busSegment.size >= 2) {
        tramos.add(TramoGuia(busSegment, colorBus, false))
    }
    if (agregarFin && pFin != pBusFin) {
        tramos.add(TramoGuia(listOf(pBusFin, pFin), Color.Gray, true))
    }
    return tramos
}

fun encontrarAcercamiento(
    coords1: List<Map<String, Double>>,
    coords2: List<Map<String, Double>>
): Triple<LatLng, LatLng, Double> {
    val pts1 = coords1.mapNotNull { c ->
        val la = c["lat"] ?: return@mapNotNull null
        val ln = c["lng"] ?: return@mapNotNull null
        LatLng(la, ln)
    }
    val pts2 = coords2.mapNotNull { c ->
        val la = c["lat"] ?: return@mapNotNull null
        val ln = c["lng"] ?: return@mapNotNull null
        LatLng(la, ln)
    }
    if (pts1.isEmpty() || pts2.isEmpty()) {
        return Triple(LatLng(0.0, 0.0), LatLng(0.0, 0.0), Double.MAX_VALUE)
    }

    var bestPt1 = pts1.first()
    var bestPt2 = pts2.first()
    var minDist = Double.MAX_VALUE

    for (p1 in pts1) {
        for (p2 in pts2) {
            val dlat = p1.latitude - p2.latitude
            val dlng = p1.longitude - p2.longitude
            val d = dlat * dlat + dlng * dlng
            if (d < minDist) {
                minDist = d
                bestPt1 = p1
                bestPt2 = p2
            }
        }
    }
    return Triple(bestPt1, bestPt2, minDist)
}

// ── Utilidades ───────────────────────────────────────────────────────────────
@SuppressLint("MissingPermission")
fun obtenerUbicacion(
    context: android.content.Context,
    onUbicacion: (android.location.Location) -> Unit
) {
    LocationServices.getFusedLocationProviderClient(context)
        .lastLocation
        .addOnSuccessListener { location -> location?.let { onUbicacion(it) } }
}

fun puntoEnRuta(click: LatLng, puntos: List<LatLng>, tolerancia: Double = 0.002): Boolean {
    for (i in 0 until puntos.size - 1) {
        val lat = (puntos[i].latitude + puntos[i + 1].latitude) / 2
        val lng = (puntos[i].longitude + puntos[i + 1].longitude) / 2
        if (Math.abs(click.latitude - lat) < tolerancia &&
            Math.abs(click.longitude - lng) < tolerancia
        ) return true
    }
    return false
}

fun String.toLatLng(): LatLng? = try {
    val parts = this.split(",").map { it.trim() }
    LatLng(parts[0].toDouble(), parts[1].toDouble())
} catch (e: Exception) { null }

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) { Color.Red }

// ── Tabs del nav inferior ────────────────────────────────────────────────────
private enum class NavTab { MAPA, HISTORIAL, PERFIL }

// ── Pantalla principal ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    onLogout: () -> Unit,
    onPerfil: () -> Unit,
    onHistorial: () -> Unit,
    origenInicial: String? = null,
    destinoInicial: String? = null,
    preferenciaInicial: String? = null
) {
    val arequipa = LatLng(-16.4090, -71.5375)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(arequipa, 13f)
    }

    var rutas by remember { mutableStateOf<List<RutaMapa>>(emptyList()) }
    var rutasVisibles by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var rutasResaltadas by remember { mutableStateOf<Set<String>>(emptySet()) }

    var tramosGuia by remember { mutableStateOf<List<TramoGuia>>(emptyList()) }
    var guiaActiva by remember { mutableStateOf(true) }
    var codigosPendientes by remember { mutableStateOf<List<String>>(emptyList()) }

    var modoSeleccion by remember { mutableStateOf<String?>(null) }
    var rutaDetalle by remember { mutableStateOf<RutaMapa?>(null) }
    var mostrarComentarios by remember { mutableStateOf(false) }
    // Guarda la dirección elegida por cada ruta: rutaId -> true (VUELTA) / false (IDA)
    var direccionPorRuta by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    // Guarda las coordenadas seleccionadas para cada código recomendado en la búsqueda activa
    var coordsRecomendadas by remember { mutableStateOf<Map<String, List<Map<String, Double>>>>(emptyMap()) }
    var marcadoresRuta by remember { mutableStateOf<List<MarkerRutaGuia>>(emptyList()) }
    var marcadoresTransbordo by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var origenLatLng by remember { mutableStateOf(origenInicial?.toLatLng()) }
    var destinoLatLng by remember { mutableStateOf(destinoInicial?.toLatLng()) }
    var mostrarBusqueda by remember {
        mutableStateOf(origenInicial != null && destinoInicial != null)
    }

    var tabActivo by remember { mutableStateOf(NavTab.MAPA) }
    var showRutasSheet by remember { mutableStateOf(false) }
    val rutasSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Carga rutas — ahora incluye coordenadasVuelta y avenidaVuelta ────────
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("rutas").get()
            .addOnSuccessListener { snapshot ->
                rutas = snapshot.documents.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    RutaMapa(
                        id              = doc.id,
                        nombre          = doc.getString("nombre")       ?: "",
                        codigo          = doc.getString("codigo")        ?: "",
                        empresa         = doc.getString("empresa")       ?: "Sin empresa",
                        color           = doc.getString("color")         ?: "#FF0000",
                        etiqueta        = doc.getString("etiqueta") ?: doc.getString("etiquetas") ?: "",
                        avenidas        = doc.getString("avenidas")      ?: "",
                        avenidaVuelta   = doc.getString("avenidaVuelta") ?: "",
                        frecuencia      = doc.getString("frecuencia")    ?: "",
                        coordenadas       = doc.get("coordenadas")
                                as? List<Map<String, Double>> ?: emptyList(),
                        coordenadasVuelta = doc.get("coordenadasVuelta")
                                as? List<Map<String, Double>> ?: emptyList()
                    )
                }
            }
    }

    // ── Recalcula tramos cuando rutas llega después de onRutaEncontrada ──────
    LaunchedEffect(rutas, codigosPendientes) {
        if (rutas.isEmpty() || codigosPendientes.isEmpty() ||
            origenLatLng == null || destinoLatLng == null) {
            coordsRecomendadas = emptyMap()
            tramosGuia = emptyList()
            marcadoresRuta = emptyList()
            marcadoresTransbordo = emptyList()
            guiaActiva = false
            return@LaunchedEffect
        }

        println("DEBUG recalculando tramos con ${rutas.size} rutas y codigos $codigosPendientes")
        val rutasRecomendadas = codigosPendientes.mapNotNull { codigo ->
            rutas.firstOrNull { it.codigo.equals(codigo, ignoreCase = true) }
        }
        println("DEBUG rutasRecomendadas: ${rutasRecomendadas.map { it.codigo }}")

        val tramos = mutableListOf<TramoGuia>()
        val n = rutasRecomendadas.size
        val nextCoordsRecomendadas = mutableMapOf<String, List<Map<String, Double>>>()
        val nextDireccion = direccionPorRuta.toMutableMap()
        val nextMarcadoresRuta = mutableListOf<MarkerRutaGuia>()
        val nextMarcadoresTransbordo = mutableListOf<LatLng>()

        if (n == 1) {
            val ruta = rutasRecomendadas[0]
            val coords = elegirDireccion(ruta, origenLatLng!!, destinoLatLng!!)
            nextCoordsRecomendadas[ruta.codigo.trim()] = coords
            
            val esVuelta = ruta.coordenadasVuelta.isNotEmpty() && coords == ruta.coordenadasVuelta
            nextDireccion[ruta.id] = esVuelta

            val segmento = calcularTramo(coords, origenLatLng!!, destinoLatLng!!)
            if (segmento.isNotEmpty()) {
                tramos.addAll(crearTramos(segmento, parseColor(ruta.color)))
                val posBadge = obtenerPuntoMasAdelante(segmento, startIndex = 1, metros = 10.0)
                nextMarcadoresRuta.add(MarkerRutaGuia(posicion = posBadge, codigo = ruta.codigo, color = ruta.color))
            }
        } else if (n >= 2) {
            val coordsList = mutableListOf<List<Map<String, Double>>>()
            val transferPoints = mutableListOf<Pair<LatLng, LatLng>>()

            // 1. Elegir dirección para la primera ruta
            val coords0 = elegirDireccion(rutasRecomendadas[0], origenLatLng!!, destinoLatLng!!)
            coordsList.add(coords0)
            nextCoordsRecomendadas[rutasRecomendadas[0].codigo.trim()] = coords0
            
            val esVuelta0 = rutasRecomendadas[0].coordenadasVuelta.isNotEmpty() && coords0 == rutasRecomendadas[0].coordenadasVuelta
            nextDireccion[rutasRecomendadas[0].id] = esVuelta0

            // 2. Propagar para encontrar acercamientos de rutas intermedias
            for (i in 1 until n) {
                val prevCoords = coordsList[i - 1]
                val rCurrent = rutasRecomendadas[i]

                val (transPrevIda, transCurrIda, minDistIda) = encontrarAcercamiento(prevCoords, rCurrent.coordenadas)
                val (transPrevVuelta, transCurrVuelta, minDistVuelta) = if (rCurrent.coordenadasVuelta.isNotEmpty()) {
                    encontrarAcercamiento(prevCoords, rCurrent.coordenadasVuelta)
                } else {
                    Triple(LatLng(0.0, 0.0), LatLng(0.0, 0.0), Double.MAX_VALUE)
                }

                val (transPrev, transCurr, coordsCurrent) = if (minDistVuelta < minDistIda) {
                    Triple(transPrevVuelta, transCurrVuelta, rCurrent.coordenadasVuelta)
                } else {
                    Triple(transPrevIda, transCurrIda, rCurrent.coordenadas)
                }

                coordsList.add(coordsCurrent)
                transferPoints.add(Pair(transPrev, transCurr))
                
                nextCoordsRecomendadas[rCurrent.codigo.trim()] = coordsCurrent
                val esVueltaCurrent = rCurrent.coordenadasVuelta.isNotEmpty() && coordsCurrent == rCurrent.coordenadasVuelta
                nextDireccion[rCurrent.id] = esVueltaCurrent
            }

            // 3. Construir tramos para cada una de las N rutas
            for (i in 0 until n) {
                val r = rutasRecomendadas[i]
                val coords = coordsList[i]
                val startPt = if (i == 0) origenLatLng!! else transferPoints[i - 1].second
                val endPt = if (i == n - 1) destinoLatLng!! else transferPoints[i].first

                val segmento = calcularTramo(coords, startPt, endPt)
                if (segmento.isNotEmpty()) {
                    tramos.addAll(
                        crearTramos(
                            segmento = segmento,
                            colorBus = parseColor(r.color),
                            agregarInicio = (i == 0),
                            agregarFin = (i == n - 1)
                        )
                    )
                    val metrosDesplazamiento = if (i == 0) 10.0 else 150.0
                    val posBadge = obtenerPuntoMasAdelante(segmento, startIndex = 1, metros = metrosDesplazamiento)
                    nextMarcadoresRuta.add(MarkerRutaGuia(posicion = posBadge, codigo = r.codigo, color = r.color))
                }
            }

            for (i in 0 until n - 1) {
                nextMarcadoresTransbordo.add(transferPoints[i].first)
            }
        }
        coordsRecomendadas = nextCoordsRecomendadas
        direccionPorRuta = nextDireccion
        tramosGuia = tramos
        marcadoresRuta = nextMarcadoresRuta
        marcadoresTransbordo = nextMarcadoresTransbordo
        guiaActiva = true
    }

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) {
            obtenerUbicacion(context) { location ->
                scope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude), 15f
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp, color = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.DirectionsBus, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SITP Arequipa", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = NavRed)
                    }
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = tabActivo == NavTab.MAPA && !showRutasSheet,
                        onClick = { showRutasSheet = false; tabActivo = NavTab.MAPA },
                        icon = {
                            NavIcon(
                                icon = { sz ->
                                    Icon(Icons.Filled.Map, "Mapa",
                                        tint = if (tabActivo == NavTab.MAPA && !showRutasSheet) Color.White else NavGray,
                                        modifier = Modifier.size(sz))
                                },
                                active = tabActivo == NavTab.MAPA && !showRutasSheet
                            )
                        },
                        label = {
                            Text("Mapa", fontSize = 11.sp,
                                fontWeight = if (tabActivo == NavTab.MAPA && !showRutasSheet) FontWeight.Bold else FontWeight.Normal,
                                color = if (tabActivo == NavTab.MAPA && !showRutasSheet) NavRed else NavGray)
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        selected = showRutasSheet,
                        onClick = {
                            if (showRutasSheet) {
                                scope.launch { rutasSheetState.hide() }
                                    .invokeOnCompletion { showRutasSheet = false }
                            } else {
                                tabActivo = NavTab.MAPA
                                showRutasSheet = true
                            }
                        },
                        icon = {
                            Icon(Icons.Filled.DirectionsBus, "Rutas",
                                tint = if (showRutasSheet) NavRed else NavGray,
                                modifier = Modifier.size(22.dp))
                        },
                        label = {
                            Text("Rutas", fontSize = 11.sp,
                                fontWeight = if (showRutasSheet) FontWeight.Bold else FontWeight.Normal,
                                color = if (showRutasSheet) NavRed else NavGray)
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        selected = tabActivo == NavTab.HISTORIAL,
                        onClick = { showRutasSheet = false; tabActivo = NavTab.HISTORIAL },
                        icon = {
                            Icon(Icons.Filled.History, "Historial",
                                tint = if (tabActivo == NavTab.HISTORIAL) NavRed else NavGray,
                                modifier = Modifier.size(22.dp))
                        },
                        label = {
                            Text("Mi Actividad", fontSize = 11.sp,
                                fontWeight = if (tabActivo == NavTab.HISTORIAL) FontWeight.Bold else FontWeight.Normal,
                                color = if (tabActivo == NavTab.HISTORIAL) NavRed else NavGray)
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        selected = tabActivo == NavTab.PERFIL,
                        onClick = { showRutasSheet = false; tabActivo = NavTab.PERFIL },
                        icon = {
                            Icon(Icons.Filled.Person, "Perfil",
                                tint = if (tabActivo == NavTab.PERFIL) NavRed else NavGray,
                                modifier = Modifier.size(22.dp))
                        },
                        label = {
                            Text("Perfil", fontSize = 11.sp,
                                fontWeight = if (tabActivo == NavTab.PERFIL) FontWeight.Bold else FontWeight.Normal,
                                color = if (tabActivo == NavTab.PERFIL) NavRed else NavGray)
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            when (tabActivo) {

                NavTab.MAPA -> {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            when (modoSeleccion) {
                                "origen"  -> { origenLatLng  = latLng; modoSeleccion = null; mostrarBusqueda = true }
                                "destino" -> { destinoLatLng = latLng; modoSeleccion = null; mostrarBusqueda = true }
                                else -> { }
                            }
                        },
                        properties = MapProperties(
                            isMyLocationEnabled = locationPermission.status.isGranted,
                            mapType = mapType,
                            latLngBoundsForCameraTarget = com.google.android.gms.maps.model.LatLngBounds(
                                LatLng(-16.5500, -71.6500),
                                LatLng(-16.2500, -71.4000)
                            ),
                            mapStyleOptions = MapStyleOptions("""
                                [
                                  {"featureType":"poi","stylers":[{"visibility":"off"}]},
                                  {"featureType":"transit","stylers":[{"visibility":"off"}]},
                                  {"featureType":"road","elementType":"labels","stylers":[{"visibility":"on"}]}
                                ]
                            """)
                        )
                    ) {
                        origenLatLng?.let {
                            Marker(state = MarkerState(position = it), title = "Origen",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        }
                        destinoLatLng?.let {
                            Marker(state = MarkerState(position = it), title = "Destino",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        }

                        // ── Rutas visibles del drawer (IDA o VUELTA según toggle) ──────
                        rutas.filter { rutasVisibles.contains(it.id) }.forEach { ruta ->
                            val usarVuelta = (direccionPorRuta[ruta.id] == true) &&
                                    ruta.coordenadasVuelta.isNotEmpty()
                            val coordsUsadas = if (usarVuelta) ruta.coordenadasVuelta else ruta.coordenadas
                            val puntos = coordsUsadas.mapNotNull { coord ->
                                val lat = coord["lat"] ?: return@mapNotNull null
                                val lng = coord["lng"] ?: return@mapNotNull null
                                LatLng(lat, lng)
                            }
                            if (puntos.isNotEmpty())
                                Polyline(points = puntos, color = parseColor(ruta.color), width = 7f)
                        }

                        // ── Capa 1: rutas resaltadas por IA (contexto, tenue) ─────────────
                        rutas.filter { ruta ->
                            rutasResaltadas.any { it.equals(ruta.codigo, ignoreCase = true) }
                        }.forEach { ruta ->
                            // Usar el sentido correcto precalculado (o fallback a elegirDireccion si no está)
                            val coordsUsadas = coordsRecomendadas[ruta.codigo.trim()]
                                ?: (if (origenLatLng != null && destinoLatLng != null) {
                                    elegirDireccion(ruta, origenLatLng!!, destinoLatLng!!)
                                } else {
                                    ruta.coordenadas
                                })
                            val puntos = coordsUsadas.mapNotNull { coord ->
                                val lat = coord["lat"] ?: return@mapNotNull null
                                val lng = coord["lng"] ?: return@mapNotNull null
                                LatLng(lat, lng)
                            }
                            if (puntos.isNotEmpty())
                                Polyline(points = puntos,
                                    color = parseColor(ruta.color).copy(alpha = 0.35f), width = 10f)
                        }

                        // ── Capa 2: guía GPS (tramo exacto, sentido correcto) ─────────────
                        if (tramosGuia.isNotEmpty() && guiaActiva) {
                            tramosGuia.forEach { tramo ->
                                if (!tramo.esCaminata)
                                    Polyline(points = tramo.puntos, color = Color.White, width = 24f)
                            }
                            tramosGuia.forEach { tramo ->
                                if (!tramo.esCaminata) {
                                    Polyline(points = tramo.puntos, color = Color(0xFF1565C0), width = 16f)
                                    Polyline(points = tramo.puntos, color = Color(0xFF42A5F5), width = 7f)
                                } else {
                                    Polyline(
                                        points = tramo.puntos,
                                        color = Color.DarkGray,
                                        width = 8f,
                                        pattern = listOf(com.google.android.gms.maps.model.Dot(), com.google.android.gms.maps.model.Gap(15f))
                                    )
                                }
                            }
                        }

                        // ── Capa 3: marcadores de ruta y transbordo (badges y combis) ─────
                        if (guiaActiva) {
                            val iconCombi = remember(context) { crearBitmapCombiIcon(context) }
                            
                            marcadoresRuta.forEach { marcador ->
                                val iconBadge = remember(context, marcador.codigo, marcador.color) {
                                    crearBitmapCodigoRuta(context, marcador.codigo, marcador.color)
                                }
                                Marker(
                                    state = MarkerState(position = marcador.posicion),
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    icon = iconBadge,
                                    title = "Ruta ${marcador.codigo}"
                                )
                            }
                            
                            marcadoresTransbordo.forEach { pos ->
                                Marker(
                                    state = MarkerState(position = pos),
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    icon = iconCombi,
                                    title = "Transbordo"
                                )
                            }
                        }
                    }

                    // ── Banner modo selección ─────────────────────────────
                    if (modoSeleccion != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (modoSeleccion == "origen")
                                    Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (modoSeleccion == "origen")
                                        "📍 Toca el mapa para marcar el ORIGEN"
                                    else "🏁 Toca el mapa para marcar el DESTINO",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        modoSeleccion = null
                                        mostrarBusqueda = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Cancelar",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NavRed
                                    )
                                }
                            }
                        }
                    }

                    // ── Barra de búsqueda + botones flotantes ─────────────
                    if (modoSeleccion == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(6.dp, RoundedCornerShape(16.dp))
                                    .clickable {
                                        rutasResaltadas = emptySet()
                                        mostrarBusqueda = true
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Search, null, tint = NavGray, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("¿A dónde vas?", fontSize = 15.sp, color = NavGray)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Filled.Tune, null, tint = NavRed, modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            if (locationPermission.status.isGranted) {
                                                obtenerUbicacion(context) { location ->
                                                    scope.launch {
                                                        cameraPositionState.animate(
                                                            CameraUpdateFactory.newLatLngZoom(
                                                                LatLng(location.latitude, location.longitude), 16f
                                                            )
                                                        )
                                                    }
                                                }
                                            } else {
                                                locationPermission.launchPermissionRequest()
                                            }
                                        },
                                        containerColor = Color.White,
                                        contentColor = NavRed,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.MyLocation, "Mi ubicación", modifier = Modifier.size(20.dp))
                                    }

                                    var showMapTypeMenu by remember { mutableStateOf(false) }
                                    Box {
                                        SmallFloatingActionButton(
                                            onClick = { showMapTypeMenu = true },
                                            containerColor = Color.White,
                                            contentColor = NavRed,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Layers, "Tipo de mapa", modifier = Modifier.size(20.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showMapTypeMenu,
                                            onDismissRequest = { showMapTypeMenu = false }
                                        ) {
                                            DropdownMenuItem(text = { Text("🗺️  Estándar") },
                                                onClick = { mapType = MapType.NORMAL; showMapTypeMenu = false })
                                            DropdownMenuItem(text = { Text("🛰️  Satélite") },
                                                onClick = { mapType = MapType.SATELLITE; showMapTypeMenu = false })
                                            DropdownMenuItem(text = { Text("⛰️  Relieve") },
                                                onClick = { mapType = MapType.TERRAIN; showMapTypeMenu = false })
                                            DropdownMenuItem(text = { Text("🔀  Híbrido") },
                                                onClick = { mapType = MapType.HYBRID; showMapTypeMenu = false })
                                        }
                                    }

                                    if (tramosGuia.isNotEmpty()) {
                                        SmallFloatingActionButton(
                                            onClick = { guiaActiva = !guiaActiva },
                                            containerColor = if (guiaActiva) NavRed else Color.White,
                                            contentColor   = if (guiaActiva) Color.White else NavRed,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.LocationOn,
                                                contentDescription = if (guiaActiva) "Ocultar guía" else "Mostrar guía",
                                                modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Panel de búsqueda con animación ──────────────────
                    val rutaColores = remember(rutas) { rutas.associate { it.codigo.trim() to it.color } }
                    val rutaEtiquetas = remember(rutas) { rutas.associate { it.codigo.trim() to it.etiqueta.trim() } }
                    // Sentido elegido por elegirDireccion() para cada código recomendado
                    val rutaSentidos = remember(rutas, codigosPendientes, origenLatLng, destinoLatLng) {
                        val currentOrigen = origenLatLng
                        val currentDestino = destinoLatLng
                        if (currentOrigen == null || currentDestino == null || rutas.isEmpty() || codigosPendientes.isEmpty()) {
                            emptyMap<String, String>()
                        } else {
                            val result = mutableMapOf<String, String>()
                            val rutasRecomendadas = codigosPendientes.mapNotNull { codigo ->
                                rutas.firstOrNull { it.codigo.equals(codigo, ignoreCase = true) }
                            }
                            val n = rutasRecomendadas.size
                            if (n == 1) {
                                val r = rutasRecomendadas[0]
                                val coords = elegirDireccion(r, currentOrigen, currentDestino)
                                val esVuelta = r.coordenadasVuelta.isNotEmpty() && coords == r.coordenadasVuelta
                                result[r.codigo.trim()] = if (esVuelta) "VUELTA" else "IDA"
                            } else if (n >= 2) {
                                val coordsList = mutableListOf<List<Map<String, Double>>>()

                                // 1. Elegir dirección para la primera ruta
                                val coords0 = elegirDireccion(rutasRecomendadas[0], currentOrigen, currentDestino)
                                coordsList.add(coords0)
                                val esVuelta0 = rutasRecomendadas[0].coordenadasVuelta.isNotEmpty() && coords0 == rutasRecomendadas[0].coordenadasVuelta
                                result[rutasRecomendadas[0].codigo.trim()] = if (esVuelta0) "VUELTA" else "IDA"

                                // 2. Propagar para encontrar acercamientos de rutas intermedias
                                for (i in 1 until n) {
                                    val prevCoords = coordsList[i - 1]
                                    val rCurrent = rutasRecomendadas[i]

                                    val (transPrevIda, transCurrIda, minDistIda) = encontrarAcercamiento(prevCoords, rCurrent.coordenadas)
                                    val (transPrevVuelta, transCurrVuelta, minDistVuelta) = if (rCurrent.coordenadasVuelta.isNotEmpty()) {
                                        encontrarAcercamiento(prevCoords, rCurrent.coordenadasVuelta)
                                    } else {
                                        Triple(LatLng(0.0, 0.0), LatLng(0.0, 0.0), Double.MAX_VALUE)
                                    }

                                    val (transPrev, transCurr, coordsCurrent) = if (minDistVuelta < minDistIda) {
                                        Triple(transPrevVuelta, transCurrVuelta, rCurrent.coordenadasVuelta)
                                    } else {
                                        Triple(transPrevIda, transCurrIda, rCurrent.coordenadas)
                                    }

                                    coordsList.add(coordsCurrent)
                                    val esVueltaCurrent = rCurrent.coordenadasVuelta.isNotEmpty() && coordsCurrent == rCurrent.coordenadasVuelta
                                    result[rCurrent.codigo.trim()] = if (esVueltaCurrent) "VUELTA" else "IDA"
                                }
                            }
                            result
                        }
                    }
                    AnimatedVisibility(
                        visible = mostrarBusqueda,
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        enter = slideInVertically(
                            animationSpec = tween(durationMillis = 380),
                            initialOffsetY = { it }
                        ) + fadeIn(animationSpec = tween(durationMillis = 280)),
                        exit = slideOutVertically(
                            animationSpec = tween(durationMillis = 300),
                            targetOffsetY = { it }
                        ) + fadeOut(animationSpec = tween(durationMillis = 200))
                    ) {
                        BusquedaSheet(
                            origenLatLng        = origenLatLng,
                            destinoLatLng       = destinoLatLng,
                            onDismiss           = { mostrarBusqueda = false },
                            onSeleccionarOrigen = { mostrarBusqueda = false; modoSeleccion = "origen" },
                            onSeleccionarDestino= { mostrarBusqueda = false; modoSeleccion = "destino" },
                            onRutaEncontrada    = { codigos ->
                                rutasResaltadas   = codigos.toSet()
                                codigosPendientes = codigos
                                println("DEBUG onRutaEncontrada: codigos=$codigos rutas.size=${rutas.size}")

                                if (origenLatLng != null && destinoLatLng != null) {
                                    val tramos = mutableListOf<TramoGuia>()
                                    val rutasRecomendadas = codigos.mapNotNull { codigo ->
                                        rutas.firstOrNull { it.codigo.equals(codigo, ignoreCase = true) }
                                    }

                                    val n = rutasRecomendadas.size
                                    val nextMarcadoresRuta = mutableListOf<MarkerRutaGuia>()
                                    val nextMarcadoresTransbordo = mutableListOf<LatLng>()

                                    if (n == 1) {
                                        val ruta = rutasRecomendadas[0]
                                        val coords = elegirDireccion(ruta, origenLatLng!!, destinoLatLng!!)
                                        val segmento = calcularTramo(coords, origenLatLng!!, destinoLatLng!!)
                                        if (segmento.isNotEmpty()) {
                                            tramos.addAll(crearTramos(segmento, parseColor(ruta.color)))
                                            val posBadge = obtenerPuntoMasAdelante(segmento, startIndex = 1, metros = 10.0)
                                            nextMarcadoresRuta.add(MarkerRutaGuia(posicion = posBadge, codigo = ruta.codigo, color = ruta.color))
                                        }
                                    } else if (n >= 2) {
                                        val coordsList = mutableListOf<List<Map<String, Double>>>()
                                        val transferPoints = mutableListOf<Pair<LatLng, LatLng>>()

                                        // 1. Elegir dirección para la primera ruta
                                        val coords0 = elegirDireccion(rutasRecomendadas[0], origenLatLng!!, destinoLatLng!!)
                                        coordsList.add(coords0)

                                        // 2. Propagar para encontrar acercamientos de rutas intermedias
                                        for (i in 1 until n) {
                                            val prevCoords = coordsList[i - 1]
                                            val rCurrent = rutasRecomendadas[i]

                                            val (transPrevIda, transCurrIda, minDistIda) = encontrarAcercamiento(prevCoords, rCurrent.coordenadas)
                                            val (transPrevVuelta, transCurrVuelta, minDistVuelta) = if (rCurrent.coordenadasVuelta.isNotEmpty()) {
                                                encontrarAcercamiento(prevCoords, rCurrent.coordenadasVuelta)
                                            } else {
                                                Triple(LatLng(0.0, 0.0), LatLng(0.0, 0.0), Double.MAX_VALUE)
                                            }

                                            val (transPrev, transCurr, coordsCurrent) = if (minDistVuelta < minDistIda) {
                                                Triple(transPrevVuelta, transCurrVuelta, rCurrent.coordenadasVuelta)
                                            } else {
                                                Triple(transPrevIda, transCurrIda, rCurrent.coordenadas)
                                            }

                                            coordsList.add(coordsCurrent)
                                            transferPoints.add(Pair(transPrev, transCurr))
                                        }

                                        // 3. Construir tramos para cada una de las N rutas
                                        for (i in 0 until n) {
                                            val r = rutasRecomendadas[i]
                                            val coords = coordsList[i]
                                            val startPt = if (i == 0) origenLatLng!! else transferPoints[i - 1].second
                                            val endPt = if (i == n - 1) destinoLatLng!! else transferPoints[i].first

                                            val segmento = calcularTramo(coords, startPt, endPt)
                                            if (segmento.isNotEmpty()) {
                                                tramos.addAll(
                                                    crearTramos(
                                                        segmento = segmento,
                                                        colorBus = parseColor(r.color),
                                                        agregarInicio = (i == 0),
                                                        agregarFin = (i == n - 1)
                                                    )
                                                )
                                                val metrosDesplazamiento = if (i == 0) 10.0 else 150.0
                                                val posBadge = obtenerPuntoMasAdelante(segmento, startIndex = 1, metros = metrosDesplazamiento)
                                                nextMarcadoresRuta.add(MarkerRutaGuia(posicion = posBadge, codigo = r.codigo, color = r.color))
                                            }
                                        }

                                        for (i in 0 until n - 1) {
                                            nextMarcadoresTransbordo.add(transferPoints[i].first)
                                        }
                                    }
                                    tramosGuia = tramos
                                    marcadoresRuta = nextMarcadoresRuta
                                    marcadoresTransbordo = nextMarcadoresTransbordo
                                    guiaActiva = true
                                }
                            },
                            onNuevaOptimizacion = {
                                rutasResaltadas   = emptySet()
                                tramosGuia        = emptyList()
                                marcadoresRuta    = emptyList()
                                marcadoresTransbordo = emptyList()
                                codigosPendientes = emptyList()
                                guiaActiva        = true
                            },
                            rutaColores  = rutaColores,
                            rutaSentidos = rutaSentidos,
                            rutaEtiquetas = rutaEtiquetas
                        )
                    }

                    if (mostrarComentarios && rutaDetalle != null) {
                        ComentariosSheet(
                            rutaId     = rutaDetalle!!.id,
                            rutaNombre = rutaDetalle!!.nombre,
                            rutaCodigo = rutaDetalle!!.codigo,
                            onDismiss  = { mostrarComentarios = false }
                        )
                    }
                }

                NavTab.HISTORIAL -> {
                    HistorialScreen(
                        onBack = { tabActivo = NavTab.MAPA },
                        onRepetirBusqueda = { origen, destino, _ ->
                            origenLatLng    = origen.toLatLng()
                            destinoLatLng   = destino.toLatLng()
                            mostrarBusqueda = true
                            tabActivo       = NavTab.MAPA
                        },
                        externalSnackbarHostState = snackbarHostState
                    )
                }

                NavTab.PERFIL -> {
                    PerfilScreen(
                        onBack   = { tabActivo = NavTab.MAPA },
                        onLogout = onLogout
                    )
                }
            }

            if (showRutasSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showRutasSheet = false },
                    sheetState       = rutasSheetState,
                    scrimColor       = Color.Transparent,
                    containerColor   = Color.White,
                    shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    dragHandle = {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.width(40.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE0E0E0)))
                        }
                    }
                ) {
                    RutasSheetContent(
                        rutas        = rutas,
                        rutasVisibles = rutasVisibles,
                        onToggleRuta = { id ->
                            rutasVisibles = if (rutasVisibles.contains(id))
                                rutasVisibles - id else rutasVisibles + id
                        },
                        onDetalleRuta = { ruta ->
                            rutaDetalle        = ruta
                            rutasVisibles      = rutasVisibles + ruta.id
                            scope.launch { rutasSheetState.hide() }.invokeOnCompletion {
                                showRutasSheet = false
                            }
                        }
                    )
                }
            }

            if (rutaDetalle != null) {
                RutaDetalleSheet(
                    ruta               = rutaDetalle!!,
                    mostrarVuelta      = direccionPorRuta[rutaDetalle!!.id] == true,
                    onCambiarDireccion = { vuelta ->
                        direccionPorRuta = direccionPorRuta + (rutaDetalle!!.id to vuelta)
                    },
                    onDismiss          = { rutaDetalle = null },
                    onBack             = { rutaDetalle = null; showRutasSheet = true },
                    onVerOpiniones     = { mostrarComentarios = true }
                )
            }
        }
    }
}

// ── NavIcon ──────────────────────────────────────────────────────────────────
@Composable
private fun NavIcon(
    icon: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
    active: Boolean
) {
    Box(
        modifier = if (active)
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(NavRed)
        else Modifier,
        contentAlignment = Alignment.Center
    ) { icon(22.dp) }
}

// ── RutasSheetContent ────────────────────────────────────────────────────────
@Composable
fun RutasSheetContent(
    rutas: List<RutaMapa>,
    rutasVisibles: Set<String>,
    onToggleRuta: (String) -> Unit,
    onDetalleRuta: (RutaMapa) -> Unit
) {
    // 1. Extraer cuencas disponibles y agrupar rutas de forma dinámica
    val cuencasMap = remember(rutas) {
        val regex = Regex("""C-\d+""", RegexOption.IGNORE_CASE)
        fun obtenerCuenca(r: RutaMapa): String {
            val matchNombre = regex.find(r.nombre)?.value
            if (matchNombre != null) return matchNombre.uppercase()
            val matchEtiqueta = regex.find(r.etiqueta)?.value
            if (matchEtiqueta != null) return matchEtiqueta.uppercase()
            val matchCodigo = regex.find(r.codigo)?.value
            if (matchCodigo != null) return matchCodigo.uppercase()
            return "OTROS"
        }

        rutas.groupBy { obtenerCuenca(it) }
    }

    // 2. Ordenar las cuencas numéricamente: C-1, C-2, ..., C-10, y OTROS al final
    val cuencasOrdenadas = remember(cuencasMap) {
        cuencasMap.keys.sortedWith(Comparator { a, b ->
            val numA = a.substringAfter("-").toIntOrNull()
            val numB = b.substringAfter("-").toIntOrNull()
            when {
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1
                numB != null -> 1
                else -> a.compareTo(b)
            }
        })
    }

    // 3. Estado de la cuenca seleccionada (por defecto la primera cuenca o vacía si no hay)
    var cuencaSeleccionada by remember(cuencasOrdenadas) {
        mutableStateOf(cuencasOrdenadas.firstOrNull() ?: "")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Rutas oficiales MPA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavSecondary)
                Text("Toca una ruta para verla en el mapa", fontSize = 13.sp, color = NavGray)
            }
            Icon(Icons.Filled.Tune, "Filtrar", tint = NavGray, modifier = Modifier.size(22.dp))
        }
        HorizontalDivider(color = Color(0xFFF0F0F0))
        if (rutas.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavRed)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
            ) {
                // ── Columna Izquierda: Menú de Cuencas (Ancho fijo, scrollable) ──
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFF9F9F9))
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    cuencasOrdenadas.forEach { cuenca ->
                        val esSeleccionada = cuenca == cuencaSeleccionada
                        val numRutas = cuencasMap[cuenca]?.size ?: 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cuencaSeleccionada = cuenca }
                                .background(if (esSeleccionada) Color.White else Color.Transparent)
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (esSeleccionada) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(4.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(NavRed)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = cuenca,
                                    fontSize = 15.sp,
                                    fontWeight = if (esSeleccionada) FontWeight.Bold else FontWeight.Medium,
                                    color = if (esSeleccionada) NavRed else NavSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (numRutas == 1) "1 ruta" else "$numRutas rutas",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (esSeleccionada) NavRed.copy(alpha = 0.7f) else NavGray
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.fillMaxWidth())
                    }
                }

                // Separador vertical fino
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFE0E0E0))
                )

                // ── Columna Derecha: Listado de Rutas de la Cuenca Seleccionada ──
                val rutasDeCuenca = cuencasMap[cuencaSeleccionada] ?: emptyList()
                if (rutasDeCuenca.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay rutas en esta cuenca", fontSize = 13.sp, color = NavGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(rutasDeCuenca) { ruta ->
                            RutaListItem(
                                ruta = ruta,
                                isVisible = rutasVisibles.contains(ruta.id),
                                onToggle = { onToggleRuta(ruta.id) },
                                onDetalle = { onDetalleRuta(ruta) },
                                hideCuenca = true
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Color(0xFFF5F5F5)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── RutaListItem ─────────────────────────────────────────────────────────────
@Composable
fun RutaListItem(
    ruta: RutaMapa,
    isVisible: Boolean,
    onToggle: () -> Unit,
    onDetalle: () -> Unit = {},
    hideCuenca: Boolean = false
) {
    val bgColor = try { Color(android.graphics.Color.parseColor(ruta.color)) } catch (e: Exception) { NavRed }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(64.dp).height(46.dp).clip(RoundedCornerShape(12.dp))
                .background(bgColor.copy(alpha = if (isVisible) 1f else 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(ruta.codigo, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Clip)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            val titleText = if (hideCuenca) {
                if (ruta.etiqueta.isNotEmpty()) "\"${ruta.etiqueta}\"" else ruta.codigo
            } else {
                if (ruta.etiqueta.isNotEmpty()) {
                    "\"${ruta.etiqueta}\" - ${ruta.nombre.ifEmpty { "Sin Nombre" }}"
                } else {
                    ruta.nombre.ifEmpty { ruta.codigo }
                }
            }
            Text(titleText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (isVisible) NavRed else NavSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitulo = buildString {
                if (ruta.empresa.isNotEmpty()) append(ruta.empresa)
                if (ruta.frecuencia.isNotEmpty()) { if (isNotEmpty()) append(" • "); append(ruta.frecuencia) }
            }
            if (subtitulo.isNotEmpty())
                Text(subtitulo, fontSize = 12.sp, color = NavGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Ver detalle",
            tint = if (isVisible) NavRed else NavGray,
            modifier = Modifier.size(20.dp).clickable { onDetalle() })
    }
}

// ── RutaDetalleSheet — muestra IDA y VUELTA en el tab RECORRIDO ──────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutaDetalleSheet(
    ruta: RutaMapa,
    mostrarVuelta: Boolean = false,
    onCambiarDireccion: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onBack: () -> Unit = onDismiss,
    onVerOpiniones: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val comentarioViewModel: ComentarioViewModel = viewModel()
    val comentarios by comentarioViewModel.comentarios.collectAsState()
    val comentarioState by comentarioViewModel.comentarioState.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    var textoComentario by remember { mutableStateOf("") }

    LaunchedEffect(ruta.id) { comentarioViewModel.cargarComentarios(ruta.id) }
    LaunchedEffect(comentarioState) {
        if (comentarioState is ComentarioState.Success) {
            textoComentario = ""
            comentarioViewModel.resetState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        scrimColor       = Color.Transparent,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFDDDDDD)))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // ── Cabecera ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver",
                        tint = NavGray, modifier = Modifier.size(20.dp))
                }
                val badgeColor = try { Color(android.graphics.Color.parseColor(ruta.color)) }
                catch (e: Exception) { NavRed }
                Box(modifier = Modifier.width(52.dp).height(42.dp)
                    .clip(RoundedCornerShape(10.dp)).background(badgeColor),
                    contentAlignment = Alignment.Center) {
                    Text(ruta.codigo, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Clip)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val detailTitle = if (ruta.etiqueta.isNotEmpty()) {
                        "\"${ruta.etiqueta}\" - ${ruta.nombre}".trim()
                    } else {
                        ruta.nombre.trim().ifEmpty { ruta.codigo }
                    }
                    Text(detailTitle, fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    if (ruta.empresa.isNotEmpty())
                        Text("Emp: ${ruta.empresa}", fontSize = 12.sp, color = NavGray)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Tabs ─────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                listOf("RECORRIDO", "OPINIONES").forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Column(
                        modifier = Modifier.weight(1f).clickable { selectedTab = index },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NavRed else NavGray,
                            modifier = Modifier.padding(vertical = 10.dp), letterSpacing = 0.5.sp)
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp)
                            .background(if (isSelected) NavRed else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {

                // ── Tab RECORRIDO: toggle IDA/VUELTA + texto ──────────────
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Toggle IDA / VUELTA — solo si la ruta tiene vuelta
                        if (ruta.coordenadasVuelta.isNotEmpty() || ruta.avenidaVuelta.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF5F5F5)),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                listOf(false to "➡ IDA", true to "⬅ VUELTA")
                                    .forEach { (esVuelta, label) ->
                                        val seleccionado = mostrarVuelta == esVuelta
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (seleccionado) NavRed
                                                    else Color.Transparent
                                                )
                                                .clickable { onCambiarDireccion(esVuelta) }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (seleccionado) FontWeight.Bold
                                                else FontWeight.Normal,
                                                color = if (seleccionado) Color.White else NavGray
                                            )
                                        }
                                    }
                            }
                        }

                        // Texto del recorrido según selección
                        val textoRecorrido = if (mostrarVuelta && ruta.avenidaVuelta.isNotEmpty())
                            ruta.avenidaVuelta else ruta.avenidas
                        val colorLabel = if (mostrarVuelta) Color(0xFF1565C0) else NavRed
                        val labelDir   = if (mostrarVuelta) "⬅ VUELTA" else "➡ IDA"

                        if (textoRecorrido.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (mostrarVuelta)
                                        Color(0xFFF0F4FF) else Color(0xFFFFF5F5)
                                ),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        labelDir, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorLabel, letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        textoRecorrido, fontSize = 12.sp,
                                        color = Color(0xFF424242), lineHeight = 18.sp
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Sin información de recorrido",
                                    fontSize = 13.sp, color = NavGray
                                )
                            }
                        }
                    }
                }

                // ── Tab OPINIONES ─────────────────────────────────────────
                1 -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (comentarios.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center) {
                                Text("No hay opiniones aún. ¡Sé el primero!", fontSize = 13.sp, color = NavGray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(comentarios) { comentario ->
                                    val fecha = comentario.fecha?.toDate()?.let { ts ->
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(ts)
                                    } ?: ""
                                    Card(modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (comentario.destacado)
                                                Color(0xFFFFEBEE) else Color(0xFFF5F5F5)),
                                        elevation = CardDefaults.cardElevation(0.dp)) {
                                        Column(modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (comentario.destacado)
                                                Text("📌 Destacado", fontSize = 10.sp,
                                                    color = NavRed, fontWeight = FontWeight.Bold)
                                            Text(comentario.texto, fontSize = 13.sp, color = Color(0xFF212121))
                                            Text("${comentario.nombreUsuario} · $fecha",
                                                fontSize = 11.sp, color = NavGray)
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        if (user != null) {
                            Text("Deja tu opinión:", fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF212121))
                            OutlinedTextField(
                                value = textoComentario,
                                onValueChange = { textoComentario = it },
                                placeholder = { Text("Comparte tu experiencia con esta ruta...", fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NavRed,
                                    focusedLabelColor  = NavRed,
                                    cursorColor        = NavRed)
                            )
                            when (comentarioState) {
                                is ComentarioState.Error ->
                                    Text((comentarioState as ComentarioState.Error).mensaje,
                                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                is ComentarioState.Success ->
                                    Text("✅ Comentario enviado.", color = NavRed, fontSize = 12.sp)
                                else -> {}
                            }
                            Button(
                                onClick = {
                                    comentarioViewModel.publicarComentario(
                                        rutaId = ruta.id, rutaNombre = ruta.nombre,
                                        rutaCodigo = ruta.codigo, texto = textoComentario)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = textoComentario.isNotEmpty() && comentarioState !is ComentarioState.Loading,
                                colors = ButtonDefaults.buttonColors(containerColor = NavRed)
                            ) {
                                if (comentarioState is ComentarioState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Filled.Forum, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Publicar opinión", fontSize = 13.sp)
                                }
                            }
                        } else {
                            Text("Inicia sesión para dejar un comentario", fontSize = 13.sp, color = NavGray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

fun distMetrosAprox(a: com.google.android.gms.maps.model.LatLng, b: com.google.android.gms.maps.model.LatLng): Double {
    val latMid = (a.latitude + b.latitude) / 2.0 * 0.017453292519943295
    val dLat = (b.latitude - a.latitude) * 111132.954
    val dLng = (b.longitude - a.longitude) * 111132.954 * Math.cos(latMid)
    return Math.sqrt(dLat * dLat + dLng * dLng)
}

fun obtenerPuntoMasAdelante(
    segmento: List<com.google.android.gms.maps.model.LatLng>,
    startIndex: Int,
    metros: Double
): com.google.android.gms.maps.model.LatLng {
    if (segmento.isEmpty()) return com.google.android.gms.maps.model.LatLng(0.0, 0.0)
    if (startIndex >= segmento.lastIndex) return segmento.last()
    
    // Evitar que el marcador pase del penúltimo punto (fin del recorrido en combi)
    val maxIndex = (segmento.size - 2).coerceAtLeast(startIndex)
    
    var currentPt = segmento[startIndex]
    var accumulatedDist = 0.0
    
    for (i in startIndex + 1..maxIndex) {
        val nextPt = segmento[i]
        val d = distMetrosAprox(currentPt, nextPt)
        accumulatedDist += d
        currentPt = nextPt
        if (accumulatedDist >= metros) {
            return nextPt
        }
    }
    return segmento[maxIndex]
}

fun crearBitmapCodigoRuta(
    context: android.content.Context,
    codigo: String,
    colorHex: String
): com.google.android.gms.maps.model.BitmapDescriptor {
    val scale = context.resources.displayMetrics.density
    val paddingX = (8 * scale).toInt()
    val paddingY = (4 * scale).toInt()
    val fontSize = 12 * scale
    
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = fontSize
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    val bounds = android.graphics.Rect()
    textPaint.getTextBounds(codigo, 0, codigo.length, bounds)
    val textWidth = bounds.width()
    val textHeight = bounds.height()
    
    val pillHeight = (textHeight + paddingY * 2)
    val pillWidth = (textWidth + paddingX * 2).coerceAtLeast(pillHeight)
    
    val bitmap = android.graphics.Bitmap.createBitmap(pillWidth, pillHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = try {
            android.graphics.Color.parseColor(colorHex)
        } catch (e: Exception) {
            android.graphics.Color.RED
        }
        style = android.graphics.Paint.Style.FILL
    }
    
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f * scale
    }
    
    val rect = android.graphics.RectF(0f, 0f, pillWidth.toFloat(), pillHeight.toFloat())
    val radius = pillHeight.toFloat() / 2f
    
    canvas.drawRoundRect(rect, radius, radius, bgPaint)
    canvas.drawRoundRect(rect, radius, radius, strokePaint)
    
    val x = pillWidth / 2f
    val y = (pillHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(codigo, x, y, textPaint)
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun crearBitmapCombiIcon(context: android.content.Context): com.google.android.gms.maps.model.BitmapDescriptor {
    val scale = context.resources.displayMetrics.density
    val size = (32 * scale).toInt()
    
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C62828")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2 * scale
    }
    
    val cx = size / 2f
    val cy = size / 2f
    val radius = (size / 2f) - (1 * scale)
    canvas.drawCircle(cx, cy, radius, bgPaint)
    canvas.drawCircle(cx, cy, radius, borderPaint)
    
    val combiW = 16 * scale
    val combiH = 14 * scale
    val combiLeft = cx - (combiW / 2)
    val combiTop = cy - (combiH / 2) - (1 * scale)
    
    val wheelW = 3 * scale
    val wheelH = 2 * scale
    val wheelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(
        combiLeft + 2 * scale,
        combiTop + combiH,
        combiLeft + 2 * scale + wheelW,
        combiTop + combiH + wheelH,
        wheelPaint
    )
    canvas.drawRect(
        combiLeft + combiW - 2 * scale - wheelW,
        combiTop + combiH,
        combiLeft + combiW - 2 * scale,
        combiTop + combiH + wheelH,
        wheelPaint
    )
    
    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C62828")
        style = android.graphics.Paint.Style.FILL
    }
    val bodyRect = android.graphics.RectF(combiLeft, combiTop, combiLeft + combiW, combiTop + combiH)
    canvas.drawRoundRect(bodyRect, 3 * scale, 3 * scale, bodyPaint)
    
    val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E0F7FA")
        style = android.graphics.Paint.Style.FILL
    }
    val glassRect = android.graphics.RectF(
        combiLeft + 1.5f * scale,
        combiTop + 1.5f * scale,
        combiLeft + combiW - 1.5f * scale,
        combiTop + combiH / 2f
    )
    canvas.drawRoundRect(glassRect, 1.5f * scale, 1.5f * scale, glassPaint)
    
    val lightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFEB3B")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(combiLeft + 3f * scale, combiTop + combiH - 4f * scale, 1.5f * scale, lightPaint)
    canvas.drawCircle(combiLeft + combiW - 3f * scale, combiTop + combiH - 4f * scale, 1.5f * scale, lightPaint)
    
    val grillePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#B0BEC5")
        style = android.graphics.Paint.Style.FILL
    }
    val grilleRect = android.graphics.RectF(
        combiLeft + 5f * scale,
        combiTop + combiH - 4.5f * scale,
        combiLeft + combiW - 5f * scale,
        combiTop + combiH - 3.5f * scale
    )
    canvas.drawRect(grilleRect, grillePaint)
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}