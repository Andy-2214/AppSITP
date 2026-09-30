package com.sitp.arequipa.presentation.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.model.BusquedaHistorial
import com.sitp.arequipa.domain.model.RutaFavorita
import com.sitp.arequipa.domain.usecase.CargarFavoritosUseCase
import com.sitp.arequipa.domain.usecase.CargarHistorialUseCase
import com.sitp.arequipa.domain.usecase.EliminarBusquedaUseCase
import com.sitp.arequipa.domain.usecase.EliminarFavoritoUseCase
import com.sitp.arequipa.domain.usecase.GuardarBusquedaUseCase
import com.sitp.arequipa.domain.usecase.GuardarFavoritoUseCase
import com.sitp.arequipa.domain.usecase.ObtenerSesionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistorialViewModel(
    private val obtenerSesionUseCase: ObtenerSesionUseCase,
    private val guardarBusquedaUseCase: GuardarBusquedaUseCase,
    private val cargarHistorialUseCase: CargarHistorialUseCase,
    private val eliminarBusquedaUseCase: EliminarBusquedaUseCase,
    private val cargarFavoritosUseCase: CargarFavoritosUseCase,
    private val guardarFavoritoUseCase: GuardarFavoritoUseCase,
    private val eliminarFavoritoUseCase: EliminarFavoritoUseCase
) : ViewModel() {

    private val uid: String? get() = obtenerSesionUseCase().uid

    private val _historial = MutableStateFlow<List<BusquedaHistorial>>(emptyList())
    val historial: StateFlow<List<BusquedaHistorial>> = _historial

    private val _favoritos = MutableStateFlow<List<RutaFavorita>>(emptyList())
    val favoritos: StateFlow<List<RutaFavorita>> = _favoritos

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _favoritoAutoGuardado = MutableStateFlow(false)
    val favoritoAutoGuardado: StateFlow<Boolean> = _favoritoAutoGuardado

    // HU-22: Guardar búsqueda en historial y verificar favorito automático (HU-20)
    fun guardarBusqueda(
        origen: String,
        destino: String,
        preferencia: String,
        respuestaIA: String
    ) {
        val uidActual = uid ?: return
        viewModelScope.launch {
            try {
                val favoritoCreado = guardarBusquedaUseCase(uidActual, origen, destino, preferencia, respuestaIA)
                if (favoritoCreado) _favoritoAutoGuardado.value = true
            } catch (e: Exception) {
                println("DEBUG historial error: ${e.message}")
            }
        }
    }

    // HU-22: Cargar historial del usuario
    fun cargarHistorial() {
        val uidActual = uid ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                _historial.value = cargarHistorialUseCase(uidActual)
            } catch (e: Exception) {
                println("DEBUG historial error: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    // HU-22: Eliminar búsqueda del historial
    fun eliminarBusqueda(busquedaId: String) {
        val uidActual = uid ?: return
        viewModelScope.launch {
            try {
                eliminarBusquedaUseCase(uidActual, busquedaId)
                _historial.value = _historial.value.filter { it.id != busquedaId }
            } catch (e: Exception) {
                println("DEBUG eliminar historial error: ${e.message}")
            }
        }
    }

    // HU-20: Cargar favoritos
    fun cargarFavoritos() {
        val uidActual = uid ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                _favoritos.value = cargarFavoritosUseCase(uidActual)
            } catch (e: Exception) {
                println("DEBUG favoritos error: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    // HU-20: Guardar favorito manualmente
    fun guardarFavoritoManual(origen: String, destino: String, nombre: String) {
        val uidActual = uid ?: return
        viewModelScope.launch {
            try {
                guardarFavoritoUseCase(uidActual, origen, destino, nombre)
                cargarFavoritos()
            } catch (e: Exception) {
                println("DEBUG guardar favorito error: ${e.message}")
            }
        }
    }

    // HU-20: Eliminar favorito
    fun eliminarFavorito(favoritoId: String) {
        val uidActual = uid ?: return
        viewModelScope.launch {
            try {
                eliminarFavoritoUseCase(uidActual, favoritoId)
                _favoritos.value = _favoritos.value.filter { it.id != favoritoId }
            } catch (e: Exception) {
                println("DEBUG eliminar favorito error: ${e.message}")
            }
        }
    }

    fun resetFavoritoAutoGuardado() {
        _favoritoAutoGuardado.value = false
    }
}