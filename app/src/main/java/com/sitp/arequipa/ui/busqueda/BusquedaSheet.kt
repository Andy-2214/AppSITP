package com.sitp.arequipa.ui.busqueda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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

private val BusRed      = Color(0xFFC62828)
private val BusDarkRed  = Color(0xFF8E1B1B)
private val BusGray     = Color(0xFF757575)
private val BusBg       = Color(0xFFF5F5F5)
private val BusGreen    = Color(0xFF388E3C)
private val BusLightRed = Color(0xFFFFEBEE)

fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) { Color(0xFFC62828) }

// ── Data models ──────────────────────────────────────────────────────────────
data class SegmentoRuta(
    val numero: Int,
    val codigoRuta: String,
    val abordarTexto: String,
    val abordarDetalle: String,
    val bajarTexto: String,
    val bajarDetalle: String
)

data class RutaParseada(
    val codigos: List<String>,
    val segmentos: List<SegmentoRuta>,
    val estimacionTiempo: String,
    val estimacionCosto: String
)

// ── Parser ───────────────────────────────────────────────────────────────────
fun parsearRespuestaIA(respuesta: String): RutaParseada {
    val codigosRegex = Regex("RUTAS\\s*:\\s*\\[?([^\\]\\n]+)\\]?", RegexOption.IGNORE_CASE)
    val codigos = codigosRegex.find(respuesta)?.groupValues?.get(1)
        ?.split(",")?.map { it.trim().trim('"').trim() }?.filter { it.isNotEmpty() }
        ?: emptyList()

    val pasosRegex = Regex("""(\d+)\.\s*(.+?)(?=\n\d+\.|\nESTIMACION|\nEstimacion|\nEstimación|\z)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    val segmentosSinFusionar = pasosRegex.findAll(respuesta).toList().mapNotNull { match ->
        val texto = match.groupValues[2].trim().replace("\n", " ").replace("  ", " ")
        val contieneRuta = listOf("combi","bus","tome","transbordo","bájese","bajese","camine")
            .any { texto.contains(it, ignoreCase = true) }
        if (!contieneRuta) return@mapNotNull null

        val codigo = Regex("""(?:combi|bus|ruta)\s+([A-Z]+-?\d+[A-Za-z]?)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.get(1) ?: ""

        val splitRegex = Regex(""",?\s*y\s+(?:bájese|bajese|baje)\s+""", RegexOption.IGNORE_CASE)
        val partes = splitRegex.split(texto, 2)
        val abordar = partes[0].trim()
        val bajar   = if (partes.size > 1) partes[1].trim() else ""

        val cercaRegex = Regex(""",?\s*cerca\s+de\s+(.+)""", RegexOption.IGNORE_CASE)
        val mA = cercaRegex.find(abordar)
        val mB = cercaRegex.find(bajar)

        val abordarTexto   = (if (mA != null) abordar.substring(0, mA.range.first) else abordar).trim().trimEnd(',', '.')
        val abordarDetalle = mA?.groupValues?.get(1)?.trim()?.trimEnd('.') ?: ""
        val bajarBase      = (if (mB != null) bajar.substring(0, mB.range.first) else bajar).trim().trimEnd(',', '.')
        val bajarDetalle   = mB?.groupValues?.get(1)?.trim()?.trimEnd('.') ?: ""
        val bajarTexto = when {
            bajarBase.isEmpty() -> ""
            bajarBase.startsWith("Bájese", ignoreCase = true) ||
                    bajarBase.startsWith("Bajese",  ignoreCase = true) ||
                    bajarBase.startsWith("Baje",    ignoreCase = true) ->
                bajarBase.replaceFirst("baje ", "Bájese en ", ignoreCase = true)
                    .replaceFirst("bajese ", "Bájese en ", ignoreCase = true)
            else -> "Bájese en $bajarBase"
        }

        SegmentoRuta(0, codigo, abordarTexto,
            if (abordarDetalle.isNotEmpty()) "Cerca de $abordarDetalle" else "",
            bajarTexto,
            if (bajarDetalle.isNotEmpty()) "Cerca de $bajarDetalle" else "")
    }

    val segmentosFusionados = segmentosSinFusionar
        .fold(mutableListOf<SegmentoRuta>()) { acc, segmento ->
            val ultimo = acc.lastOrNull()
            if (ultimo != null && ultimo.codigoRuta == segmento.codigoRuta) {
                acc[acc.lastIndex] = ultimo.copy(
                    bajarTexto   = segmento.bajarTexto,
                    bajarDetalle = segmento.bajarDetalle
                )
            } else {
                acc.add(segmento)
            }
            acc
        }
        .mapIndexed { i, s -> s.copy(numero = i + 1) }

    val tiempo = Regex("""ESTIMACION[:\s]*([^,\n]*(?:minutos|min|hora)[^,\n]*)""", RegexOption.IGNORE_CASE)
        .find(respuesta)?.groupValues?.get(1)?.trim() ?: ""
    val costo  = Regex("""S/\s*[\d.]+(?:\s*\([^)]*\))?""", RegexOption.IGNORE_CASE)
        .find(respuesta)?.value?.trim() ?: ""

    return RutaParseada(codigos.distinct(), segmentosFusionados, tiempo, costo)
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BusquedaSheet(
    origenLatLng: LatLng?,
    destinoLatLng: LatLng?,
    onDismiss: () -> Unit,
    onSeleccionarOrigen: () -> Unit,
    onSeleccionarDestino: () -> Unit,
    onRutaEncontrada: (List<String>) -> Unit,
    onNuevaOptimizacion: () -> Unit,
    rutaColores: Map<String, String> = emptyMap(),
    rutaSentidos: Map<String, String> = emptyMap(),
    busquedaViewModel: BusquedaViewModel = viewModel(),
    historialViewModel: HistorialViewModel = viewModel()
) {
    var preferencia   by remember { mutableStateOf("tiempo") }
    var consultaExtra by remember { mutableStateOf("") }
    val busquedaState by busquedaViewModel.busquedaState.collectAsState()

    // ── Nuevo estado: panel minimizado ────────────────────────────────────
    var minimizado by remember { mutableStateOf(false) }

    var yaGuardado by remember { mutableStateOf(false) }
    LaunchedEffect(busquedaState) {
        if (busquedaState is BusquedaState.Success && !yaGuardado) {
            yaGuardado = true
            val r = (busquedaState as BusquedaState.Success).respuesta
            if (origenLatLng != null && destinoLatLng != null) {
                historialViewModel.guardarBusqueda(
                    origen      = "${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}",
                    destino     = "${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}",
                    preferencia = preferencia,
                    respuestaIA = r
                )
            }
        }
        if (busquedaState is BusquedaState.Idle) yaGuardado = false
    }

    var primeraVez by remember { mutableStateOf(true) }
    LaunchedEffect(origenLatLng, destinoLatLng) {
        if (primeraVez) primeraVez = false else busquedaViewModel.resetState()
    }

    val rutaParseada = remember(busquedaState) {
        if (busquedaState is BusquedaState.Success)
            parsearRespuestaIA((busquedaState as BusquedaState.Success).respuesta)
        else RutaParseada(emptyList(), emptyList(), "", "")
    }

    LaunchedEffect(rutaParseada.codigos) {
        if (rutaParseada.codigos.isNotEmpty()) onRutaEncontrada(rutaParseada.codigos)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Si minimizado: altura fija tipo "pastilla"; si no: 55% de pantalla
            .then(
                if (minimizado) Modifier.wrapContentHeight()
                else Modifier.fillMaxHeight(0.55f)
            ),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color.White,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Handle + botón minimizar ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
            ) {
                // Barrita gris centrada
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE0E0E0))
                )
                // Botón minimizar/expandir alineado a la derecha
                IconButton(
                    onClick = { minimizado = !minimizado },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (minimizado)
                            Icons.Filled.KeyboardArrowUp
                        else
                            Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (minimizado) "Expandir" else "Minimizar",
                        tint = BusGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Fila resumen visible siempre (cuando minimizado muestra info básica) ──
            if (minimizado) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { minimizado = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BusLightRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Place, null, tint = BusRed, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = when (busquedaState) {
                            is BusquedaState.Success -> "Ruta encontrada — toca para ver"
                            is BusquedaState.Loading -> "Buscando ruta..."
                            else -> "Buscar ruta — toca para abrir"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A)
                    )
                }
            }

            // ── Contenido principal — animado al mostrar/ocultar ──────────
            AnimatedVisibility(
                visible = !minimizado,
                enter = expandVertically(animationSpec = tween(250)),
                exit  = shrinkVertically(animationSpec = tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val esResultado = busquedaState is BusquedaState.Success
                        IconButton(
                            onClick = {
                                if (esResultado) {
                                    consultaExtra = ""
                                    primeraVez    = true
                                    busquedaViewModel.resetState()
                                    onNuevaOptimizacion()
                                } else {
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = BusDarkRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(BusLightRed),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Place, null, tint = BusRed, modifier = Modifier.size(20.dp)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar ruta", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    }

                    if (busquedaState !is BusquedaState.Success && busquedaState !is BusquedaState.Loading) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onSeleccionarOrigen() },
                                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = BusBg), elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Filled.LocationOn, null, tint = BusGreen, modifier = Modifier.size(22.dp))
                                    Column {
                                        Text("ORIGEN", fontSize = 10.sp, color = BusGray, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                        Text(
                                            if (origenLatLng != null) "${String.format("%.4f", origenLatLng.latitude)}, ${String.format("%.4f", origenLatLng.longitude)}"
                                            else "Toca para marcar origen",
                                            fontSize = 14.sp, color = if (origenLatLng != null) Color(0xFF1A1A1A) else BusGray, fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Box(modifier = Modifier.padding(start = 28.dp).width(2.dp).height(6.dp).background(Color(0xFFBDBDBD)))
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onSeleccionarDestino() },
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                                colors = CardDefaults.cardColors(containerColor = BusBg), elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Filled.Place, null, tint = BusRed, modifier = Modifier.size(22.dp))
                                    Column {
                                        Text("DESTINO", fontSize = 10.sp, color = BusGray, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                        Text(
                                            if (destinoLatLng != null) "${String.format("%.4f", destinoLatLng.latitude)}, ${String.format("%.4f", destinoLatLng.longitude)}"
                                            else "Toca para marcar destino",
                                            fontSize = 14.sp, color = if (destinoLatLng != null) Color(0xFF1A1A1A) else BusGray, fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Text("Preferencia:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreferenciaCard(Icons.Filled.AccessTime, "Menor tiempo", "Llega más rápido a tu destino", preferencia == "tiempo") { preferencia = "tiempo" }
                            PreferenciaCard(Icons.Filled.MonetizationOn, "Menor costo", "La ruta más económica", preferencia == "costo") { preferencia = "costo" }
                            PreferenciaCard(Icons.Filled.SwapCalls, "Menos transbordos", "Ruta más directa, menos caminata", preferencia == "transbordos") { preferencia = "transbordos" }
                        }
                        Text(
                            when (preferencia) {
                                "tiempo"      -> "Optimizando por: menor tiempo de viaje"
                                "costo"       -> "Optimizando por: menor costo (S/1.30 por combi)"
                                "transbordos" -> "Optimizando por: menos transbordos posibles"
                                else -> ""
                            }, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = BusRed
                        )
                        Text("Información adicional (opcional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        OutlinedTextField(
                            value = consultaExtra, onValueChange = { consultaExtra = it },
                            placeholder = { Text("Ej: Quiero pasar por el mercado San Camilo", fontSize = 13.sp, color = BusGray) },
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BusRed, focusedLabelColor = BusRed, cursorColor = BusRed)
                        )
                    }

                    when (val state = busquedaState) {
                        is BusquedaState.Loading -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = BusRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("La IA está analizando las rutas...", color = BusGray, fontSize = 14.sp)
                            }
                        }
                        is BusquedaState.Success -> {
                            ResultadoRutaTimeline(rutaParseada, state.respuesta, rutaColores, rutaSentidos)
                        }
                        is BusquedaState.Error -> {
                            Text(state.mensaje, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            OutlinedButton(
                                onClick = { busquedaViewModel.resetState() },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, BusRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BusRed)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Intentar de nuevo", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    if (origenLatLng != null && destinoLatLng != null) {
                                        busquedaViewModel.buscarRutaPorCoordenadas(
                                            origenLat = origenLatLng.latitude, origenLng = origenLatLng.longitude,
                                            destinoLat = destinoLatLng.latitude, destinoLng = destinoLatLng.longitude,
                                            preferencia = preferencia, consultaExtra = consultaExtra
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BusRed),
                                enabled = origenLatLng != null && destinoLatLng != null
                            ) { Text("Buscar ruta óptima", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        }
                    }
                }
            }
        }
    }
}

// ── Timeline de resultados ────────────────────────────────────────────────────
@Composable
private fun ResultadoRutaTimeline(
    rutaParseada: RutaParseada,
    respuestaOriginal: String,
    rutaColores: Map<String, String>,
    rutaSentidos: Map<String, String> = emptyMap()
) {
    if (rutaParseada.segmentos.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BusLightRed), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🤖 Recomendación de la IA:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BusRed)
                Text(respuestaOriginal, fontSize = 13.sp, color = Color(0xFF1A1A1A))
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (rutaParseada.codigos.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BusBg),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rutaParseada.codigos.forEachIndexed { idx, codigo ->
                        if (idx > 0) Spacer(modifier = Modifier.width(20.dp))
                        val color = rutaColores[codigo]?.let { parseHexColor(it) } ?: BusRed
                        val sentido = rutaSentidos[codigo]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(22.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            Text(codigo, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                            if (sentido != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (sentido == "VUELTA") Color(0xFFE3F2FD)
                                            else Color(0xFFFFEBEE)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (sentido == "VUELTA") "⬅ VUELTA" else "➡ IDA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sentido == "VUELTA") Color(0xFF1565C0) else BusRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Pastillas de tiempo y costo ──────────────────────────────────
        val numCombis = rutaParseada.segmentos.size
        val costoCalculado = "S/%.2f".format(numCombis * 1.30)
        val labelCombis = if (numCombis == 1) "1 combi" else "$numCombis combis"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pastilla tiempo (solo si la IA lo devolvió)
            if (rutaParseada.estimacionTiempo.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = rutaParseada.estimacionTiempo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            // Pastilla costo — siempre exacto, calculado desde segmentos
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFF8E1))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Filled.MonetizationOn,
                    contentDescription = null,
                    tint = Color(0xFFF57F17),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$costoCalculado · $labelCombis",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF57F17)
                )
            }
        }

        rutaParseada.segmentos.forEachIndexed { index, segmento ->
            val esUltimo = index == rutaParseada.segmentos.lastIndex
            SegmentoRutaCard(segmento, esUltimo, hayTransbordo = !esUltimo, rutaColores)
        }
    }
}

// ── Card de segmento ─────────────────────────────────────────────────────────
@Composable
private fun SegmentoRutaCard(
    segmento: SegmentoRuta,
    esUltimo: Boolean,
    hayTransbordo: Boolean,
    rutaColores: Map<String, String>
) {
    val rutaColor      = rutaColores[segmento.codigoRuta]?.let { parseHexColor(it) } ?: BusDarkRed
    val rutaColorLight = rutaColor.copy(alpha = 0.12f)

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(rutaColorLight))
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(rutaColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (!esUltimo) {
                    Box(modifier = Modifier.width(3.dp).height(16.dp).background(rutaColor))
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFFF6F00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⇄", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(3.dp).height(16.dp).background(rutaColor))
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = if (esUltimo) 0.dp else 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(BusRed), contentAlignment = Alignment.Center) {
                        Text("${segmento.numero}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (segmento.codigoRuta.isNotEmpty()) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(rutaColor).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(segmento.codigoRuta, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.RadioButtonUnchecked, null, tint = rutaColor, modifier = Modifier.size(18.dp).offset(y = 2.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(segmento.abordarTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A), lineHeight = 18.sp)
                        if (segmento.abordarDetalle.isNotEmpty())
                            Text(segmento.abordarDetalle, fontSize = 12.sp, color = BusGray, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                if (segmento.bajarTexto.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Place, null, tint = BusRed, modifier = Modifier.size(18.dp).offset(y = 2.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(segmento.bajarTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A), lineHeight = 18.sp)
                            if (segmento.bajarDetalle.isNotEmpty())
                                Text(segmento.bajarDetalle, fontSize = 12.sp, color = BusGray, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Card de preferencia ───────────────────────────────────────────────────────
@Composable
private fun PreferenciaCard(
    icon: ImageVector, titulo: String, subtitulo: String, selected: Boolean, onClick: () -> Unit
) {
    val bgColor      = if (selected) BusRed else BusBg
    val contentColor = if (selected) Color.White else Color(0xFF1A1A1A)
    val subColor     = if (selected) Color.White.copy(alpha = 0.85f) else BusGray
    val iconBg       = if (selected) Color.White.copy(alpha = 0.2f) else Color(0xFFE0E0E0)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = contentColor)
                Text(subtitulo, fontSize = 12.sp, color = subColor)
            }
        }
    }
}