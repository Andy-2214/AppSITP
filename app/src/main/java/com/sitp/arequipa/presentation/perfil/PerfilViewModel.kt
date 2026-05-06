package com.sitp.arequipa.presentation.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.entities.User
import com.sitp.arequipa.domain.repositories.AuthRepository
import com.sitp.arequipa.domain.repositories.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PerfilViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _mensaje = MutableStateFlow("")
    val mensaje: StateFlow<String> = _mensaje

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            _user.value = userRepository.getUser(uid)
        }
    }

    fun updateNombre(nuevoNombre: String) {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUserId() ?: return@launch
                userRepository.updateNombre(uid, nuevoNombre)
                _user.value = _user.value?.copy(nombre = nuevoNombre)
                _mensaje.value = "✅ Nombre actualizado"
            } catch (e: Exception) {
                _mensaje.value = "Error al actualizar nombre"
            }
        }
    }
}
