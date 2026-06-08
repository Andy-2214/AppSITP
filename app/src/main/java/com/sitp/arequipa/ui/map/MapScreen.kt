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

// ── Selecciona coordenadas IDA o VUELTA según la dirección del viaje ──────────
// Devuelve el listado de coordenadas cuyo primer punto esté más cerca del origen.
// Si la ruta solo tiene coordenadas de IDA (vuelta vacía), devuelve las de IDA.
fun elegirDireccion(
    ruta: RutaMapa,
    origenLatLng: LatLng,
    destinoLatLng: LatLng
): List<Map<String, Double>> {
    if (ruta.coordenadasVuelta.isEmpty()) return ruta.coordenadas  // sin vuelta → IDA siempre

    fun distSq(a: LatLng, b: LatLng): Double {
        val dl = a.latitude  - b.latitude
        val dn = a.longitude - b.longitude
        return dl * dl + dn * dn
    }

    // Primer y último punto de IDA
    val idaPrimero  = ruta.coordenadas.firstOrNull()
        ?.let { LatLng(it["lat"] ?: 0.0, it["lng"] ?: 0.0) }
    val idaUltimo   = ruta.coordenadas.lastOrNull()
        ?.let { LatLng(it["lat"] ?: 0.0, it["lng"] ?: 0.0) }

    // Primer y último punto de VUELTA
    val vueltaPrimero = ruta.coordenadasVuelta.firstOrNull()
        ?.let { LatLng(it["lat"] ?: 0.0, it["lng"] ?: 0.0) }
    val vueltaUltimo  = ruta.coordenadasVuelta.lastOrNull()
        ?.let { LatLng(it["lat"] ?: 0.0, it["lng"] ?: 0.0) }

    if (idaPrimero == null || vueltaPrimero == null) return ruta.coordenadas

    // Distancia del origen al inicio de IDA vs inicio de VUELTA
    val distIdaOrigen    = distSq(idaPrimero,    origenLatLng)
    val distVueltaOrigen = distSq(vueltaPrimero, origenLatLng)

    // También comparar fin de cada sentido con el destino para desempatar
    val distIdaDestino    = idaUltimo?.let    { distSq(it, destinoLatLng) } ?: Double.MAX_VALUE
    val distVueltaDestino = vueltaUltimo?.let { distSq(it, destinoLatLng) } ?: Double.MAX_VALUE

    // Preferir el sentido cuyo inicio esté más cerca del origen
    // y cuyo fin esté más cerca del destino
    val scoreIda    = distIdaOrigen    + distIdaDestino
    val scoreVuelta = distVueltaOrigen + distVueltaDestino

    return if (scoreVuelta < scoreIda) ruta.coordenadasVuelta else ruta.coordenadas
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
        if (rutas.isNotEmpty() && codigosPendientes.isNotEmpty() &&
            origenLatLng != null && destinoLatLng != null) {

            println("DEBUG recalculando tramos con ${rutas.size} rutas y codigos $codigosPendientes")
            val rutasRecomendadas = rutas.filter { ruta ->
                codigosPendientes.any { it.equals(ruta.codigo, ignoreCase = true) }
            }
            println("DEBUG rutasRecomendadas: ${rutasRecomendadas.map { it.codigo }}")

            val tramos = mutableListOf<TramoGuia>()
            when {
                rutasRecomendadas.size == 1 -> {
                    val ruta  = rutasRecomendadas[0]
                    // ← usa el sentido correcto (IDA o VUELTA)
                    val coords = elegirDireccion(ruta, origenLatLng!!, destinoLatLng!!)
                    val segmento = calcularTramo(coords, origenLatLng!!, destinoLatLng!!)
                    println("DEBUG segmento size: ${segmento.size}")
                    if (segmento.isNotEmpty())
                        tramos.add(TramoGuia(segmento, parseColor(ruta.color), false))
                }
                rutasRecomendadas.size >= 2 -> {
                    val ruta1  = rutasRecomendadas[0]
                    val ruta2  = rutasRecomendadas[1]
                    val coords1 = elegirDireccion(ruta1, origenLatLng!!, destinoLatLng!!)
                    val coords2 = elegirDireccion(ruta2, origenLatLng!!, destinoLatLng!!)
                    val seg1 = calcularTramo(coords1, origenLatLng!!, destinoLatLng!!)
                    val transbordo = seg1.lastOrNull() ?: return@LaunchedEffect
                    val seg2 = calcularTramo(coords2, transbordo, destinoLatLng!!)
                    if (seg1.isNotEmpty()) tramos.add(TramoGuia(seg1, parseColor(ruta1.color), false))
                    if (seg2.isNotEmpty()) tramos.add(TramoGuia(seg2, parseColor(ruta2.color), false))
                }
            }
            tramosGuia = tramos
            guiaActiva = true
        }
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
                            // Mostrar IDA tenue como contexto del recorrido completo
                            val puntos = ruta.coordenadas.mapNotNull { coord ->
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
                                }
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
                    val rutaColores = remember(rutas) { rutas.associate { it.codigo to it.color } }
                    // Sentido elegido por elegirDireccion() para cada código recomendado
                    val rutaSentidos = remember(rutas, codigosPendientes, origenLatLng, destinoLatLng) {
                        if (origenLatLng == null || destinoLatLng == null) emptyMap<String, String>()
                        else codigosPendientes.mapNotNull { codigo ->
                            val ruta = rutas.firstOrNull {
                                it.codigo.equals(codigo, ignoreCase = true)
                            } ?: return@mapNotNull null
                            val usaVuelta = ruta.coordenadasVuelta.isNotEmpty() && run {
                                val primeroIda    = ruta.coordenadas.firstOrNull()       ?: return@run false
                                val ultimoIda     = ruta.coordenadas.lastOrNull()        ?: return@run false
                                val primeroVuelta = ruta.coordenadasVuelta.firstOrNull() ?: return@run false
                                val ultimoVuelta  = ruta.coordenadasVuelta.lastOrNull()  ?: return@run false
                                fun distSq(p: Map<String,Double>, lat: Double, lng: Double): Double {
                                    val dl = (p["lat"] ?: 0.0) - lat
                                    val dn = (p["lng"] ?: 0.0) - lng
                                    return dl*dl + dn*dn
                                }
                                val oLat = origenLatLng!!.latitude;  val oLng = origenLatLng!!.longitude
                                val dLat = destinoLatLng!!.latitude; val dLng = destinoLatLng!!.longitude
                                val scoreIda    = distSq(primeroIda,    oLat, oLng) + distSq(ultimoIda,    dLat, dLng)
                                val scoreVuelta = distSq(primeroVuelta, oLat, oLng) + distSq(ultimoVuelta, dLat, dLng)
                                scoreVuelta < scoreIda
                            }
                            codigo to if (usaVuelta) "VUELTA" else "IDA"
                        }.toMap()
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
                                    val rutasRecomendadas = rutas.filter { ruta ->
                                        codigos.any { it.equals(ruta.codigo, ignoreCase = true) }
                                    }

                                    fun distSqLL(a: LatLng, b: LatLng): Double {
                                        val dlat = a.latitude  - b.latitude
                                        val dlng = a.longitude - b.longitude
                                        return dlat * dlat + dlng * dlng
                                    }

                                    fun tramoCasiIdentico(r1: RutaMapa, r2: RutaMapa): Boolean {
                                        val pts1 = r1.coordenadas.mapNotNull { c ->
                                            val la = c["lat"] ?: return@mapNotNull null
                                            val ln = c["lng"] ?: return@mapNotNull null
                                            LatLng(la, ln)
                                        }
                                        val pts2 = r2.coordenadas.mapNotNull { c ->
                                            val la = c["lat"] ?: return@mapNotNull null
                                            val ln = c["lng"] ?: return@mapNotNull null
                                            LatLng(la, ln)
                                        }
                                        if (pts1.isEmpty() || pts2.isEmpty()) return false
                                        val TOL = 0.003 * 0.003
                                        val mismoInicio = distSqLL(pts1.first(), pts2.first()) < TOL ||
                                                distSqLL(pts1.first(), pts2.last())  < TOL
                                        val mismoFin    = distSqLL(pts1.last(),  pts2.first()) < TOL ||
                                                distSqLL(pts1.last(),  pts2.last())  < TOL
                                        return mismoInicio && mismoFin
                                    }

                                    val rutasUnicas = rutasRecomendadas.fold(mutableListOf<RutaMapa>()) { acc, r ->
                                        if (acc.none { tramoCasiIdentico(it, r) }) acc.add(r)
                                        acc
                                    }

                                    when {
                                        rutasUnicas.size == 1 -> {
                                            val ruta   = rutasUnicas[0]
                                            // ← elegir IDA o VUELTA según dirección del viaje
                                            val coords = elegirDireccion(ruta, origenLatLng!!, destinoLatLng!!)
                                            val segmento = calcularTramo(coords, origenLatLng!!, destinoLatLng!!)
                                            if (segmento.isNotEmpty())
                                                tramos.add(TramoGuia(segmento, parseColor(ruta.color), false))
                                        }
                                        rutasUnicas.size >= 2 -> {
                                            val ruta1   = rutasUnicas[0]
                                            val ruta2   = rutasUnicas[1]
                                            val coords1 = elegirDireccion(ruta1, origenLatLng!!, destinoLatLng!!)
                                            val coords2 = elegirDireccion(ruta2, origenLatLng!!, destinoLatLng!!)
                                            val segmento1 = calcularTramo(coords1, origenLatLng!!, destinoLatLng!!)
                                            val transbordo = if (segmento1.isNotEmpty()) segmento1.last() else {
                                                val pts1 = ruta1.coordenadas.mapNotNull { c ->
                                                    val la = c["lat"] ?: return@mapNotNull null
                                                    val ln = c["lng"] ?: return@mapNotNull null
                                                    LatLng(la, ln)
                                                }
                                                pts1.minByOrNull { distSqLL(it, destinoLatLng!!) }
                                                    ?: return@BusquedaSheet
                                            }
                                            val segmento2 = calcularTramo(coords2, transbordo, destinoLatLng!!)
                                            if (segmento1.isNotEmpty())
                                                tramos.add(TramoGuia(segmento1, parseColor(ruta1.color), false))
                                            if (segmento2.isNotEmpty())
                                                tramos.add(TramoGuia(segmento2, parseColor(ruta2.color), false))
                                        }
                                    }
                                    tramosGuia = tramos
                                    guiaActiva = true
                                }
                            },
                            onNuevaOptimizacion = {
                                rutasResaltadas   = emptySet()
                                tramosGuia        = emptyList()
                                codigosPendientes = emptyList()
                                guiaActiva        = true
                            },
                            rutaColores  = rutaColores,
                            rutaSentidos = rutaSentidos
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.75f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(rutas) { ruta ->
                    RutaListItem(ruta = ruta, isVisible = rutasVisibles.contains(ruta.id),
                        onToggle = { onToggleRuta(ruta.id) }, onDetalle = { onDetalleRuta(ruta) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = Color(0xFFF5F5F5))
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
    onDetalle: () -> Unit = {}
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
            Text(ruta.nombre.ifEmpty { ruta.codigo }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
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
                    Text("${ruta.codigo} ${ruta.nombre}".trim(), fontSize = 17.sp,
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