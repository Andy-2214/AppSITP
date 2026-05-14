package com.sitp.arequipa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.signInWithEmailAndPassword(email, password).await()
                println("DEBUG isEmailVerified: ${result.user?.isEmailVerified}")
                if (result.user?.isEmailVerified == false) {
                    auth.signOut()
                    _authState.value = AuthState.Error(
                        "Debes verificar tu email antes de ingresar. Revisa tu bandeja de entrada."
                    )
                    return@launch
                }
                _authState.value = AuthState.Success
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
                val result = auth.createUserWithEmailAndPassword(email, password).await()

                val usuario = hashMapOf(
                    "nombre" to nombre,
                    "email" to email,
                    "genero" to genero,
                    "edad" to edad,
                    "distrito" to distrito,
                    "fechaRegistro" to com.google.firebase.Timestamp.now()
                )
                db.collection("usuarios")
                    .document(result.user!!.uid)
                    .set(usuario)
                    .await()

                result.user!!.sendEmailVerification().await()
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
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Error al enviar email")
            }
        }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun reenviarVerificacion(email: String) {
        viewModelScope.launch {
            try {
                auth.currentUser?.sendEmailVerification()?.await()
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