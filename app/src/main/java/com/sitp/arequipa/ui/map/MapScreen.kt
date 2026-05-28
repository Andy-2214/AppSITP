package com.sitp.arequipa.ui.map

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.maps.android.compose.MapType
import com.sitp.arequipa.ui.busqueda.BusquedaSheet
import com.sitp.arequipa.ui.comentarios.ComentariosSheet
import com.google.android.gms.maps.model.BitmapDescriptorFactory

data class RutaMapa(
    val id: String = "",
    val nombre: String = "",
    val codigo: String = "",
    val empresa: String = "",
    val color: String = "#FF0000",
    val avenidas: String = "",
    val coordenadas: List<Map<String, Double>> = emptyList()
)

@SuppressLint("MissingPermission")
fun obtenerUbicacion(
    context: android.content.Context,
    onUbicacion: (android.location.Location) -> Unit
) {
    LocationServices.getFusedLocationProviderClient(context)
        .lastLocation
        .addOnSuccessListener { location ->
            location?.let { onUbicacion(it) }
        }
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

// Convierte un string "lat, lng" a LatLng
fun String.toLatLng(): LatLng? {
    return try {
        val parts = this.split(",").map { it.trim() }
        LatLng(parts[0].toDouble(), parts[1].toDouble())
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    onLogout: () -> Unit,
    onPerfil: () -> Unit,
    onHistorial: () -> Unit,
    origenInicial: String? = null,      // ← nuevo
    destinoInicial: String? = null,     // ← nuevo
    preferenciaInicial: String? = null  // ← nuevo
) {
    val arequipa = LatLng(-16.4090, -71.5375)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(arequipa, 13f)
    }

    var rutas by remember { mutableStateOf<List<RutaMapa>>(emptyList()) }
    var rutasVisibles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var empresasExpandidas by remember { mutableStateOf<Set<String>>(emptySet()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var rutasResaltadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var modoSeleccion by remember { mutableStateOf<String?>(null) }
    var rutaSeleccionada by remember { mutableStateOf<RutaMapa?>(null) }
    var mostrarComentarios by remember { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // ── Si viene de repetir búsqueda, pre-rellenar origen/destino ──
    var origenLatLng by remember { mutableStateOf(origenInicial?.toLatLng()) }
    var destinoLatLng by remember { mutableStateOf(destinoInicial?.toLatLng()) }
    var mostrarBusqueda by remember {
        mutableStateOf(origenInicial != null && destinoInicial != null)
    }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("rutas")
            .get()
            .addOnSuccessListener { snapshot ->
                rutas = snapshot.documents.map { doc ->
                    RutaMapa(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        codigo = doc.getString("codigo") ?: "",
                        empresa = doc.getString("empresa") ?: "Sin empresa",
                        color = doc.getString("color") ?: "#FF0000",
                        avenidas = doc.getString("avenidas") ?: "",
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

    val rutasPorEmpresa = rutas.groupBy { it.empresa }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚌 Transporte Arequipa", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { scope.launch { drawerState.close() } }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Divider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Buscar ruta") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        rutasResaltadas = emptySet()
                        mostrarBusqueda = true
                    }
                )

                Divider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Mi perfil") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onPerfil()
                    }
                )

                Divider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Mi historial y favoritos") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onHistorial()
                    }
                )

                Divider()

                Text(
                    text = "Rutas disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    rutasPorEmpresa.forEach { (empresa, rutasEmpresa) ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = empresa,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    empresasExpandidas =
                                        if (empresasExpandidas.contains(empresa))
                                            empresasExpandidas - empresa
                                        else empresasExpandidas + empresa
                                }) {
                                    Icon(
                                        if (empresasExpandidas.contains(empresa))
                                            Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            }
                        }

                        if (empresasExpandidas.contains(empresa)) {
                            items(rutasEmpresa) { ruta ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = rutasVisibles.contains(ruta.id),
                                        onCheckedChange = { checked ->
                                            rutasVisibles =
                                                if (checked) rutasVisibles + ruta.id
                                                else rutasVisibles - ruta.id
                                            if (!checked) {
                                                rutasResaltadas = rutasResaltadas - ruta.codigo
                                            }
                                        }
                                    )
                                    Column {
                                        Text(ruta.codigo, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            ruta.nombre,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Divider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("Cerrar sesión") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            val rutaTocada = rutas
                                .filter { ruta ->
                                    rutasVisibles.contains(ruta.id) ||
                                            rutasResaltadas.any { it.equals(ruta.codigo, ignoreCase = true) }
                                }
                                .firstOrNull { ruta ->
                                    val puntos = ruta.coordenadas.mapNotNull { coord ->
                                        val lat = coord["lat"] ?: return@mapNotNull null
                                        val lng = coord["lng"] ?: return@mapNotNull null
                                        LatLng(lat, lng)
                                    }
                                    puntoEnRuta(latLng, puntos)
                                }
                            rutaSeleccionada = rutaTocada
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
                            {"featureType": "poi", "stylers": [{"visibility": "off"}]},
                            {"featureType": "transit", "stylers": [{"visibility": "off"}]},
                            {"featureType": "road", "elementType": "labels", "stylers": [{"visibility": "on"}]}
                        ]
                    """)
                )
            ) {
                origenLatLng?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "Origen",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                destinoLatLng?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "Destino",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }

                // Rutas normales del drawer
                rutas.filter { rutasVisibles.contains(it.id) }
                    .forEach { ruta ->
                        val puntos = ruta.coordenadas.mapNotNull { coord ->
                            val lat = coord["lat"] ?: return@mapNotNull null
                            val lng = coord["lng"] ?: return@mapNotNull null
                            LatLng(lat, lng)
                        }
                        if (puntos.isNotEmpty()) {
                            Polyline(points = puntos, color = parseColor(ruta.color), width = 7f)
                        }
                    }

                // Rutas resaltadas por IA — borde blanco + color oficial
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

                // Dialog al tocar una ruta
                rutaSeleccionada?.let { ruta ->
                    AlertDialog(
                        onDismissRequest = { rutaSeleccionada = null },
                        title = { Text("🚌 ${ruta.codigo}") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Nombre: ${ruta.nombre}")
                                Text("Empresa: ${ruta.empresa}")
                                if (ruta.avenidas.isNotEmpty()) {
                                    Text("Avenidas: ${ruta.avenidas}")
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Color:")
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                parseColor(ruta.color),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { mostrarComentarios = true }) {
                                Text("💬 Ver opiniones")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { rutaSeleccionada = null }) {
                                Text("Cerrar")
                            }
                        }
                    )
                }
            }

            if (modoSeleccion != null) {
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (modoSeleccion == "origen")
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
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

            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        mapType = when (mapType) {
                            MapType.NORMAL    -> MapType.SATELLITE
                            MapType.SATELLITE -> MapType.HYBRID
                            MapType.HYBRID    -> MapType.TERRAIN
                            MapType.TERRAIN   -> MapType.NORMAL
                            else              -> MapType.NORMAL
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = when (mapType) {
                            MapType.NORMAL    -> "🗺️"
                            MapType.SATELLITE -> "🛰️"
                            MapType.HYBRID    -> "🌍"
                            MapType.TERRAIN   -> "🏔️"
                            else              -> "🗺️"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                FloatingActionButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menú")
                }

                FloatingActionButton(
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
                    }
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Mi ubicación")
                }
            }

            if (mostrarBusqueda) {
                BusquedaSheet(
                    origenLatLng = origenLatLng,
                    destinoLatLng = destinoLatLng,
                    onDismiss = { mostrarBusqueda = false },
                    onSeleccionarOrigen = {
                        mostrarBusqueda = false
                        modoSeleccion = "origen"
                    },
                    onSeleccionarDestino = {
                        mostrarBusqueda = false
                        modoSeleccion = "destino"
                    },
                    onRutaEncontrada = { codigos ->
                        rutasResaltadas = codigos.toSet()
                    }
                )
            }

            // HU-21: Sheet de comentarios
            if (mostrarComentarios && rutaSeleccionada != null) {
                ComentariosSheet(
                    rutaId = rutaSeleccionada!!.id,
                    rutaNombre = rutaSeleccionada!!.nombre,
                    rutaCodigo = rutaSeleccionada!!.codigo,
                    onDismiss = {
                        mostrarComentarios = false
                        rutaSeleccionada = null
                    }
                )
            }
        }
    }
}

fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Red
    }
}