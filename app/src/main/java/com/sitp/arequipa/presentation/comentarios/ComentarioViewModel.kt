package com.sitp.arequipa.presentation.comentarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.model.Comentario
import com.sitp.arequipa.domain.usecase.CargarComentariosUseCase
import com.sitp.arequipa.domain.usecase.ObtenerSesionUseCase
import com.sitp.arequipa.domain.usecase.PublicarComentarioUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ComentarioViewModel(
    private val obtenerSesionUseCase: ObtenerSesionUseCase,
    private val publicarComentarioUseCase: PublicarComentarioUseCase,
    private val cargarComentariosUseCase: CargarComentariosUseCase
) : ViewModel() {

    private val _comentarios = MutableStateFlow<List<Comentario>>(emptyList())
    val comentarios: StateFlow<List<Comentario>> = _comentarios

    private val _comentarioState = MutableStateFlow<ComentarioState>(ComentarioState.Idle)
    val comentarioState: StateFlow<ComentarioState> = _comentarioState

    val sesionActiva: Boolean get() = obtenerSesionUseCase().uid != null

    // HU-21: Publicar comentario
    fun publicarComentario(rutaId: String, rutaNombre: String, rutaCodigo: String, texto: String) {
        val uid = obtenerSesionUseCase().uid ?: return
        viewModelScope.launch {
            try {
                _comentarioState.value = ComentarioState.Loading
                publicarComentarioUseCase(uid, rutaId, rutaNombre, rutaCodigo, texto)
                _comentarioState.value = ComentarioState.Success
            } catch (e: IllegalArgumentException) {
                _comentarioState.value = ComentarioState.Error(e.message ?: "Error de validación")
            } catch (e: Exception) {
                _comentarioState.value = ComentarioState.Error("Error al publicar: ${e.message}")
            }
        }
    }

    // HU-21: Cargar comentarios aprobados de una ruta
    fun cargarComentarios(rutaId: String) {
        viewModelScope.launch {
            try {
                _comentarios.value = cargarComentariosUseCase(rutaId)
            } catch (e: Exception) {
                println("DEBUG comentarios error: ${e.message}")
            }
        }
    }

    fun resetState() {
        _comentarioState.value = ComentarioState.Idle
    }
}

sealed class ComentarioState {
    object Idle : ComentarioState()
    object Loading : ComentarioState()
    object Success : ComentarioState()
    data class Error(val mensaje: String) : ComentarioState()
}