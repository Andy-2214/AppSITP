package com.sitp.arequipa.presentation.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.model.UsuarioPerfil
import com.sitp.arequipa.domain.usecase.ActualizarNombreUseCase
import com.sitp.arequipa.domain.usecase.ObtenerPerfilUseCase
import com.sitp.arequipa.domain.usecase.ObtenerSesionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PerfilUiState {
    object Loading : PerfilUiState()
    data class Success(val perfil: UsuarioPerfil) : PerfilUiState()
    data class Error(val mensaje: String) : PerfilUiState()
}

class PerfilViewModel(
    private val obtenerSesionUseCase: ObtenerSesionUseCase,
    private val obtenerPerfilUseCase: ObtenerPerfilUseCase,
    private val actualizarNombreUseCase: ActualizarNombreUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PerfilUiState>(PerfilUiState.Loading)
    val uiState: StateFlow<PerfilUiState> = _uiState

    fun cargarPerfil() {
        val sesion = obtenerSesionUseCase()
        val uid = sesion.uid ?: run {
            _uiState.value = PerfilUiState.Error("No hay usuario autenticado")
            return
        }
        val currentEmail = sesion.email ?: ""
        viewModelScope.launch {
            _uiState.value = PerfilUiState.Loading
            try {
                val perfil = obtenerPerfilUseCase(uid).let {
                    if (it.email.isBlank()) it.copy(email = currentEmail) else it
                }
                _uiState.value = PerfilUiState.Success(perfil)
            } catch (e: Exception) {
                _uiState.value = PerfilUiState.Error(e.message ?: "Error al cargar perfil")
            }
        }
    }

    fun actualizarNombre(nuevoNombre: String) {
        val uid = obtenerSesionUseCase().uid ?: return
        val current = _uiState.value
        viewModelScope.launch {
            try {
                actualizarNombreUseCase(uid, nuevoNombre)
                if (current is PerfilUiState.Success) {
                    _uiState.value = PerfilUiState.Success(current.perfil.copy(nombre = nuevoNombre))
                }
            } catch (e: Exception) {
                println("DEBUG PerfilViewModel actualizarNombre error: ${e.message}")
            }
        }
    }
}