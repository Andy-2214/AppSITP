package com.sitp.arequipa.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.viewmodel.AuthState
import com.sitp.arequipa.viewmodel.AuthViewModel

// Paleta del sistema de diseño
private val FPRed       = Color(0xFFC62828)
private val FPRedDark   = Color(0xFFB71C1C)
private val FPSecondary = Color(0xFF424242)
private val FPTertiary  = Color(0xFF1565C0)
private val FPNeutral   = Color(0xFFF5F5F5)
private val FPFieldBg   = Color(0xFFEEEEEE)
private val FPLabelGray = Color(0xFF9E9E9E)
private val FPTextGray  = Color(0xFF757575)

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // ── Flecha atrás ──────────────────────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = FPSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Ícono de candado en caja rosada ───────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFCDD2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LockReset,
                    contentDescription = "Recuperar contraseña",
                    tint = FPRed,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Título ────────────────────────────────────────────────────
            Text(
                text = "Recuperar contraseña",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FPSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Subtítulo ─────────────────────────────────────────────────
            Text(
                text = "Ingresa el correo electrónico asociado a tu cuenta y te enviaremos instrucciones para crear una nueva contraseña.",
                fontSize = 14.sp,
                color = FPTextGray,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Banner azul de confirmación (solo al enviar) ───────────────
            if (authState is AuthState.PasswordResetSent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = null,
                            tint = FPTertiary,
                            modifier = Modifier
                                .size(22.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Revisa tu correo",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FPTertiary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Te enviamos un enlace. Revisa tu bandeja de entrada o carpeta de spam.",
                                fontSize = 13.sp,
                                color = FPTertiary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Banner de error ───────────────────────────────────────────
            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = FPRed,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Campo correo electrónico ──────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(FPFieldBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "Correo electrónico",
                            fontSize = 12.sp,
                            color = FPLabelGray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = email,
                            onValueChange = { email = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = Color(0xFF212121)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (email.isEmpty()) {
                                    Text(
                                        text = "usuario@ejemplo.com",
                                        fontSize = 15.sp,
                                        color = Color(0xFFBDBDBD)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
                // Línea roja inferior
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(FPRedDark)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Botón Enviar enlace ───────────────────────────────────────
            Button(
                onClick = { authViewModel.recuperarPassword(email) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = authState !is AuthState.Loading && email.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FPRedDark)
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enviar enlace de recuperación",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}