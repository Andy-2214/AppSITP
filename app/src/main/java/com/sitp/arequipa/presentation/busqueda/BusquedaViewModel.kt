package com.sitp.arequipa.presentation.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.repository.RouteRepository
import com.sitp.arequipa.domain.usecase.BuscarRutaUseCase
import com.sitp.arequipa.domain.usecase.RecomendarRutaIAUseCase
import com.sitp.arequipa.domain.usecase.ResultadoBusqueda
import com.sitp.arequipa.domain.usecase.SincronizarRutasUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BusquedaViewModel(
    private val routeRepository: RouteRepository,
    private val buscarRutaUseCase: BuscarRutaUseCase,
    private val recomendarRutaIAUseCase: RecomendarRutaIAUseCase,
    private val sincronizarRutasUseCase: SincronizarRutasUseCase
) : ViewModel() {

    private val _busquedaState = MutableStateFlow<BusquedaState>(BusquedaState.Idle)
    val busquedaState: StateFlow<BusquedaState> = _busquedaState

    init {
        viewModelScope.launch {
            try {
                sincronizarRutasUseCase()
                println("DEBUG BusquedaViewModel: Rutas sincronizadas con éxito localmente.")
            } catch (e: Exception) {
                println("DEBUG BusquedaViewModel: Error al sincronizar rutas: ${e.message}")
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

                // 1. Obtener rutas locales
                val rutas = routeRepository.obtenerRutas().first()

                // 2. Ejecutar algoritmo de enrutamiento (el use case usa Dispatchers.Default)
                val resultado = buscarRutaUseCase(
                    rutas = rutas,
                    origenLat = origenLat,
                    origenLng = origenLng,
                    destinoLat = destinoLat,
                    destinoLng = destinoLng,
                    preferencia = preferencia,
                    consultaExtra = consultaExtra
                )

                when (resultado) {
                    is ResultadoBusqueda.Local -> {
                        // a) Mostrar resultado local INSTANTÁNEO
                        _busquedaState.value = BusquedaState.Success(resultado.respuesta)

                        // b) En segundo plano: reemplazar "Punto de origen/destino" con nombres reales
                        viewModelScope.launch {
                            try {
                                val origenDeferred = async { recomendarRutaIAUseCase.obtenerNombreOrigen(origenLat, origenLng) }
                                val destinoDeferred = async { recomendarRutaIAUseCase.obtenerNombreDestino(destinoLat, destinoLng) }
                                val origenNombre = origenDeferred.await()
                                val destinoNombre = destinoDeferred.await()
                                val respuestaFinal = resultado.respuesta
                                    .replace("Punto de origen", origenNombre)
                                    .replace("Punto de destino", destinoNombre)
                                _busquedaState.value = BusquedaState.Success(respuestaFinal)
                            } catch (e: Exception) {
                                println("DEBUG BusquedaViewModel: geocoding en segundo plano falló: ${e.message}")
                            }
                        }
                    }

                    is ResultadoBusqueda.NecesitaIA -> {
                        // Geocodificar en paralelo antes de llamar a Groq
                        val origenDeferred = async { recomendarRutaIAUseCase.obtenerNombreOrigen(origenLat, origenLng) }
                        val destinoDeferred = async { recomendarRutaIAUseCase.obtenerNombreDestino(destinoLat, destinoLng) }
                        val origenNombre = origenDeferred.await()
                        val destinoNombre = destinoDeferred.await()

                        val respuesta = recomendarRutaIAUseCase.recomendar(
                            origenNombre = origenNombre,
                            destinoNombre = destinoNombre,
                            preferencia = preferencia,
                            rutasFinales = resultado.rutasFinales,
                            rutasOrigen = resultado.rutasOrigen,
                            rutasDestino = resultado.rutasDestino,
                            esDirecta = resultado.esDirecta,
                            consultaExtra = consultaExtra
                        )
                        _busquedaState.value = BusquedaState.Success(respuesta)
                    }
                }

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