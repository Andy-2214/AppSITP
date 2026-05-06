package com.sitp.arequipa.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.application.usecases.auth.LoginUseCase
import com.sitp.arequipa.application.usecases.auth.LogoutUseCase
import com.sitp.arequipa.application.usecases.auth.RegisterUseCase
import com.sitp.arequipa.application.usecases.auth.ResetPasswordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                loginUseCase(email, password)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Email o contraseña incorrectos")
            }
        }
    }

    fun register(
        nombre: String,
        email: String,
        password: String,
        genero: String,
        edad: Int,
        distrito: String
    ) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                registerUseCase(nombre, email, password, genero, edad, distrito)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Error al registrarse: ${e.message}")
            }
        }
    }

    fun recuperarPassword(email: String) {
        viewModelScope.launch {
            try {
                resetPasswordUseCase(email)
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Error al enviar email")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _authState.value = AuthState.Idle
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object PasswordResetSent : AuthState()
    data class Error(val message: String) : AuthState()
}
