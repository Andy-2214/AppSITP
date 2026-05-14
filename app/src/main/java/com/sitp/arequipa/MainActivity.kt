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
        "map" -> MapScreen(
            onLogout = {
                authViewModel.logout()
                currentScreen = "login"
            },
            onPerfil = {
                currentScreen = "perfil"
            }
        )
        "perfil" -> PerfilScreen(
            onBack = { currentScreen = "map" }
        )
    }
}