package com.sitp.arequipa.ui.map

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
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

data class RutaMapa(
    val id: String = "",
    val nombre: String = "",
    val codigo: String = "",
    val empresa: String = "",
    val color: String = "#FF0000",
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    onLogout: () -> Unit,
    onPerfil: () -> Unit
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

    val locationPermission = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Cargar rutas desde Firebase
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
                        coordenadas = doc.get("coordenadas") as? List<Map<String, Double>> ?: emptyList()
                    )
                }
            }
    }
    LaunchedEffect(Unit) {
        if (!locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
        }
    }

    val rutasPorEmpresa = rutas.groupBy { it.empresa }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚌 Transporte Arequipa",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = { scope.launch { drawerState.close() } }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

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

                Text(
                    text = "Rutas disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    rutasPorEmpresa.forEach { (empresa, rutasEmpresa) ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    empresasExpandidas = if (empresasExpandidas.contains(empresa))
                                        empresasExpandidas - empresa
                                    else
                                        empresasExpandidas + empresa
                                }) {
                                    Icon(
                                        if (empresasExpandidas.contains(empresa))
                                            Icons.Default.KeyboardArrowUp
                                        else
                                            Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            }
                        }

                        if (empresasExpandidas.contains(empresa)) {
                            items(rutasEmpresa) { ruta ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = rutasVisibles.contains(ruta.id),
                                        onCheckedChange = { checked ->
                                            rutasVisibles = if (checked)
                                                rutasVisibles + ruta.id
                                            else
                                                rutasVisibles - ruta.id
                                        }
                                    )
                                    Column {
                                        Text(
                                            text = ruta.codigo,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = ruta.nombre,
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
                properties = MapProperties(
                    isMyLocationEnabled = locationPermission.status.isGranted,
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
                rutas.filter { rutasVisibles.contains(it.id) }.forEach { ruta ->
                    val puntos = ruta.coordenadas.mapNotNull { coord ->
                        val lat = coord["lat"] ?: return@mapNotNull null
                        val lng = coord["lng"] ?: return@mapNotNull null
                        LatLng(lat, lng)
                    }
                    if (puntos.isNotEmpty()) {
                        Polyline(
                            points = puntos,
                            color = parseColor(ruta.color),
                            width = 8f
                        )
                    }
                }
            }

            // Botones lado derecho centro
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón menú
                FloatingActionButton(
                    onClick = { scope.launch { drawerState.open() } }
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menú")
                }

                // Botón mi ubicación
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