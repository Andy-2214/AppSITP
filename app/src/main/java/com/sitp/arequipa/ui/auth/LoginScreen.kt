package com.sitp.arequipa.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.viewmodel.AuthState
import com.sitp.arequipa.viewmodel.AuthViewModel

private val SITPRed = Color(0xFFB71C1C)
private val SITPRedLight = Color(0xFFD32F2F)
private val FieldBackground = Color(0xFFEEEEEE)
private val FieldBorderColor = Color(0xFFB71C1C)
private val TextGray = Color(0xFF757575)
private val LabelGray = Color(0xFF9E9E9E)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGoToRegister: () -> Unit,
    onGoToForgotPassword: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono bus rojo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SITPRed),
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

            // Título
            Text(
                text = "SITP Arequipa",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = SITPRed
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Inicia sesión para continuar",
                fontSize = 15.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Campo Email
            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = "Correo electrónico",
                placeholder = "usuario@ejemplo.com"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Contraseña
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña",
                placeholder = "••••••••",
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ¿Olvidaste tu contraseña?
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onGoToForgotPassword, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = "¿Olvidaste tu contraseña?",
                        color = SITPRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Mensaje de error
            if (authState is AuthState.Error) {
                val errorMsg = (authState as AuthState.Error).message
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMsg,
                    color = SITPRed,
                    fontSize = 13.sp
                )
                if (errorMsg.contains("verificar")) {
                    TextButton(onClick = { authViewModel.reenviarVerificacion(email) }) {
                        Text("Reenviar email de verificación", color = SITPRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón Ingresar
            Button(
                onClick = { authViewModel.login(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = authState !is AuthState.Loading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SITPRed)
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Ingresar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Enlace Regístrate
            Text(
                text = "¿No tienes cuenta?",
                color = TextGray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            TextButton(onClick = onGoToRegister, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = "Regístrate",
                    color = SITPRed,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    val visualTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> VisualTransformation.None
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(FieldBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = LabelGray,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicAuthInput(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = placeholder,
                        visualTransformation = visualTransformation,
                        modifier = Modifier.weight(1f)
                    )
                    if (isPassword && onTogglePassword != null) {
                        IconButton(
                            onClick = onTogglePassword,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Ocultar" else "Mostrar",
                                tint = TextGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        // Línea roja inferior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(FieldBorderColor)
        )
    }
}

@Composable
private fun BasicAuthInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        visualTransformation = visualTransformation,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 15.sp,
            color = Color(0xFF212121)
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    color = Color(0xFFBDBDBD)
                )
            }
            innerTextField()
        }
    )
}