package com.sitp.arequipa.ui.busqueda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sitp.arequipa.viewmodel.HistorialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusquedaSheet(
    origenLatLng: LatLng?,
    destinoLatLng: LatLng?,
    onDismiss: () -> Unit,
    onSeleccionarOrigen: () -> Unit,
    onSeleccionarDestino: () -> Unit,
    onRutaEncontrada: (List<String>) -> Unit,
    busquedaViewModel: BusquedaViewModel = viewModel(),
    historialViewModel: HistorialViewModel = viewModel()
) {
    var preferencia by remember { mutableStateOf("tiempo") }
    var consultaExtra by remember { mutableStateOf("") }
    val busquedaState by busquedaViewModel.busquedaState.collectAsState()

    // Guardar en historial automáticamente cuando llega resultado
    var yaGuardado by remember { mutableStateOf(false) }
    LaunchedEffect(busquedaState) {
        if (busquedaState is BusquedaState.Success && !yaGuardado) {
            yaGuardado = true
            val respuesta = (busquedaState as BusquedaState.Success).respuesta
            // Solo guardamos si tenemos coordenadas válidas
            if (origenLatLng != null && destinoLatLng != null) {
                historialViewModel.guardarBusqueda(
                    origen = "${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}",
                    destino = "${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}",
                    preferencia = preferencia,
                    respuestaIA = respuesta
                )
            }
        }
        // Resetear flag cuando vuelve a Idle
        if (busquedaState is BusquedaState.Idle) {
            yaGuardado = false
        }
    }

    // Resetea solo cuando el usuario mueve un pin, no al abrir el sheet
    var primeraVez by remember { mutableStateOf(true) }
    LaunchedEffect(origenLatLng, destinoLatLng) {
        if (primeraVez) {
            primeraVez = false
        } else {
            busquedaViewModel.resetState()
        }
    }

    val codigosRuta: List<String> = remember(busquedaState) {
        if (busquedaState is BusquedaState.Success) {
            val respuesta = (busquedaState as BusquedaState.Success).respuesta
            val codigosRegex = Regex(
                "RUTAS\\s*:\\s*\\[?([^\\]\\n]+)\\]?",
                RegexOption.IGNORE_CASE
            )
            val match = codigosRegex.find(respuesta)
            val lista = match?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim().trim('"').trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            println("DEBUG codigos: $lista")
            lista
        } else emptyList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔍 Buscar ruta",
                style = MaterialTheme.typography.headlineSmall
            )

            if (busquedaState !is BusquedaState.Success &&
                busquedaState !is BusquedaState.Loading
            ) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSeleccionarOrigen
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (origenLatLng != null)
                                "Origen: ${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}"
                            else "Toca para marcar origen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (origenLatLng != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSeleccionarDestino
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFF44336))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (destinoLatLng != null)
                                "Destino: ${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}"
                            else "Toca para marcar destino",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (destinoLatLng != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Preferencia:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                    FilterChip(
                        selected = preferencia == "transbordos",
                        onClick = { preferencia = "transbordos" },
                        label = { Text("🔀 Menos transbordos") }
                    )
                }

                Text(
                    text = when (preferencia) {
                        "tiempo"      -> "Optimizando por: menor tiempo de viaje"
                        "costo"       -> "Optimizando por: menor costo (S/1.30 por combi)"
                        "transbordos" -> "Optimizando por: menos transbordos posibles"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = consultaExtra,
                    onValueChange = { consultaExtra = it },
                    label = { Text("Información adicional (opcional)") },
                    placeholder = {
                        Text(
                            "Ej: Quiero pasar por el mercado San Camilo, prefiero caminar poco",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🤖 Recomendación de la IA:", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.respuesta, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (codigosRuta.isNotEmpty()) {
                        Button(
                            onClick = { onRutaEncontrada(codigosRuta); onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗺️ Ver rutas en el mapa")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            consultaExtra = ""
                            primeraVez = true
                            busquedaViewModel.resetState()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nueva optimización")
                    }
                }

                is BusquedaState.Error -> {
                    Text(state.mensaje, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = { busquedaViewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Intentar de nuevo")
                    }
                }

                else -> {
                    Button(
                        onClick = {
                            if (origenLatLng != null && destinoLatLng != null) {
                                busquedaViewModel.buscarRutaPorCoordenadas(
                                    origenLat = origenLatLng.latitude,
                                    origenLng = origenLatLng.longitude,
                                    destinoLat = destinoLatLng.latitude,
                                    destinoLng = destinoLatLng.longitude,
                                    preferencia = preferencia,
                                    consultaExtra = consultaExtra
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