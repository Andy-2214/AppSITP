package com.sitp.arequipa.presentation.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    onBack: () -> Unit,
    perfilViewModel: PerfilViewModel
) {
    val user by perfilViewModel.user.collectAsState()
    val mensaje by perfilViewModel.mensaje.collectAsState()

    var editandoNombre by remember { mutableStateOf(false) }
    var nuevoNombre by remember { mutableStateOf("") }

    LaunchedEffect(user) {
        user?.let { nuevoNombre = it.nombre }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nombre
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nombre", style = MaterialTheme.typography.labelMedium)
                    if (editandoNombre) {
                        OutlinedTextField(
                            value = nuevoNombre,
                            onValueChange = { nuevoNombre = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                perfilViewModel.updateNombre(nuevoNombre)
                                editandoNombre = false
                            }) { Text("Guardar") }
                            OutlinedButton(onClick = { editandoNombre = false }) {
                                Text("Cancelar")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user?.nombre ?: "", style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { editandoNombre = true }) {
                                Text("Editar")
                            }
                        }
                    }
                }
            }

            // Email (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Email", style = MaterialTheme.typography.labelMedium)
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Género (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Género", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (user?.genero.isNullOrEmpty()) "No especificado" else user!!.genero,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Edad (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edad", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (user?.edad == 0) "No especificada" else "${user?.edad} años",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Distrito (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Distrito", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (user?.distrito.isNullOrEmpty()) "No especificado" else user!!.distrito,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (mensaje.isNotEmpty()) {
                Text(mensaje, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
