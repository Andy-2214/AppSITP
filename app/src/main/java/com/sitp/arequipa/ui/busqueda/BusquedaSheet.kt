package com.sitp.arequipa.ui.busqueda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import com.sitp.arequipa.viewmodel.BusquedaState
import com.sitp.arequipa.viewmodel.BusquedaViewModel
import com.sitp.arequipa.viewmodel.HistorialViewModel

private val BusRed    = Color(0xFFC62828)
private val BusGray   = Color(0xFF757575)
private val BusBg     = Color(0xFFF5F5F5)
private val BusGreen  = Color(0xFF388E3C)

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

    // ── Lógica: guardar historial ──────────────────────────────────────────────
    var yaGuardado by remember { mutableStateOf(false) }
    LaunchedEffect(busquedaState) {
        if (busquedaState is BusquedaState.Success && !yaGuardado) {
            yaGuardado = true
            val respuesta = (busquedaState as BusquedaState.Success).respuesta
            if (origenLatLng != null && destinoLatLng != null) {
                historialViewModel.guardarBusqueda(
                    origen  = "${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}",
                    destino = "${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}",
                    preferencia   = preferencia,
                    respuestaIA   = respuesta
                )
            }
        }
        if (busquedaState is BusquedaState.Idle) yaGuardado = false
    }

    var primeraVez by remember { mutableStateOf(true) }
    LaunchedEffect(origenLatLng, destinoLatLng) {
        if (primeraVez) primeraVez = false
        else busquedaViewModel.resetState()
    }

    val codigosRuta: List<String> = remember(busquedaState) {
        if (busquedaState is BusquedaState.Success) {
            val respuesta = (busquedaState as BusquedaState.Success).respuesta
            val codigosRegex = Regex("RUTAS\\s*:\\s*\\[?([^\\]\\n]+)\\]?", RegexOption.IGNORE_CASE)
            val match = codigosRegex.find(respuesta)
            match?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim().trim('"').trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } else emptyList()
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Título ────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = BusRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Buscar ruta",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            }

            // ── Tarjetas Origen / Destino ─────────────────────────────────────
            if (busquedaState !is BusquedaState.Success && busquedaState !is BusquedaState.Loading) {

                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Origen
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccionarOrigen() },
                        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = BusBg),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = BusGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text("ORIGEN", fontSize = 10.sp, color = BusGray, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                Text(
                                    text = if (origenLatLng != null)
                                        "Origen: ${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}"
                                    else "Toca para marcar origen",
                                    fontSize = 15.sp,
                                    color = if (origenLatLng != null) Color(0xFF1A1A1A) else BusGray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Línea conectora
                    Box(
                        modifier = Modifier
                            .padding(start = 28.dp)
                            .width(2.dp)
                            .height(6.dp)
                            .background(Color(0xFFBDBDBD))
                    )

                    // Destino
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccionarDestino() },
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                        colors = CardDefaults.cardColors(containerColor = BusBg),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = BusRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text("DESTINO", fontSize = 10.sp, color = BusGray, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                Text(
                                    text = if (destinoLatLng != null)
                                        "Destino: ${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}"
                                    else "Toca para marcar destino",
                                    fontSize = 15.sp,
                                    color = if (destinoLatLng != null) Color(0xFF1A1A1A) else BusGray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // ── Preferencia ───────────────────────────────────────────────
                Text(
                    text = "Preferencia:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenciaCard(
                        icon     = Icons.Filled.AccessTime,
                        titulo   = "Menor tiempo",
                        subtitulo = "Llega más rápido a tu destino",
                        selected = preferencia == "tiempo",
                        onClick  = { preferencia = "tiempo" }
                    )
                    PreferenciaCard(
                        icon     = Icons.Filled.MonetizationOn,
                        titulo   = "Menor costo",
                        subtitulo = "La ruta más económica",
                        selected = preferencia == "costo",
                        onClick  = { preferencia = "costo" }
                    )
                    PreferenciaCard(
                        icon     = Icons.Filled.SwapCalls,
                        titulo   = "Menos transbordos",
                        subtitulo = "Ruta más directa, menos caminata",
                        selected = preferencia == "transbordos",
                        onClick  = { preferencia = "transbordos" }
                    )
                }

                // ── Optimizando por ───────────────────────────────────────────
                Text(
                    text = when (preferencia) {
                        "tiempo"      -> "Optimizando por: menor tiempo de viaje"
                        "costo"       -> "Optimizando por: menor costo (S/1.30 por combi)"
                        "transbordos" -> "Optimizando por: menos transbordos posibles"
                        else -> ""
                    },
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = BusRed
                )

                // ── Campo adicional ───────────────────────────────────────────
                Text(
                    text = "Información adicional (opcional)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A)
                )
                OutlinedTextField(
                    value = consultaExtra,
                    onValueChange = { consultaExtra = it },
                    placeholder = {
                        Text(
                            "Ej: Quiero pasar por el mercado San Camilo, prefiero caminar poco",
                            fontSize = 13.sp,
                            color = BusGray
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BusRed,
                        focusedLabelColor  = BusRed,
                        cursorColor        = BusRed
                    )
                )
            }

            // ── Estados de búsqueda (lógica intacta) ─────────────────────────
            when (val state = busquedaState) {
                is BusquedaState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = BusRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("La IA está analizando las rutas...", color = BusGray, fontSize = 14.sp)
                    }
                }

                is BusquedaState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🤖 Recomendación de la IA:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BusRed)
                            Text(state.respuesta, fontSize = 13.sp, color = Color(0xFF1A1A1A))
                        }
                    }

                    if (codigosRuta.isNotEmpty()) {
                        Button(
                            onClick = { onRutaEncontrada(codigosRuta); onDismiss() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BusRed)
                        ) {
                            Text("🗺️ Ver rutas en el mapa", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            consultaExtra = ""
                            primeraVez    = true
                            busquedaViewModel.resetState()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BusRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BusRed)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nueva optimización", fontWeight = FontWeight.SemiBold)
                    }
                }

                is BusquedaState.Error -> {
                    Text(state.mensaje, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = { busquedaViewModel.resetState() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BusRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BusRed)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Intentar de nuevo", fontWeight = FontWeight.SemiBold)
                    }
                }

                else -> {
                    Button(
                        onClick = {
                            if (origenLatLng != null && destinoLatLng != null) {
                                busquedaViewModel.buscarRutaPorCoordenadas(
                                    origenLat    = origenLatLng.latitude,
                                    origenLng    = origenLatLng.longitude,
                                    destinoLat   = destinoLatLng.latitude,
                                    destinoLng   = destinoLatLng.longitude,
                                    preferencia  = preferencia,
                                    consultaExtra = consultaExtra
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BusRed),
                        enabled = origenLatLng != null && destinoLatLng != null
                    ) {
                        Text("Buscar ruta óptima", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ── Card de preferencia ───────────────────────────────────────────────────────
@Composable
private fun PreferenciaCard(
    icon: ImageVector,
    titulo: String,
    subtitulo: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor      = if (selected) BusRed else BusBg
    val contentColor = if (selected) Color.White else Color(0xFF1A1A1A)
    val subColor     = if (selected) Color.White.copy(alpha = 0.85f) else BusGray
    val iconBg       = if (selected) Color.White.copy(alpha = 0.2f) else Color(0xFFE0E0E0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = contentColor)
                Text(subtitulo, fontSize = 12.sp, color = subColor)
            }
        }
    }
}