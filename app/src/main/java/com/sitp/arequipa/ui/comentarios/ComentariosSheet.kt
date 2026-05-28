package com.sitp.arequipa.ui.comentarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.sitp.arequipa.viewmodel.ComentarioState
import com.sitp.arequipa.viewmodel.ComentarioViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComentariosSheet(
    rutaId: String,
    rutaNombre: String,
    rutaCodigo: String,
    onDismiss: () -> Unit,
    comentarioViewModel: ComentarioViewModel = viewModel()
) {
    val comentarios by comentarioViewModel.comentarios.collectAsState()
    val comentarioState by comentarioViewModel.comentarioState.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    var textoComentario by remember { mutableStateOf("") }

    LaunchedEffect(rutaId) {
        comentarioViewModel.cargarComentarios(rutaId)
    }

    LaunchedEffect(comentarioState) {
        if (comentarioState is ComentarioState.Success) {
            textoComentario = ""
            comentarioViewModel.resetState()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "💬 Opiniones ciudadanas — $rutaNombre",
                style = MaterialTheme.typography.titleMedium
            )

            Divider()

            // Lista de comentarios aprobados
            if (comentarios.isEmpty()) {
                Text(
                    "No hay comentarios aún. ¡Sé el primero!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(comentarios) { comentario ->
                        val fecha = comentario.fecha?.toDate()?.let {
                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                        } ?: ""

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (comentario.destacado)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (comentario.destacado) {
                                    Text("📌 Destacado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(comentario.texto, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${comentario.nombreUsuario} · $fecha",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Divider()

            // Campo para nuevo comentario
            if (user != null) {
                Text("Deja tu opinión:", style = MaterialTheme.typography.labelLarge)

                OutlinedTextField(
                    value = textoComentario,
                    onValueChange = { textoComentario = it },
                    placeholder = { Text("Comparte tu experiencia con esta ruta...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                when (comentarioState) {
                    is ComentarioState.Error -> {
                        Text(
                            (comentarioState as ComentarioState.Error).mensaje,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is ComentarioState.Success -> {
                        Text(
                            "✅ Comentario enviado. Será visible tras la moderación.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> {}
                }

                Button(
                    onClick = {
                        comentarioViewModel.publicarComentario(
                            rutaId = rutaId,
                            rutaNombre = rutaNombre,
                            rutaCodigo = rutaCodigo,
                            texto = textoComentario

                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = textoComentario.isNotEmpty() &&
                            comentarioState !is ComentarioState.Loading
                ) {
                    if (comentarioState is ComentarioState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publicar comentario")
                    }
                }
            } else {
                Text(
                    "Inicia sesión para dejar un comentario",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}