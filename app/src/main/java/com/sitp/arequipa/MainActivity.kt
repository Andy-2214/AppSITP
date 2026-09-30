package com.sitp.arequipa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitp.arequipa.di.provideViewModelFactory
import com.sitp.arequipa.presentation.auth.LoginScreen
import com.sitp.arequipa.presentation.auth.RegisterScreen
import com.sitp.arequipa.presentation.auth.ForgotPasswordScreen
import com.sitp.arequipa.presentation.map.MapScreen
import com.sitp.arequipa.presentation.theme.SistemaTransporteArequipaTheme
import com.sitp.arequipa.presentation.auth.AuthViewModel

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
    val authViewModel: AuthViewModel = viewModel(factory = provideViewModelFactory())

    // Sesión activa verificada vía VerificarSesionUseCase (Clean Architecture)
    val sesionActiva = authViewModel.sesionActiva()
    val pantallaInicial = if (sesionActiva) "map" else "login"

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