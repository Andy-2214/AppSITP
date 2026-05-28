package com.sitp.arequipa.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.viewmodel.AuthState
import com.sitp.arequipa.viewmodel.AuthViewModel

private val RegRed = Color(0xFFB71C1C)
private val RegFieldBg = Color(0xFFEEEEEE)
private val RegFieldBorder = Color(0xFFB71C1C)
private val RegTextGray = Color(0xFF757575)
private val RegLabelGray = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onGoToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var genero by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var distrito by remember { mutableStateOf("") }
    var expandedGenero by remember { mutableStateOf(false) }
    var expandedDistrito by remember { mutableStateOf(false) }
    var nombreError by remember { mutableStateOf(false) }

    val generos = listOf("Masculino", "Femenino", "Prefiero no decir")
    val distritos = listOf(
        "Arequipa (Cercado)", "Alto Selva Alegre", "Cayma",
        "Cerro Colorado", "Jacobo Hunter", "José Luis Bustamante y Rivero",
        "Mariano Melgar", "Miraflores", "Paucarpata",
        "Sachaca", "Socabaya", "Tiabaya", "Uchumayo", "Yanahuara"
    )

    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            authViewModel.resetState()
            onRegisterSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono bus rojo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RegRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsBus,
                    contentDescription = "Bus icon",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Crear cuenta",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Únete a SIT Arequipa para planificar tus rutas.",
                fontSize = 14.sp,
                color = RegTextGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Campo Nombre con validación de error
            RegisterFieldWithError(
                value = nombre,
                onValueChange = {
                    nombre = it
                    nombreError = it.isEmpty()
                },
                label = "Nombre completo *",
                hasError = nombreError,
                errorMessage = "El nombre completo es obligatorio."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Email
            RegisterField(
                value = email,
                onValueChange = { email = it },
                label = "Correo electrónico *"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Contraseña
            RegisterFieldPassword(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña *",
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible }
            )
            Text(
                text = "Mínimo 8 caracteres.",
                fontSize = 12.sp,
                color = RegTextGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Género y Edad en fila
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dropdown Género
                Box(modifier = Modifier.weight(1f)) {
                    RegisterDropdown(
                        value = genero,
                        label = "Género",
                        expanded = expandedGenero,
                        onExpandChange = { expandedGenero = it }
                    )
                    DropdownMenu(
                        expanded = expandedGenero,
                        onDismissRequest = { expandedGenero = false }
                    ) {
                        generos.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion) },
                                onClick = {
                                    genero = opcion
                                    expandedGenero = false
                                }
                            )
                        }
                    }
                }

                // Campo Edad
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RegFieldBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Edad",
                            fontSize = 12.sp,
                            color = RegLabelGray,
                            fontWeight = FontWeight.Medium
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = edad,
                            onValueChange = { if (it.all { c -> c.isDigit() }) edad = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = Color(0xFF212121)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Línea roja debajo de fila género/edad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(RegFieldBorder)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown Distrito
            Box(modifier = Modifier.fillMaxWidth()) {
                RegisterDropdown(
                    value = distrito,
                    label = "Distrito de residencia",
                    expanded = expandedDistrito,
                    onExpandChange = { expandedDistrito = it }
                )
                DropdownMenu(
                    expanded = expandedDistrito,
                    onDismissRequest = { expandedDistrito = false }
                ) {
                    distritos.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                distrito = opcion
                                expandedDistrito = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error de estado
            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = RegRed,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón Crear cuenta
            Button(
                onClick = {
                    if (nombre.isEmpty()) {
                        nombreError = true
                        return@Button
                    }
                    authViewModel.register(
                        nombre = nombre,
                        email = email,
                        password = password,
                        genero = genero,
                        edad = edad.toIntOrNull() ?: 0,
                        distrito = distrito
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = authState !is AuthState.Loading &&
                        nombre.isNotEmpty() &&
                        email.isNotEmpty() &&
                        password.isNotEmpty() &&
                        genero.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RegRed)
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Crear cuenta",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onGoToLogin, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = "¿Ya tienes una cuenta? Inicia sesión",
                    color = RegRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RegisterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(RegFieldBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = RegLabelGray,
                    fontWeight = FontWeight.Medium
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = Color(0xFF212121)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(RegFieldBorder)
        )
    }
}

@Composable
private fun RegisterFieldWithError(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hasError: Boolean,
    errorMessage: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(if (hasError) Color(0xFFFFF0F0) else RegFieldBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = if (hasError) RegRed else RegLabelGray,
                        fontWeight = FontWeight.Medium
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Color(0xFF212121)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (hasError) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Error",
                        tint = RegRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(RegFieldBorder)
        )
        if (hasError) {
            Text(
                text = errorMessage,
                color = RegRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 3.dp)
            )
        }
    }
}

@Composable
private fun RegisterFieldPassword(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(RegFieldBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = RegLabelGray,
                        fontWeight = FontWeight.Medium
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Color(0xFF212121)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    onClick = onTogglePassword,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Ocultar" else "Mostrar",
                        tint = RegTextGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(RegFieldBorder)
        )
    }
}

@Composable
private fun RegisterDropdown(
    value: String,
    label: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(RegFieldBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = RegLabelGray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = value.ifEmpty { "" },
                        fontSize = 15.sp,
                        color = Color(0xFF212121)
                    )
                }
                IconButton(
                    onClick = { onExpandChange(!expanded) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Desplegar",
                        tint = RegTextGray
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(RegFieldBorder)
        )
    }
}