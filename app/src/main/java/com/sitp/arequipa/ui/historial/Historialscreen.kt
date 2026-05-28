package com.sitp.arequipa.ui.historial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.viewmodel.BusquedaHistorial
import com.sitp.arequipa.viewmodel.HistorialViewModel
import com.sitp.arequipa.viewmodel.RutaFavorita
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    onBack: () -> Unit,
    onRepetirBusqueda: (String, String, String) -> Unit = { _, _, _ -> },
    historialViewModel: HistorialViewModel = viewModel()
) {
    val historial by historialViewModel.historial.collectAsState()
    val favoritos by historialViewModel.favoritos.collectAsState()
    val loading by historialViewModel.loading.collectAsState()
    val favoritoAutoGuardado by historialViewModel.favoritoAutoGuardado.collectAsState()
    var tabSeleccionado by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        historialViewModel.cargarHistorial()
        historialViewModel.cargarFavoritos()
    }

    LaunchedEffect(favoritoAutoGuardado) {
        if (favoritoAutoGuardado) {
            snackbarHostState.showSnackbar("⭐ ¡Ruta guardada como favorita automáticamente!")
            historialViewModel.resetFavoritoAutoGuardado()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi actividad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabSeleccionado) {
                Tab(
                    selected = tabSeleccionado == 0,
                    onClick = { tabSeleccionado = 0 },
                    text = { Text("📋 Historial") }
                )
                Tab(
                    selected = tabSeleccionado == 1,
                    onClick = { tabSeleccionado = 1 },
                    text = { Text("⭐ Favoritos") }
                )
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (tabSeleccionado) {
                    0 -> HistorialTab(
                        historial = historial,
                        onEliminar = { historialViewModel.eliminarBusqueda(it) },
                        onRepetir = onRepetirBusqueda
                    )
                    1 -> FavoritosTab(
                        favoritos = favoritos,
                        onEliminar = { historialViewModel.eliminarFavorito(it) },
                        onRepetir = onRepetirBusqueda
                    )
                }
            }
        }
    }
}

@Composable
fun HistorialTab(
    historial: List<BusquedaHistorial>,
    onEliminar: (String) -> Unit,
    onRepetir: (String, String, String) -> Unit
) {
    if (historial.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Aún no tienes búsquedas.\n¡Empieza a explorar las rutas de Arequipa!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(historial, key = { it.id }) { busqueda ->
            BusquedaCard(
                busqueda = busqueda,
                onEliminar = { onEliminar(busqueda.id) },
                onRepetir = { onRepetir(busqueda.origen, busqueda.destino, busqueda.preferencia) }
            )
        }
    }
}

@Composable
fun BusquedaCard(
    busqueda: BusquedaHistorial,
    onEliminar: () -> Unit,
    onRepetir: () -> Unit
) {
    val fechaFormateada = busqueda.fecha?.toDate()?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
    } ?: ""

    val preferenciaTexto = when (busqueda.preferencia) {
        "tiempo" -> "⏱️ Más rápido"
        "costo" -> "💰 Más económico"
        "transbordos" -> "🔀 Menos transbordos"
        else -> busqueda.preferencia
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📍 ${busqueda.origen}", style = MaterialTheme.typography.bodyMedium)
                    Text("🏁 ${busqueda.destino}", style = MaterialTheme.typography.bodyMedium)
                    Text(preferenciaTexto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(fechaFormateada, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEliminar) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onRepetir, modifier = Modifier.fillMaxWidth()) {
                Text("Repetir búsqueda")
            }
        }
    }
}

@Composable
fun FavoritosTab(
    favoritos: List<RutaFavorita>,
    onEliminar: (String) -> Unit,
    onRepetir: (String, String, String) -> Unit
) {
    if (favoritos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No tienes rutas favoritas aún.\nLas rutas que busques 3 veces\nse guardarán automáticamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(favoritos, key = { it.id }) { favorito ->
            FavoritoCard(
                favorito = favorito,
                onEliminar = { onEliminar(favorito.id) },
                onRepetir = { onRepetir(favorito.origen, favorito.destino, "tiempo") }
            )
        }
    }
}

@Composable
fun FavoritoCard(
    favorito: RutaFavorita,
    onEliminar: () -> Unit,
    onRepetir: () -> Unit
) {
    val fechaFormateada = favorito.fechaUltimoUso?.toDate()?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
    } ?: ""

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(favorito.nombre, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("📍 ${favorito.origen}", style = MaterialTheme.typography.bodyMedium)
                    Text("🏁 ${favorito.destino}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Usado ${favorito.frecuencia} veces · Último: $fechaFormateada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEliminar) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onRepetir, modifier = Modifier.fillMaxWidth()) {
                Text("Buscar esta ruta")
            }
        }
    }
}