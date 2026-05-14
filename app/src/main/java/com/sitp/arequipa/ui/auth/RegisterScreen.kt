package com.sitp.arequipa.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.viewmodel.AuthState
import com.sitp.arequipa.viewmodel.AuthViewModel

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
    var genero by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var distrito by remember { mutableStateOf("") }
    var expandedGenero by remember { mutableStateOf(false) }

    val generos = listOf("Masculino", "Femenino", "Prefiero no decir")
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            authViewModel.resetState()
            onRegisterSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🚌 Crear cuenta",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Nombre
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre completo *") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email *") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Contraseña
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña * (mín. 8 caracteres)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Género (obligatorio)
        ExposedDropdownMenuBox(
            expanded = expandedGenero,
            onExpandedChange = { expandedGenero = it }
        ) {
            OutlinedTextField(
                value = genero,
                onValueChange = {},
                readOnly = true,
                label = { Text("Género *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGenero) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
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

        Spacer(modifier = Modifier.height(12.dp))

        // Edad (opcional)
        OutlinedTextField(
            value = edad,
            onValueChange = { if (it.all { c -> c.isDigit() }) edad = it },
            label = { Text("Edad (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Distrito (opcional)
        OutlinedTextField(
            value = distrito,
            onValueChange = { distrito = it },
            label = { Text("Distrito (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                authViewModel.register(
                    nombre = nombre,
                    email = email,
                    password = password,
                    genero = genero,
                    edad = edad.toIntOrNull() ?: 0,
                    distrito = distrito
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthState.Loading &&
                    nombre.isNotEmpty() &&
                    email.isNotEmpty() &&
                    password.isNotEmpty() &&
                    genero.isNotEmpty()
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Crear cuenta")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onGoToLogin) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }
}