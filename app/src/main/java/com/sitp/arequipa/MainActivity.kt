package com.sitp.arequipa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sitp.arequipa.di.AppModule
import com.sitp.arequipa.presentation.auth.AuthViewModel
import com.sitp.arequipa.presentation.auth.LoginScreen
import com.sitp.arequipa.presentation.auth.RegisterScreen
import com.sitp.arequipa.presentation.auth.ForgotPasswordScreen
import com.sitp.arequipa.presentation.map.MapViewModel
import com.sitp.arequipa.presentation.map.MapScreen
import com.sitp.arequipa.presentation.perfil.PerfilViewModel
import com.sitp.arequipa.presentation.perfil.PerfilScreen
import com.sitp.arequipa.ui.theme.SistemaTransporteArequipaTheme

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

/** Factory genérico para ViewModels con dependencias manuales */
inline fun <reified T : ViewModel> viewModelFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V {
            @Suppress("UNCHECKED_CAST")
            return create() as V
        }
    }

@Composable
fun AppNavigation() {
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory { AppModule.provideAuthViewModel() }
    )
    val mapViewModel: MapViewModel = viewModel(
        factory = viewModelFactory { AppModule.provideMapViewModel() }
    )
    val perfilViewModel: PerfilViewModel = viewModel(
        factory = viewModelFactory { AppModule.providePerfilViewModel() }
    )

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
            onPerfil = { currentScreen = "perfil" },
            mapViewModel = mapViewModel
        )
        "perfil" -> PerfilScreen(
            onBack = { currentScreen = "map" },
            perfilViewModel = perfilViewModel
        )
    }
}