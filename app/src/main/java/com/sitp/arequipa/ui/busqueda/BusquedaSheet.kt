package com.sitp.arequipa.ui.busqueda

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
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

            // Campo Origen
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSeleccionarOrigen
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50) // verde
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
            }

            // Campo Destino
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSeleccionarDestino
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFFF44336) // rojo
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
            }

            // Selector tiempo/costo
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

            // Resultado de la IA
            when (val state = busquedaState) {
                is BusquedaState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("La IA está analizando las rutas...")
                    }
                }
                is BusquedaState.Success -> {
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

                    LaunchedEffect(state.respuesta) {
                        val codigosRegex = Regex("RUTAS:\\s*\\[([^\\]]+)\\]")
                        val match = codigosRegex.find(state.respuesta)
                        val codigos = match?.groupValues?.get(1)
                            ?.split(",")
                            ?.map { it.trim() }
                            ?: emptyList()
                        if (codigos.isNotEmpty()) {
                            onRutaEncontrada(codigos)
                        }
                    }
                }
                is BusquedaState.Error -> {
                    Text(
                        text = state.mensaje,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

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
                enabled = origenLatLng != null && destinoLatLng != null &&
                        busquedaState !is BusquedaState.Loading
            ) {
                Text("Buscar ruta óptima")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}