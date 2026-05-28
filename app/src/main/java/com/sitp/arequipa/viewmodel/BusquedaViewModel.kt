package com.sitp.arequipa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.service.IAService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BusquedaViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val iaService = IAService()

    private val _busquedaState = MutableStateFlow<BusquedaState>(BusquedaState.Idle)
    val busquedaState: StateFlow<BusquedaState> = _busquedaState

    fun buscarRuta(origen: String, destino: String, preferencia: String) {
        viewModelScope.launch {
            try {
                _busquedaState.value = BusquedaState.Loading

                val snapshot = db.collection("rutas").get().await()
                val rutas = snapshot.documents.map { doc ->
                    mapOf(
                        "codigo"   to (doc.getString("codigo") ?: ""),
                        "nombre"   to (doc.getString("nombre") ?: ""),
                        "empresa"  to (doc.getString("empresa") ?: ""),
                        "avenidas" to (doc.getString("avenidas") ?: ""),
                        "color"    to (doc.getString("color") ?: "")
                    )
                }

                val respuesta = iaService.recomendarRuta(
                    origen = origen,
                    destino = destino,
                    preferencia = preferencia,
                    rutas = rutas
                )

                _busquedaState.value = BusquedaState.Success(respuesta)

            } catch (e: Exception) {
                _busquedaState.value = BusquedaState.Error("Error: ${e.message}")
            }
        }
    }

    fun buscarRutaPorCoordenadas(
        origenLat: Double,
        origenLng: Double,
        destinoLat: Double,
        destinoLng: Double,
        preferencia: String,
        consultaExtra: String = ""
    ) {
        if (_busquedaState.value is BusquedaState.Loading) return

        viewModelScope.launch {
            try {
                _busquedaState.value = BusquedaState.Loading

                val origenNombre = iaService.coordenadasANombre(origenLat, origenLng)
                val destinoNombre = iaService.coordenadasANombre(destinoLat, destinoLng)

                println("DEBUG origen: $origenNombre")
                println("DEBUG destino: $destinoNombre")

                val snapshot = db.collection("rutas").get().await()
                val rutas = snapshot.documents.map { doc ->
                    mapOf(
                        "codigo"   to (doc.getString("codigo") ?: ""),
                        "nombre"   to (doc.getString("nombre") ?: ""),
                        "empresa"  to (doc.getString("empresa") ?: ""),
                        "avenidas" to (doc.getString("avenidas") ?: "")
                    )
                }

                val respuesta = iaService.recomendarRuta(
                    origen = origenNombre,
                    destino = destinoNombre,
                    preferencia = preferencia,
                    rutas = rutas,
                    consultaExtra = consultaExtra
                )

                _busquedaState.value = BusquedaState.Success(respuesta)

            } catch (e: Exception) {
                _busquedaState.value = BusquedaState.Error("Hubo un problema: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _busquedaState.value = BusquedaState.Idle
    }
}

sealed class BusquedaState {
    object Idle : BusquedaState()
    object Loading : BusquedaState()
    data class Success(val respuesta: String) : BusquedaState()
    data class Error(val mensaje: String) : BusquedaState()
}