package com.sitp.arequipa.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.viewmodel.HistorialViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.tasks.await

// ── Paleta del perfil ─────────────────────────────────────────────────────────
private val ProfRed    = Color(0xFFC62828)
private val ProfGray   = Color(0xFF9E9E9E)
private val ProfBg     = Color(0xFFF5F5F5)
private val ProfDark   = Color(0xFF212121)
private val ProfCard   = Color.White

// ── Símbolos y colores de género ──────────────────────────────────────────────
private fun generoSymbol(genero: String): Pair<String, Color> = when (
    genero.lowercase().trim()
) {
    "masculino", "hombre", "male", "m"         -> "♂" to Color(0xFF1565C0)
    "femenino", "mujer", "female", "f"          -> "♀" to Color(0xFFAD1457)
    "no binario", "no-binario", "nb",
    "non-binary", "otro", "other", "x"          -> "⚧" to Color(0xFF6A1B9A)
    else                                         -> ""  to Color.Transparent
}

@Composable
fun PerfilScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val db   = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    val historialViewModel: HistorialViewModel = viewModel()
    val historial by historialViewModel.historial.collectAsState()
    val scope = rememberCoroutineScope()

    var nombre    by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf(user?.email ?: "") }
    var genero    by remember { mutableStateOf("") }
    var edad      by remember { mutableStateOf("") }
    var distrito  by remember { mutableStateOf("") }
    var comentariosCount by remember { mutableIntStateOf(0) }

    // Estado de edición de nombre
    var editandoNombre by remember { mutableStateOf(false) }
    var nuevoNombre    by remember { mutableStateOf("") }

    // ── Cargar datos ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        historialViewModel.cargarHistorial()

        user?.uid?.let { uid ->
            // Datos del perfil
            val doc = db.collection("usuarios").document(uid).get().await()
            nombre   = doc.getString("nombre")       ?: ""
            nuevoNombre = nombre
            genero   = doc.getString("genero")       ?: ""
            edad     = doc.getLong("edad")?.toString() ?: ""
            distrito = doc.getString("distrito")     ?: ""

            // Conteo de comentarios del usuario
            val comsSnap = db.collection("comentarios")
                .whereEqualTo("usuarioId", uid)
                .get().await()
            comentariosCount = comsSnap.size()
        }
    }

    // ── Iniciales del avatar ──────────────────────────────────────────────
    val initials = nombre
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    val (generoSym, generoColor) = generoSymbol(genero)

    // ── UI ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ── Avatar ────────────────────────────────────────────────────────
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .shadow(4.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(ProfRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials.ifEmpty { "?" },
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
            }
            // Botón editar nombre (lápiz clickable)
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .size(30.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { editandoNombre = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Editar nombre",
                    tint = ProfRed,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Diálogo de edición de nombre ─────────────────────────
        if (editandoNombre) {
            AlertDialog(
                onDismissRequest = { editandoNombre = false },
                title = { Text("Cambiar nombre", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = nuevoNombre,
                        onValueChange = { nuevoNombre = it },
                        label = { Text("Nuevo nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ProfRed,
                            focusedLabelColor = ProfRed,
                            cursorColor = ProfRed
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = nuevoNombre.trim()
                            if (trimmed.isNotEmpty()) {
                                scope.launch {
                                    user?.uid?.let { uid ->
                                        db.collection("usuarios").document(uid)
                                            .update("nombre", trimmed).await()
                                        nombre = trimmed
                                    }
                                    editandoNombre = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ProfRed)
                    ) { Text("Guardar", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        nuevoNombre = nombre
                        editandoNombre = false
                    }) { Text("Cancelar", color = ProfGray) }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Nombre + género + email + distrito ────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Nombre + símbolo género
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = nombre.ifEmpty { "Usuario" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProfDark
                )
                if (generoSym.isNotEmpty()) {
                    Text(
                        text = generoSym,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = generoColor
                    )
                }
            }

            // Email
            Text(
                text = email,
                fontSize = 14.sp,
                color = ProfGray
            )

            // Distrito pill
            if (distrito.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = ProfGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$distrito, Arequipa",
                            fontSize = 13.sp,
                            color = Color(0xFF424242)
                        )
                    }
                }
            }
        }

        // ── Stats card: Búsquedas | Años | Comentarios ────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ProfCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    value = historial.size.toString(),
                    label = "Búsquedas"
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(Color(0xFFEEEEEE))
                )

                StatItem(
                    value = edad.ifEmpty { "-" },
                    label = "Años"
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(Color(0xFFEEEEEE))
                )

                StatItem(
                    value = comentariosCount.toString(),
                    label = "Comentarios"
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        // ── Cerrar sesión ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ProfCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = onLogout
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ExitToApp,
                        contentDescription = null,
                        tint = ProfRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Cerrar sesión",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProfRed
                )
            }
        }
    }
}

// ── Columna de estadística ────────────────────────────────────────────────────
@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = ProfRed
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = ProfGray
        )
    }
}