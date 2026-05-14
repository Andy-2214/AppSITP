package com.sitp.arequipa.ui.busqueda

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.viewmodel.BusquedaState
import com.sitp.arequipa.viewmodel.BusquedaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusquedaSheet(
    origenLatLng: LatLng?,
    destinoLatLng: LatLng?,
    onDismiss: () -> Unit,
    onSeleccionarOrigen: () -> Unit,
    onSeleccionarDestino: () -> Unit,
    onRutaEncontrada: (List<String>) -> Unit,
    busquedaViewModel: BusquedaViewModel = viewModel()
) {
    var preferencia by remember { mutableStateOf("tiempo") }
    val busquedaState by busquedaViewModel.busquedaState.collectAsState()

    // Extraer códigos una sola vez cuando llega el Success
    val codigosRuta: List<String> = remember(busquedaState) {
        if (busquedaState is BusquedaState.Success) {
            val respuesta = (busquedaState as BusquedaState.Success).respuesta
            val codigosRegex = Regex("RUTAS\\s*:\\s*\\[([^\\]]+)\\]", RegexOption.IGNORE_CASE)
            val match = codigosRegex.find(respuesta)
            match?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim().trim('"') }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } else emptyList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔍 Buscar ruta",
                style = MaterialTheme.typography.headlineSmall
            )

            // Campos origen/destino y preferencia solo visibles cuando no hay resultado
            if (busquedaState !is BusquedaState.Success) {

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSeleccionarOrigen
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (origenLatLng != null)
                                "Origen: ${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}"
                            else "Toca para marcar origen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (origenLatLng != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSeleccionarDestino
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (destinoLatLng != null)
                                "Destino: ${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}"
                            else "Toca para marcar destino",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (destinoLatLng != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Preferencia:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferencia == "tiempo",
                        onClick = { preferencia = "tiempo" },
                        label = { Text("⏱️ Más rápido") }
                    )
                    FilterChip(
                        selected = preferencia == "costo",
                        onClick = { preferencia = "costo" },
                        label = { Text("💰 Más económico") }
                    )
                }
            }

            // Estados
            when (val state = busquedaState) {

                is BusquedaState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("La IA está analizando las rutas...")
                    }
                }

                is BusquedaState.Success -> {
                    // Tarjeta con la respuesta de la IA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🤖 Recomendación de la IA:",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.respuesta,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Botón principal: ver en el mapa y cerrar el sheet
                    if (codigosRuta.isNotEmpty()) {
                        Button(
                            onClick = {
                                onRutaEncontrada(codigosRuta)
                                onDismiss() // FIX BUG 2: ahora cierra aquí, con intención explícita del usuario
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗺️ Ver rutas en el mapa")
                        }
                    }

                    // Botón secundario: nueva optimización — limpia el estado y vuelve al formulario
                    OutlinedButton(
                        onClick = { busquedaViewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nueva optimización")
                    }
                }

                is BusquedaState.Error -> {
                    Text(
                        text = state.mensaje,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(
                        onClick = { busquedaViewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Intentar de nuevo")
                    }
                }

                else -> {
                    // Idle: botón de buscar normal
                    Button(
                        onClick = {
                            if (origenLatLng != null && destinoLatLng != null) {
                                busquedaViewModel.buscarRutaPorCoordenadas(
                                    origenLat = origenLatLng.latitude,
                                    origenLng = origenLatLng.longitude,
                                    destinoLat = destinoLatLng.latitude,
                                    destinoLng = destinoLatLng.longitude,
                                    preferencia = preferencia
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = origenLatLng != null && destinoLatLng != null
                    ) {
                        Text("Buscar ruta óptima")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}