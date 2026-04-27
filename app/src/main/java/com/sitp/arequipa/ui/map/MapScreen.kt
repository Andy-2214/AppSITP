package com.sitp.arequipa.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*

data class RutaMapa(
    val nombre: String = "",
    val color: String = "#FF0000",
    val coordenadas: List<Map<String, Double>> = emptyList()
)

@Composable
fun MapScreen(onLogout: () -> Unit) {
    val arequipa = LatLng(-16.4090, -71.5375)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(arequipa, 13f)
    }

    var rutas by remember { mutableStateOf<List<RutaMapa>>(emptyList()) }

    // Cargar rutas desde Firebase
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("rutas")
            .get()
            .addOnSuccessListener { snapshot ->
                rutas = snapshot.documents.map { doc ->
                    RutaMapa(
                        nombre = doc.getString("nombre") ?: "",
                        color = doc.getString("color") ?: "#FF0000",
                        coordenadas = doc.get("coordenadas") as? List<Map<String, Double>> ?: emptyList()
                    )
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false,
                latLngBoundsForCameraTarget = com.google.android.gms.maps.model.LatLngBounds(
                    LatLng(-16.5500, -71.6500),
                    LatLng(-16.2500, -71.4000)
                ),
                mapStyleOptions = MapStyleOptions(
                    """
        [
            {
                "featureType": "poi",
                "stylers": [{"visibility": "off"}]
            },
            {
                "featureType": "transit",
                "stylers": [{"visibility": "off"}]
            },
            {
                "featureType": "road",
                "elementType": "labels",
                "stylers": [{"visibility": "on"}]
            }
        ]
    """
                )
            )
        ) {
            // Dibujar cada ruta
            rutas.forEach { ruta ->
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

        Button(
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Cerrar sesión")
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