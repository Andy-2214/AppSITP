package com.sitp.arequipa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.sitp.arequipa.ui.auth.LoginScreen
import com.sitp.arequipa.ui.auth.RegisterScreen
import com.sitp.arequipa.ui.auth.ForgotPasswordScreen
import com.sitp.arequipa.ui.map.MapScreen
import com.sitp.arequipa.ui.theme.SistemaTransporteArequipaTheme
import com.sitp.arequipa.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SistemaTransporteArequipaTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val authViewModel: AuthViewModel = viewModel()

    // Si ya hay una sesión activa y el email está verificado, ir directo al mapa
    val usuarioActual = FirebaseAuth.getInstance().currentUser
    val pantallaInicial = if (usuarioActual != null && usuarioActual.isEmailVerified) "map" else "login"

    var currentScreen by remember { mutableStateOf(pantallaInicial) }

    var origenRepetir by remember { mutableStateOf<String?>(null) }
    var destinoRepetir by remember { mutableStateOf<String?>(null) }
    var preferenciaRepetir by remember { mutableStateOf<String?>(null) }

    when (currentScreen) {
        "login" -> LoginScreen(
            onLoginSuccess = { currentScreen = "map" },
            onGoToRegister = { currentScreen = "register" },
            onGoToForgotPassword = { currentScreen = "forgot" },
            authViewModel = authViewModel
        )
        "register" -> RegisterScreen(
            onRegisterSuccess = { currentScreen = "login" },
            onGoToLogin = { currentScreen = "login" },
            authViewModel = authViewModel
        )
        "forgot" -> ForgotPasswordScreen(
            onBack = { currentScreen = "login" },
            authViewModel = authViewModel
        )
        "map" -> {
            MapScreen(
                onLogout = {
                    authViewModel.logout()
                    currentScreen = "login"
                },
                onPerfil = { /* Manejado internamente por el nav inferior */ },
                onHistorial = { /* Manejado internamente por el nav inferior */ },
                origenInicial = origenRepetir,
                destinoInicial = destinoRepetir,
                preferenciaInicial = preferenciaRepetir
            )
            LaunchedEffect(currentScreen) {
                if (currentScreen == "map") {
                    origenRepetir = null
                    destinoRepetir = null
                    preferenciaRepetir = null
                }
            }
        }
    }
}