package com.sitp.arequipa.presentation.busqueda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.di.provideViewModelFactory
import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.Itinerario
import com.sitp.arequipa.domain.model.LugarEncontrado
import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.PuntoInterpretado
import com.sitp.arequipa.presentation.historial.HistorialViewModel
import kotlin.math.roundToInt

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

    val pasosRegex = Regex("""(\d+)\.\s*(.+?)(?=\r?\n\s*\d+\.|\r?\n\s*\*?\*?estimaci[oó]n|\z)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    val segmentosSinFusionar = pasosRegex.findAll(respuesta).toList().mapNotNull { match ->
        val texto = match.groupValues[2].trim().replace("\n", " ").replace("  ", " ")
        val contieneRuta = listOf("combi","bus","tome","transbordo","bájese","bajese","camine")
            .any { texto.contains(it, ignoreCase = true) }
        // Descartar lineas de costo o estimacion que no son pasos de ruta
        val esCostoOEstimacion = texto.contains("S/") ||
                texto.contains("costo", ignoreCase = true) ||
                texto.contains("estimaci", ignoreCase = true) ||
                texto.matches(Regex(".*\\d+\\s*[xX]\\s*S/.*")) ||
                texto.matches(Regex("^[\\d.,]+\\s*\\(.*\\)$"))
        if (!contieneRuta || esCostoOEstimacion) return@mapNotNull null

        var codigo = Regex("""(?:combi|bus|ruta)\s+([A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.get(1) ?: ""

        if (codigo.isEmpty() || !codigos.any { it.equals(codigo, ignoreCase = true) }) {
            val codigoEncontrado = codigos.firstOrNull { cod ->
                texto.contains(Regex("""\b${Regex.escape(cod)}\b""", RegexOption.IGNORE_CASE))
            }
            if (codigoEncontrado != null) {
                codigo = codigoEncontrado
            }
        }

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
        // El texto puede traer ya el verbo y/o la preposición ("en el destino", "bajese en Av. X"):
        // se quitan y se vuelve a poner "Bájese en" una sola vez (evita "Bájese en en el destino").
        val bajarTexto = if (bajarBase.isEmpty()) "" else {
            val lugarBajada = bajarBase
                .replaceFirst(Regex("""^(bájese|bajese|baje)\s+""", RegexOption.IGNORE_CASE), "")
                .replaceFirst(Regex("""^(en\s+)+""", RegexOption.IGNORE_CASE), "")
            "Bájese en $lugarBajada"
        }

        SegmentoRuta(0, codigo, abordarTexto,
            if (abordarDetalle.isNotEmpty()) "Cerca de $abordarDetalle" else "",
            bajarTexto,
            if (bajarDetalle.isNotEmpty()) "Cerca de $bajarDetalle" else "")
    }

    val segmentosFusionados = segmentosSinFusionar
        .fold(mutableListOf<SegmentoRuta>()) { acc, segmento ->
            val ultimo = acc.lastOrNull()
            if (ultimo != null && ultimo.codigoRuta.isNotEmpty() && ultimo.codigoRuta == segmento.codigoRuta) {
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

    val tiempo = Regex("""ESTIMACI[OÓ]N[:\s]*([^,\n]*(?:minutos|min|hora)[^,\n]*)""", RegexOption.IGNORE_CASE)
        .find(respuesta)?.groupValues?.get(1)?.trim() ?: ""
    val costo  = Regex("""S/\s*[\d.]+(?:\s*\([^)]*\))?""", RegexOption.IGNORE_CASE)
        .find(respuesta)?.value?.trim() ?: ""

    return RutaParseada(codigos.distinct(), segmentosFusionados, tiempo, costo)
}

// ─────────────────────────────────────────────────────────────────────────────
/**
 * Panel de búsqueda (ADR-010 y ADR-011). Los pines se ubican moviendo el mapa bajo un pin fijo; también se
 * pueden proponer con «Buscar lugar» (sin internet) o con el chat con IA, que solo interpreta la frase.
 * Las rutas las calcula la app y las opciones aparecen a medida que el motor las encuentra.
 */
@Composable
fun BusquedaSheet(
    origenPunto: Coordenada?,
    destinoPunto: Coordenada?,
    ubicacionUsuario: Coordenada?,
    onDismiss: () -> Unit,
    onSeleccionarOrigen: () -> Unit,
    onSeleccionarDestino: () -> Unit,
    onElegirLugar: (lugar: LugarEncontrado, esOrigen: Boolean) -> Unit,
    onUbicarConIA: (origen: Coordenada?, destino: Coordenada?) -> Unit,
    onItinerario: (Itinerario?) -> Unit,
    onNuevaOptimizacion: () -> Unit,
    preferenciaInicial: String? = null,
    rutaColores: Map<String, String> = emptyMap(),
    rutaEtiquetas: Map<String, String> = emptyMap(),
    rutaCombis: Map<String, String> = emptyMap(),
    busquedaViewModel: BusquedaViewModel = viewModel(factory = provideViewModelFactory()),
    historialViewModel: HistorialViewModel = viewModel(factory = provideViewModelFactory()),
    ubicacionViewModel: UbicacionViewModel = viewModel(factory = provideViewModelFactory())
) {
    var modo by remember { mutableStateOf(ModoBusqueda.desdeTexto(preferenciaInicial)) }
    var consultaIA by remember { mutableStateOf("") }
    var textoLugar by remember { mutableStateOf("") }
    val busquedaState by busquedaViewModel.busquedaState.collectAsState()
    val lugares by busquedaViewModel.lugares.collectAsState()
    val interpretacion by busquedaViewModel.interpretacion.collectAsState()
    val nombres by busquedaViewModel.nombres.collectAsState()

    // Nombres de calle de los pines y crédito de la fuente de datos (sin saber qué proveedor es)
    val nombreOrigen by ubicacionViewModel.origen.collectAsState()
    val nombreDestino by ubicacionViewModel.destino.collectAsState()
    val atribucionDatos by ubicacionViewModel.atribucion.collectAsState()
    LaunchedEffect(origenPunto) { ubicacionViewModel.nombrarOrigen(origenPunto?.lat, origenPunto?.lng) }
    LaunchedEffect(destinoPunto) { ubicacionViewModel.nombrarDestino(destinoPunto?.lat, destinoPunto?.lng) }

    var minimizado by remember { mutableStateOf(false) }

    // Resultados: modo que se está viendo y opción elegida
    var modoVista by remember { mutableStateOf(modo) }
    var opcionElegida by remember { mutableIntStateOf(0) }
    val resultados = busquedaState as? BusquedaState.Resultados
    val opcionesVista = resultados?.opciones?.de(modoVista).orEmpty()
    val itinerario = opcionesVista.getOrNull(opcionElegida) ?: opcionesVista.firstOrNull()
    LaunchedEffect(itinerario) { onItinerario(itinerario) }
    LaunchedEffect(busquedaState is BusquedaState.Calculando) {
        if (busquedaState is BusquedaState.Calculando) { modoVista = modo; opcionElegida = 0 }
    }

    // La preferencia que entendió la IA («lo más barato») cambia el modo
    LaunchedEffect(interpretacion) {
        (interpretacion as? InterpretacionState.Lista)?.resultado?.preferencia?.let { modo = it }
    }

    // Historial: una vez por búsqueda, al terminar y con los nombres de calle ya resueltos
    var yaGuardado by remember { mutableStateOf(false) }
    LaunchedEffect(busquedaState, nombres) {
        val r = busquedaState
        if (r is BusquedaState.Idle || r is BusquedaState.Calculando) yaGuardado = false
        if (r is BusquedaState.Resultados && r.opciones.completa && !yaGuardado && origenPunto != null && destinoPunto != null) {
            val mejor = r.opciones.de(r.modo).firstOrNull() ?: return@LaunchedEffect
            if (mejor.tramosCombi.any { it.desde !in nombres || it.hasta !in nombres }) return@LaunchedEffect
            yaGuardado = true
            historialViewModel.guardarBusqueda(
                // Se guardan coordenadas (no nombres): "Repetir búsqueda" y los favoritos las vuelven a leer
                origen = origenPunto.comoTexto(),
                destino = destinoPunto.comoTexto(),
                preferencia = r.modo.comoTexto(),
                respuestaIA = TextoItinerario.aTextoHistorial(mejor, nombres)
            )
        }
    }

    var primeraVez by remember { mutableStateOf(true) }
    LaunchedEffect(origenPunto, destinoPunto) {
        if (primeraVez) primeraVez = false else { busquedaViewModel.resetState(); onItinerario(null) }
    }

    val combiDe: (String) -> String = { codigo ->
        rutaCombis.entries.firstOrNull { it.key.trim().equals(codigo.trim(), ignoreCase = true) }?.value ?: codigo
    }
    val mostrandoResultados = busquedaState is BusquedaState.Resultados || busquedaState is BusquedaState.Calculando

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minimizado) Modifier.wrapContentHeight() else Modifier.fillMaxHeight(0.6f)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color.White,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Handle + botón minimizar ──────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                Box(
                    modifier = Modifier.align(Alignment.Center).width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE0E0E0))
                )
                IconButton(
                    onClick = { minimizado = !minimizado },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(32.dp)
                ) {
                    Icon(
                        imageVector = if (minimizado) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (minimizado) "Expandir" else "Minimizar",
                        tint = BusGray, modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (minimizado) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { minimizado = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(BusLightRed),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Place, null, tint = BusRed, modifier = Modifier.size(18.dp)) }
                    Text(
                        text = when (val s = busquedaState) {
                            is BusquedaState.Resultados -> if (s.opciones.vacia) "Sin rutas — toca para ver" else "${s.opciones.de(modoVista).size} opciones — toca para ver"
                            is BusquedaState.Calculando -> "Calculando rutas..."
                            else -> "Buscar ruta — toca para abrir"
                        },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A)
                    )
                }
            }

            AnimatedVisibility(
                visible = !minimizado,
                enter = expandVertically(animationSpec = tween(250)),
                exit = shrinkVertically(animationSpec = tween(200))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (mostrandoResultados) {
                                    primeraVez = true
                                    busquedaViewModel.resetState()
                                    onItinerario(null)
                                    onNuevaOptimizacion()
                                } else onDismiss()
                            },
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = BusDarkRed, modifier = Modifier.size(22.dp)) }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BusLightRed),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Place, null, tint = BusRed, modifier = Modifier.size(20.dp)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar ruta", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    }

                    if (!mostrandoResultados) {
                        // ── Origen y destino: se ubican moviendo el mapa bajo un pin fijo ──
                        PuntosOrigenDestino(origenPunto, destinoPunto, nombreOrigen, nombreDestino, onSeleccionarOrigen, onSeleccionarDestino)

                        // ── Buscar lugar (catálogo local, sin IA, sin internet) ──
                        Text("Buscar lugar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        OutlinedTextField(
                            value = textoLugar,
                            onValueChange = {
                                textoLugar = it
                                busquedaViewModel.buscarLugares(it, ubicacionUsuario ?: origenPunto)
                            },
                            placeholder = { Text("Ej: mercado San Camilo", fontSize = 13.sp, color = BusGray) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = BusGray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BusRed, cursorColor = BusRed)
                        )
                        if (lugares.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                lugares.forEach { lugar ->
                                    FilaLugar(
                                        lugar = lugar,
                                        onOrigen = { onElegirLugar(lugar, true); textoLugar = ""; busquedaViewModel.limpiarLugares() },
                                        onDestino = { onElegirLugar(lugar, false); textoLugar = ""; busquedaViewModel.limpiarLugares() }
                                    )
                                }
                            }
                        } else if (textoLugar.trim().length >= 3) {
                            Text("Sin coincidencias en el mapa. Prueba con otro nombre o mueve el mapa.", fontSize = 12.sp, color = BusGray)
                        }

                        // ── Chat con IA: solo interpreta la frase (necesita internet) ──
                        Text("Pregúntale a la IA (necesita internet)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        OutlinedTextField(
                            value = consultaIA, onValueChange = { consultaIA = it },
                            placeholder = { Text("Ej: quiero ir de la UNSA al mercado San Camilo", fontSize = 13.sp, color = BusGray) },
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = { busquedaViewModel.interpretar(consultaIA, ubicacionUsuario) },
                                    enabled = consultaIA.isNotBlank() && interpretacion !is InterpretacionState.Interpretando
                                ) { Icon(Icons.Filled.AutoAwesome, "Interpretar", tint = if (consultaIA.isNotBlank()) BusRed else BusGray) }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BusRed, cursorColor = BusRed)
                        )
                        ResultadoInterpretacionVista(
                            estado = interpretacion,
                            ubicacionUsuario = ubicacionUsuario,
                            onElegirLugar = onElegirLugar,
                            onUbicar = { o, d -> onUbicarConIA(o, d); busquedaViewModel.limpiarInterpretacion() },
                            onCerrar = { busquedaViewModel.limpiarInterpretacion() }
                        )

                        Text("Preferencia:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreferenciaCard(Icons.Filled.AccessTime, "Menor tiempo", "Llega más rápido a tu destino", modo == ModoBusqueda.TIEMPO) { modo = ModoBusqueda.TIEMPO }
                            PreferenciaCard(Icons.Filled.MonetizationOn, "Menor costo", "Menos combis (S/1.30 cada una)", modo == ModoBusqueda.COSTO) { modo = ModoBusqueda.COSTO }
                        }
                    }

                    when (val state = busquedaState) {
                        is BusquedaState.Calculando -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = BusRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Calculando rutas...", color = BusGray, fontSize = 14.sp)
                            }
                        }
                        is BusquedaState.Resultados -> {
                            val opciones = state.opciones
                            if (opciones.vacia && opciones.completa) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = BusLightRed), elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Text(
                                        "No encontramos combis que conecten estos puntos a una distancia caminable. Prueba moviendo los pines a una avenida cercana.",
                                        fontSize = 13.sp, color = Color(0xFF1A1A1A), modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                SelectorModo(modoVista) { modoVista = it; opcionElegida = 0 }
                                SelectorOpciones(opcionesVista, opcionElegida) { opcionElegida = it }
                                if (!opciones.completa) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(color = BusGray, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                        Text("Buscando más opciones...", fontSize = 12.sp, color = BusGray)
                                    }
                                }
                                itinerario?.let { it ->
                                    ResultadoRutaTimeline(
                                        TextoItinerario.aRutaParseada(it, nombres, combiDe), "",
                                        rutaColores, TextoItinerario.sentidos(it), rutaEtiquetas, rutaCombis
                                    )
                                }
                            }
                            atribucionDatos?.let { CreditoFuenteDatos(it) }
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
                        is BusquedaState.Idle -> {
                            Button(
                                onClick = {
                                    if (origenPunto != null && destinoPunto != null) busquedaViewModel.buscarRuta(origenPunto, destinoPunto, modo)
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BusRed),
                                enabled = origenPunto != null && destinoPunto != null
                            ) { Text("Buscar rutas", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        }
                    }
                }
            }
        }
    }
}

// ── Origen y destino ─────────────────────────────────────────────────────────
@Composable
private fun PuntosOrigenDestino(
    origenPunto: Coordenada?,
    destinoPunto: Coordenada?,
    nombreOrigen: UbicacionViewModel.NombrePunto?,
    nombreDestino: UbicacionViewModel.NombrePunto?,
    onSeleccionarOrigen: () -> Unit,
    onSeleccionarDestino: () -> Unit
) {
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
                        if (origenPunto != null) textoPunto(origenPunto, nombreOrigen) else "Toca y mueve el mapa para ubicarlo",
                        fontSize = 14.sp, color = if (origenPunto != null) Color(0xFF1A1A1A) else BusGray, fontWeight = FontWeight.Medium
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
                        if (destinoPunto != null) textoPunto(destinoPunto, nombreDestino) else "Toca y mueve el mapa para ubicarlo",
                        fontSize = 14.sp, color = if (destinoPunto != null) Color(0xFF1A1A1A) else BusGray, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Un resultado de «Buscar lugar» ───────────────────────────────────────────
@Composable
private fun FilaLugar(lugar: LugarEncontrado, onOrigen: () -> Unit, onDestino: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BusBg), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lugar.nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                if (lugar.detalle.isNotEmpty()) Text(lugar.detalle, fontSize = 11.sp, color = BusGray)
            }
            TextButton(onClick = onOrigen, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Origen", color = BusGreen, fontSize = 12.sp) }
            TextButton(onClick = onDestino, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Destino", color = BusRed, fontSize = 12.sp) }
        }
    }
}

// ── Lo que entendió la IA: candidatos para cada pin ──────────────────────────
@Composable
private fun ResultadoInterpretacionVista(
    estado: InterpretacionState,
    ubicacionUsuario: Coordenada?,
    onElegirLugar: (LugarEncontrado, Boolean) -> Unit,
    onUbicar: (Coordenada?, Coordenada?) -> Unit,
    onCerrar: () -> Unit
) {
    when (estado) {
        is InterpretacionState.Interpretando -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(color = BusRed, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Text("La IA está leyendo tu consulta...", fontSize = 12.sp, color = BusGray)
        }
        is InterpretacionState.Error -> Text(estado.mensaje, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        is InterpretacionState.Lista -> {
            val r = estado.resultado
            fun primero(p: PuntoInterpretado): Coordenada? = when (p) {
                is PuntoInterpretado.MiUbicacion -> ubicacionUsuario
                is PuntoInterpretado.Candidatos -> p.lugares.firstOrNull()?.coordenada
                else -> null
            }
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)), elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Esto entendí (confirma cada punto en el mapa):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                    PuntoInterpretadoFila("Origen", r.origen, true, onElegirLugar)
                    PuntoInterpretadoFila("Destino", r.destino, false, onElegirLugar)
                    r.avisos.forEach { Text(it, fontSize = 11.sp, color = Color(0xFF8D6E00)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUbicar(primero(r.origen), primero(r.destino)) },
                            colors = ButtonDefaults.buttonColors(containerColor = BusRed),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Ubicar en el mapa", fontSize = 13.sp) }
                        TextButton(onClick = onCerrar) { Text("Cerrar", color = BusGray, fontSize = 13.sp) }
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun PuntoInterpretadoFila(titulo: String, punto: PuntoInterpretado, esOrigen: Boolean, onElegirLugar: (LugarEncontrado, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo.uppercase(), fontSize = 10.sp, color = BusGray, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        when (punto) {
            is PuntoInterpretado.NoIndicado -> Text("No lo mencionaste: ubícalo moviendo el mapa.", fontSize = 12.sp, color = BusGray)
            is PuntoInterpretado.MiUbicacion -> Text("Tu ubicación actual", fontSize = 13.sp, color = Color(0xFF1A1A1A))
            is PuntoInterpretado.Candidatos -> {
                if (punto.lugares.isEmpty()) Text("«${punto.texto}»: no encontrado", fontSize = 12.sp, color = BusGray)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    punto.lugares.forEachIndexed { i, lugar ->
                        AssistChip(
                            onClick = { onElegirLugar(lugar, esOrigen) },
                            label = { Text(if (lugar.detalle.isEmpty()) lugar.nombre else "${lugar.nombre} · ${lugar.detalle}", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = if (i == 0) Color.White else BusBg)
                        )
                    }
                }
            }
        }
    }
}

// ── Pestañas «Menos tiempo | Menos costo» ────────────────────────────────────
@Composable
private fun SelectorModo(modo: ModoBusqueda, onCambio: (ModoBusqueda) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ModoBusqueda.TIEMPO to "Menos tiempo", ModoBusqueda.COSTO to "Menos costo").forEach { (m, texto) ->
            val elegido = m == modo
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (elegido) BusRed else BusBg).clickable { onCambio(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (elegido) Color.White else Color(0xFF1A1A1A)) }
        }
    }
}

// ── Opciones 1–5 del modo elegido ────────────────────────────────────────────
@Composable
private fun SelectorOpciones(opciones: List<Itinerario>, elegida: Int, onElegir: (Int) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, op ->
            FilterChip(
                selected = i == elegida,
                onClick = { onElegir(i) },
                label = {
                    Text(
                        "${i + 1} · ${op.minutosEstimado.roundToInt()} min · ${op.numeroCombis} ${if (op.numeroCombis == 1) "combi" else "combis"}",
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BusLightRed, selectedLabelColor = BusDarkRed)
            )
        }
    }
}

// ── Nombre del pin ───────────────────────────────────────────────────────────
/** Nombre de calle del pin si ya se calculó para estas coordenadas; mientras tanto, las coordenadas. */
private fun textoPunto(punto: Coordenada, nombre: UbicacionViewModel.NombrePunto?): String =
    nombre?.takeIf { it.lat == punto.lat && it.lng == punto.lng }?.nombre
        ?: punto.comoTexto()

// ── Crédito de la fuente de los nombres de calles (lo exige su licencia, ver ADR-002) ──
@Composable
private fun CreditoFuenteDatos(atribucion: AtribucionDatos) {
    val uriHandler = LocalUriHandler.current
    val enlace = atribucion.enlace
    Text(
        text = "Nombres de calles: ${atribucion.texto}",
        color = BusGray,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enlace != null) Modifier.clickable { uriHandler.openUri(enlace) } else Modifier)
            .padding(vertical = 4.dp)
    )
}

// ── Timeline de resultados ────────────────────────────────────────────────────
@Composable
private fun ResultadoRutaTimeline(
    rutaParseada: RutaParseada,
    respuestaOriginal: String,
    rutaColores: Map<String, String>,
    rutaSentidos: Map<String, String> = emptyMap(),
    rutaEtiquetas: Map<String, String> = emptyMap(),
    rutaCombis: Map<String, String> = emptyMap()
) {
    if (rutaParseada.segmentos.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BusLightRed), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Detalle del viaje:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BusRed)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rutaParseada.codigos.forEachIndexed { idx, codigo ->
                        if (idx > 0) Spacer(modifier = Modifier.width(20.dp))
                        val color = rutaColores.entries.firstOrNull { it.key.trim().equals(codigo.trim(), ignoreCase = true) }?.value?.let { parseHexColor(it) } ?: BusRed
                        val sentido = rutaSentidos.entries.firstOrNull { it.key.trim().equals(codigo.trim(), ignoreCase = true) }?.value
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(22.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            val etiqueta = rutaEtiquetas.entries.firstOrNull { it.key.trim().equals(codigo.trim(), ignoreCase = true) }?.value
                            val combi = rutaCombis.entries.firstOrNull { it.key.trim().equals(codigo.trim(), ignoreCase = true) }?.value ?: codigo
                            val displayCodigo = if (!etiqueta.isNullOrEmpty()) "$combi \"$etiqueta\"" else combi
                            Text(displayCodigo, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
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
            SegmentoRutaCard(segmento, esUltimo, hayTransbordo = !esUltimo, rutaColores, rutaEtiquetas, rutaCombis)
        }
    }
}

// ── Card de segmento ─────────────────────────────────────────────────────────
@Composable
private fun SegmentoRutaCard(
    segmento: SegmentoRuta,
    esUltimo: Boolean,
    hayTransbordo: Boolean,
    rutaColores: Map<String, String>,
    rutaEtiquetas: Map<String, String> = emptyMap(),
    rutaCombis: Map<String, String> = emptyMap()
) {
    val rutaColor      = rutaColores.entries.firstOrNull { it.key.trim().equals(segmento.codigoRuta.trim(), ignoreCase = true) }?.value?.let { parseHexColor(it) } ?: BusDarkRed
    val rutaColorLight = rutaColor.copy(alpha = 0.12f)
    // El usuario ve la combi (LA), no el codigo interno (LA-C8)
    val combiSegmento = rutaCombis.entries.firstOrNull { it.key.trim().equals(segmento.codigoRuta.trim(), ignoreCase = true) }?.value ?: segmento.codigoRuta
    val abordarMostrado = if (segmento.codigoRuta.isNotEmpty()) segmento.abordarTexto.replace(segmento.codigoRuta, combiSegmento, ignoreCase = true) else segmento.abordarTexto

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
                        val etiquetaSegmento = rutaEtiquetas.entries.firstOrNull { it.key.trim().equals(segmento.codigoRuta.trim(), ignoreCase = true) }?.value
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(rutaColor).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(combiSegmento, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!etiquetaSegmento.isNullOrEmpty()) {
                            Text("\"$etiquetaSegmento\"", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.RadioButtonUnchecked, null, tint = rutaColor, modifier = Modifier.size(18.dp).offset(y = 2.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(abordarMostrado, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A), lineHeight = 18.sp)
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
