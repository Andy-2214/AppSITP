package com.sitp.arequipa.ui.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val scope = rememberCoroutineScope()

    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var genero by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var distrito by remember { mutableStateOf("") }
    var editandoNombre by remember { mutableStateOf(false) }
    var nuevoNombre by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }

    // Cargar datos del usuario
    LaunchedEffect(Unit) {
        user?.uid?.let { uid ->
            val doc = db.collection("usuarios").document(uid).get().await()
            nombre = doc.getString("nombre") ?: ""
            genero = doc.getString("genero") ?: ""
            edad = doc.getLong("edad")?.toString() ?: ""
            distrito = doc.getString("distrito") ?: ""
            nuevoNombre = nombre
        }
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
                                scope.launch {
                                    user?.uid?.let { uid ->
                                        db.collection("usuarios").document(uid)
                                            .update("nombre", nuevoNombre).await()
                                        nombre = nuevoNombre
                                        mensaje = "✅ Nombre actualizado"
                                    }
                                    editandoNombre = false
                                }
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
                            Text(nombre, style = MaterialTheme.typography.bodyLarge)
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
                    Text(email, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Género (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Género", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (genero.isEmpty()) "No especificado" else genero,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Edad (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edad", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (edad.isEmpty()) "No especificada" else "$edad años",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Distrito (no editable)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Distrito", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (distrito.isEmpty()) "No especificado" else distrito,
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