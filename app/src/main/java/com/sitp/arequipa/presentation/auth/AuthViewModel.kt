package com.sitp.arequipa.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.model.LoginResult
import com.sitp.arequipa.domain.usecase.LoginUseCase
import com.sitp.arequipa.domain.usecase.LogoutUseCase
import com.sitp.arequipa.domain.usecase.RecuperarPasswordUseCase
import com.sitp.arequipa.domain.usecase.ReenviarVerificacionUseCase
import com.sitp.arequipa.domain.usecase.RegisterUseCase
import com.sitp.arequipa.domain.usecase.VerificarSesionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val recuperarPasswordUseCase: RecuperarPasswordUseCase,
    private val reenviarVerificacionUseCase: ReenviarVerificacionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val verificarSesionUseCase: VerificarSesionUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun sesionActiva(): Boolean = verificarSesionUseCase()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                when (loginUseCase(email, password)) {
                    LoginResult.Exito -> _authState.value = AuthState.Success
                    LoginResult.EmailNoVerificado -> _authState.value = AuthState.Error(
                        "Debes verificar tu email antes de ingresar. Revisa tu bandeja de entrada."
                    )
                }
            } catch (e: Exception) {
                val mensaje = when {
                    e.message?.contains("no user record") == true ->
                        "No existe una cuenta con este email."
                    e.message?.contains("password is invalid") == true ->
                        "Contraseña incorrecta."
                    e.message?.contains("blocked") == true ->
                        "Demasiados intentos. Intenta más tarde."
                    else -> "Email o contraseña incorrectos."
                }
                _authState.value = AuthState.Error(mensaje)
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
                val mensaje = when {
                    e.message?.contains("email address is already in use") == true ->
                        "Este email ya está registrado. Inicia sesión."
                    e.message?.contains("badly formatted") == true ->
                        "El formato del email no es válido."
                    e.message?.contains("weak-password") == true ->
                        "La contraseña debe tener al menos 6 caracteres."
                    else -> "Error al registrarse: ${e.message}"
                }
                _authState.value = AuthState.Error(mensaje)
            }
        }
    }

    fun recuperarPassword(email: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                recuperarPasswordUseCase(email)
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Error al enviar email")
            }
        }
    }

    fun logout() {
        logoutUseCase()
        _authState.value = AuthState.Idle
    }

    fun reenviarVerificacion(email: String) {
        viewModelScope.launch {
            try {
                reenviarVerificacionUseCase()
                _authState.value = AuthState.Error("Email de verificación reenviado ✅")
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Error al reenviar el email")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object PasswordResetSent : AuthState()
    data class Error(val message: String) : AuthState()
}