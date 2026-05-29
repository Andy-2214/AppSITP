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

// ── Modelos ──────────────────────────────────────────────────────────────────
data class RutaMapa(
    val id: String = "",
    val nombre: String = "",
    val codigo: String = "",
    val empresa: String = "",
    val color: String = "#FF0000",
    val avenidas: String = "",
    val frecuencia: String = "",
    val coordenadas: List<Map<String, Double>> = emptyList()
)

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

// ── Tabs del nav inferior (sin RUTAS — va como sheet) ───────────────────────
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
    var modoSeleccion by remember { mutableStateOf<String?>(null) }
    var rutaSeleccionada by remember { mutableStateOf<RutaMapa?>(null) }
    var rutaDetalle      by remember { mutableStateOf<RutaMapa?>(null) }
    var mostrarComentarios by remember { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

     var origenLatLng by remember { mutableStateOf(origenInicial?.toLatLng()) }
    var destinoLatLng by remember { mutableStateOf(destinoInicial?.toLatLng()) }
    var mostrarBusqueda by remember {
        mutableStateOf(origenInicial != null && destinoInicial != null)
    }

    // ── Estado del nav / sheet ────────────────────────────────────────────
    var tabActivo by remember { mutableStateOf(NavTab.MAPA) }
    var showRutasSheet by remember { mutableStateOf(false) }
    val rutasSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // snackbar compartido (usado por HistorialScreen)
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Cargar rutas ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("rutas").get()
            .addOnSuccessListener { snapshot ->
                rutas = snapshot.documents.map { doc ->
                    RutaMapa(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        codigo = doc.getString("codigo") ?: "",
                        empresa = doc.getString("empresa") ?: "Sin empresa",
                        color = doc.getString("color") ?: "#FF0000",
                        avenidas = doc.getString("avenidas") ?: "",
                        frecuencia = doc.getString("frecuencia") ?: "",
                        coordenadas = doc.get("coordenadas") as? List<Map<String, Double>> ?: emptyList()
                    )
                }
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
        // ── TopBar adaptable por tab ──────────────────────────────────────
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
                    when (tabActivo) {
                        NavTab.MAPA -> {
                            // Logo + título
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NavRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.DirectionsBus,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SITP Arequipa",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavRed
                                )
                            }
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                        NavTab.HISTORIAL -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NavRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.DirectionsBus,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SITP Arequipa",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavRed
                                )
                            }
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                        NavTab.PERFIL -> {
                            // El perfil tiene su propio botón de cerrar sesión
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NavRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.DirectionsBus,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SITP Arequipa",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavRed
                                )
                            }
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        },
        // ── Bottom Navigation ─────────────────────────────────────────────
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    // Mapa
                    NavigationBarItem(
                        selected = tabActivo == NavTab.MAPA && !showRutasSheet,
                        onClick = {
                            showRutasSheet = false
                            tabActivo = NavTab.MAPA
                        },
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
                    // Rutas — abre/cierra el sheet
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
                    // Historial
                    NavigationBarItem(
                        selected = tabActivo == NavTab.HISTORIAL,
                        onClick = {
                            showRutasSheet = false
                            tabActivo = NavTab.HISTORIAL
                        },
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
                    // Perfil
                    NavigationBarItem(
                        selected = tabActivo == NavTab.PERFIL,
                        onClick = {
                            showRutasSheet = false
                            tabActivo = NavTab.PERFIL
                        },
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
                // ── Tab MAPA ──────────────────────────────────────────────
                NavTab.MAPA -> {
                    // ── Google Map ────────────────────────────────────────
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            when (modoSeleccion) {
                                "origen" -> {
                                    origenLatLng = latLng
                                    modoSeleccion = null
                                    mostrarBusqueda = true
                                }
                                "destino" -> {
                                    destinoLatLng = latLng
                                    modoSeleccion = null
                                    mostrarBusqueda = true
                                }
                                else -> {
                                    // No cerrar la búsqueda al tocar el mapa
                                }
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
                        rutas.filter { rutasVisibles.contains(it.id) }.forEach { ruta ->
                            val puntos = ruta.coordenadas.mapNotNull { coord ->
                                val lat = coord["lat"] ?: return@mapNotNull null
                                val lng = coord["lng"] ?: return@mapNotNull null
                                LatLng(lat, lng)
                            }
                            if (puntos.isNotEmpty()) Polyline(points = puntos, color = parseColor(ruta.color), width = 7f)
                        }
                        rutas.filter { ruta ->
                            rutasResaltadas.any { it.equals(ruta.codigo, ignoreCase = true) }
                        }.forEach { ruta ->
                            val puntos = ruta.coordenadas.mapNotNull { coord ->
                                val lat = coord["lat"] ?: return@mapNotNull null
                                val lng = coord["lng"] ?: return@mapNotNull null
                                LatLng(lat, lng)
                            }
                            if (puntos.isNotEmpty()) {
                                Polyline(points = puntos, color = Color.White, width = 20f)
                                Polyline(points = puntos, color = parseColor(ruta.color), width = 14f)
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
                                containerColor = if (modoSeleccion == "origen") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (modoSeleccion == "origen")
                                    "📍 Toca el mapa para marcar el ORIGEN"
                                else "🏁 Toca el mapa para marcar el DESTINO",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // ── Barra de búsqueda + botones de mapa (columna derecha) ────
                    if (modoSeleccion == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Barra de búsqueda
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

                            // Botones flotantes alineados a la derecha
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Botón: ir a mi ubicación
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

                                    // Botón: cambiar tipo de mapa
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
                                            DropdownMenuItem(
                                                text = { Text("🗺️  Estándar") },
                                                onClick = { mapType = MapType.NORMAL; showMapTypeMenu = false }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🛰️  Satélite") },
                                                onClick = { mapType = MapType.SATELLITE; showMapTypeMenu = false }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("⛰️  Relieve") },
                                                onClick = { mapType = MapType.TERRAIN; showMapTypeMenu = false }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🔀  Híbrido") },
                                                onClick = { mapType = MapType.HYBRID; showMapTypeMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Panel de búsqueda fijo (no-modal) con animación ──
                    val rutaColores = remember(rutas) {
                        rutas.associate { it.codigo to it.color }
                    }
                    AnimatedVisibility(
                        visible = mostrarBusqueda,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        enter = slideInVertically(
                            animationSpec = tween(durationMillis = 380),
                            initialOffsetY = { fullHeight -> fullHeight }   // sube desde abajo
                        ) + fadeIn(animationSpec = tween(durationMillis = 280)),
                        exit  = slideOutVertically(
                            animationSpec = tween(durationMillis = 300),
                            targetOffsetY = { fullHeight -> fullHeight }    // baja hacia abajo
                        ) + fadeOut(animationSpec = tween(durationMillis = 200))
                    ) {
                        BusquedaSheet(
                            origenLatLng        = origenLatLng,
                            destinoLatLng       = destinoLatLng,
                            onDismiss           = { mostrarBusqueda = false },
                            onSeleccionarOrigen = { mostrarBusqueda = false; modoSeleccion = "origen" },
                            onSeleccionarDestino= { mostrarBusqueda = false; modoSeleccion = "destino" },
                            onRutaEncontrada    = { codigos -> rutasResaltadas = codigos.toSet() },
                            onNuevaOptimizacion = { rutasResaltadas = emptySet() },
                            rutaColores         = rutaColores
                        )
                    }

                    // ── Sheet de comentarios ──────────────────────────────
                    if (mostrarComentarios && rutaDetalle != null) {
                        ComentariosSheet(
                            rutaId = rutaDetalle!!.id,
                            rutaNombre = rutaDetalle!!.nombre,
                            rutaCodigo = rutaDetalle!!.codigo,
                            onDismiss = { mostrarComentarios = false }
                        )
                    }
                }

                // ── Tab HISTORIAL ─────────────────────────────────────────
                NavTab.HISTORIAL -> {
                    HistorialScreen(
                        onBack = { tabActivo = NavTab.MAPA },
                        onRepetirBusqueda = { origen, destino, _ ->
                            origenLatLng = origen.toLatLng()
                            destinoLatLng = destino.toLatLng()
                            mostrarBusqueda = true
                            tabActivo = NavTab.MAPA
                        },
                        externalSnackbarHostState = snackbarHostState
                    )
                }

                // ── Tab PERFIL ────────────────────────────────────────────
                NavTab.PERFIL -> {
                    PerfilScreen(
                        onBack = { tabActivo = NavTab.MAPA },
                        onLogout = onLogout
                    )
                }
            }
        }

        // ── Bottom Sheet de RUTAS (sobre el mapa, sin scrim oscuro) ──────────
        if (showRutasSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRutasSheet = false },
                sheetState = rutasSheetState,
                scrimColor = Color.Transparent,
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE0E0E0))
                        )
                    }
                }
            ) {
                RutasSheetContent(
                    rutas = rutas,
                    rutasVisibles = rutasVisibles,
                    onToggleRuta = { id ->
                        rutasVisibles = if (rutasVisibles.contains(id))
                            rutasVisibles - id
                        else
                            rutasVisibles + id
                    },
                    onDetalleRuta = { ruta ->
                        rutaDetalle = ruta
                        rutasVisibles = rutasVisibles + ruta.id
                        scope.launch { rutasSheetState.hide() }.invokeOnCompletion {
                            showRutasSheet = false
                        }
                    }
                )
            }
        }

        // ── Sheet de detalle de ruta (sobre el mapa, sin scrim) ──────────
        if (rutaDetalle != null) {
            RutaDetalleSheet(
                ruta = rutaDetalle!!,
                onDismiss = { rutaDetalle = null },
                onBack = {
                    rutaDetalle = null
                    showRutasSheet = true
                },
                onVerOpiniones = { mostrarComentarios = true }
            )
        }
    }
}

// ── Ícono activo del nav (con fondo rojo) ────────────────────────────────────
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
    ) {
        icon(22.dp)
    }
}

// ── Contenido del sheet de Rutas ───────────────────────────────────
@Composable
fun RutasSheetContent(
    rutas: List<RutaMapa>,
    rutasVisibles: Set<String>,
    onToggleRuta: (String) -> Unit,
    onDetalleRuta: (RutaMapa) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Encabezado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Rutas oficiales MPA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavSecondary
                )
                Text(
                    text = "Toca una ruta para verla en el mapa",
                    fontSize = 13.sp,
                    color = NavGray
                )
            }
            Icon(Icons.Filled.Tune, "Filtrar", tint = NavGray, modifier = Modifier.size(22.dp))
        }

        HorizontalDivider(color = Color(0xFFF0F0F0))

        if (rutas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NavRed)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.75f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(rutas) { ruta ->
                    RutaListItem(
                        ruta = ruta,
                        isVisible = rutasVisibles.contains(ruta.id),
                        onToggle = { onToggleRuta(ruta.id) },
                        onDetalle = { onDetalleRuta(ruta) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = Color(0xFFF5F5F5)
                    )
                }
            }
        }
    }
}

// ── Item de ruta ────────────────────────────────────────────────
@Composable
fun RutaListItem(
    ruta: RutaMapa,
    isVisible: Boolean,
    onToggle: () -> Unit,
    onDetalle: () -> Unit = {}
) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(ruta.color))
    } catch (e: Exception) { NavRed }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge con código (rectangular para códigos largos como T-14)
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor.copy(alpha = if (isVisible) 1f else 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ruta.codigo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ruta.nombre.ifEmpty { ruta.codigo },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isVisible) NavRed else NavSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitulo = buildString {
                if (ruta.empresa.isNotEmpty()) append(ruta.empresa)
                if (ruta.frecuencia.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(ruta.frecuencia)
                }
            }
            if (subtitulo.isNotEmpty()) {
                Text(
                    text = subtitulo,
                    fontSize = 12.sp,
                    color = NavGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = "Ver detalle",
            tint = if (isVisible) NavRed else NavGray,
            modifier = Modifier
                .size(20.dp)
                .clickable { onDetalle() }
        )
    }
}

// ── Sheet de detalle de ruta ────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutaDetalleSheet(
    ruta: RutaMapa,
    onDismiss: () -> Unit,
    onBack: () -> Unit = onDismiss,
    onVerOpiniones: () -> Unit = {}   // kept for compatibility, not used with tabs
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Comentarios (tab Opiniones) ───────────────────────────────────
    val comentarioViewModel: ComentarioViewModel = viewModel()
    val comentarios by comentarioViewModel.comentarios.collectAsState()
    val comentarioState by comentarioViewModel.comentarioState.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    var textoComentario by remember { mutableStateOf("") }

    LaunchedEffect(ruta.id) {
        comentarioViewModel.cargarComentarios(ruta.id)
    }
    LaunchedEffect(comentarioState) {
        if (comentarioState is ComentarioState.Success) {
            textoComentario = ""
            comentarioViewModel.resetState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFDDDDDD))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // ── Fila: botón ← + badge + nombre ────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = NavGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val badgeColor = try {
                    Color(android.graphics.Color.parseColor(ruta.color))
                } catch (e: Exception) { NavRed }

                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ruta.codigo,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${ruta.codigo} ${ruta.nombre}".trim(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    if (ruta.empresa.isNotEmpty()) {
                        Text(
                            text = "Emp: ${ruta.empresa}",
                            fontSize = 12.sp,
                            color = NavGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Tabs: RECORRIDO | OPINIONES ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                listOf("RECORRIDO", "OPINIONES").forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = index },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NavRed else NavGray,
                            modifier = Modifier.padding(vertical = 10.dp),
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isSelected) NavRed else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFEEEEEE))

            Spacer(modifier = Modifier.height(12.dp))

            // ── Contenido del tab ──────────────────────────────────────
            when (selectedTab) {

                // ── Tab 0: RECORRIDO ───────────────────────────────────
                0 -> {
                    if (ruta.avenidas.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "RECORRIDO DE LA RUTA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavRed,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = ruta.avenidas,
                                    fontSize = 12.sp,
                                    color = Color(0xFF424242),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Sin información de recorrido",
                                fontSize = 13.sp,
                                color = NavGray
                            )
                        }
                    }
                }

                // ── Tab 1: OPINIONES ───────────────────────────────────
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Lista de comentarios
                        if (comentarios.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay opiniones aún. ¡Sé el primero!",
                                    fontSize = 13.sp,
                                    color = NavGray
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(comentarios) { comentario ->
                                    val fecha = comentario.fecha?.toDate()?.let { ts ->
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(ts)
                                    } ?: ""
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (comentario.destacado) Color(0xFFFFEBEE) else Color(0xFFF5F5F5)
                                        ),
                                        elevation = CardDefaults.cardElevation(0.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (comentario.destacado) {
                                                Text("📌 Destacado", fontSize = 10.sp, color = NavRed, fontWeight = FontWeight.Bold)
                                            }
                                            Text(comentario.texto, fontSize = 13.sp, color = Color(0xFF212121))
                                            Text(
                                                "${comentario.nombreUsuario} · $fecha",
                                                fontSize = 11.sp,
                                                color = NavGray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        // Campo para nuevo comentario
                        if (user != null) {
                            Text("Deja tu opinión:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF212121))

                            OutlinedTextField(
                                value = textoComentario,
                                onValueChange = { textoComentario = it },
                                placeholder = { Text("Comparte tu experiencia con esta ruta...", fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NavRed,
                                    focusedLabelColor = NavRed,
                                    cursorColor = NavRed
                                )
                            )

                            when (comentarioState) {
                                is ComentarioState.Error ->
                                    Text(
                                        (comentarioState as ComentarioState.Error).mensaje,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                is ComentarioState.Success ->
                                    Text("✅ Comentario enviado.", color = NavRed, fontSize = 12.sp)
                                else -> {}
                            }

                            Button(
                                onClick = {
                                    comentarioViewModel.publicarComentario(
                                        rutaId = ruta.id,
                                        rutaNombre = ruta.nombre,
                                        rutaCodigo = ruta.codigo,
                                        texto = textoComentario
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = textoComentario.isNotEmpty() &&
                                        comentarioState !is ComentarioState.Loading,
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
                            Text(
                                "Inicia sesión para dejar un comentario",
                                fontSize = 13.sp,
                                color = NavGray
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}