package com.sitp.arequipa.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.application.usecases.routes.GetOfficialRoutesUseCase
import com.sitp.arequipa.domain.entities.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MapViewModel(
    private val getOfficialRoutesUseCase: GetOfficialRoutesUseCase
) : ViewModel() {

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // No cargamos rutas en init — se llama desde MapScreen solo cuando el usuario ya está autenticado
    fun loadRoutes() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _routes.value = getOfficialRoutesUseCase()
            } catch (e: Exception) {
                _error.value = "Error al cargar rutas: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
