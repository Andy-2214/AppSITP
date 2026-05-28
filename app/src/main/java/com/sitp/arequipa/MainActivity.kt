package com.sitp.arequipa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.ui.auth.LoginScreen
import com.sitp.arequipa.ui.auth.RegisterScreen
import com.sitp.arequipa.ui.auth.ForgotPasswordScreen
import com.sitp.arequipa.ui.map.MapScreen
import com.sitp.arequipa.ui.theme.SistemaTransporteArequipaTheme
import com.sitp.arequipa.viewmodel.AuthViewModel
import com.sitp.arequipa.ui.perfil.PerfilScreen
import com.sitp.arequipa.ui.historial.HistorialScreen

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
    var currentScreen by remember { mutableStateOf("login") }

    // Para repetir búsqueda desde historial
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
                onPerfil = { currentScreen = "perfil" },
                onHistorial = { currentScreen = "historial" },
                origenInicial = origenRepetir,
                destinoInicial = destinoRepetir,
                preferenciaInicial = preferenciaRepetir
            )
            // Limpiar después de usar para que no se repita
            LaunchedEffect(currentScreen) {
                if (currentScreen == "map") {
                    origenRepetir = null
                    destinoRepetir = null
                    preferenciaRepetir = null
                }
            }
        }
        "perfil" -> PerfilScreen(
            onBack = { currentScreen = "map" }
        )
        "historial" -> HistorialScreen(
            onBack = { currentScreen = "map" },
            onRepetirBusqueda = { origen, destino, preferencia ->
                origenRepetir = origen
                destinoRepetir = destino
                preferenciaRepetir = preferencia
                currentScreen = "map"
            }
        )
    }
}