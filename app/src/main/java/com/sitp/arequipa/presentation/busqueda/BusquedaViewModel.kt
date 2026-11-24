package com.sitp.arequipa.presentation.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.LugarEncontrado
import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.model.ResultadoInterpretacion
import com.sitp.arequipa.domain.model.TipoTramo
import com.sitp.arequipa.domain.repository.AsistenteNoDisponibleException
import com.sitp.arequipa.domain.repository.RouteRepository
import com.sitp.arequipa.domain.usecase.BuscarLugaresUseCase
import com.sitp.arequipa.domain.usecase.BuscarRutaUseCase
import com.sitp.arequipa.domain.usecase.InterpretarConsultaUseCase
import com.sitp.arequipa.domain.usecase.ObtenerNombreUbicacionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Panel de búsqueda (ADR-010 y ADR-011):
 * - calcula las rutas con el motor propio y muestra las opciones a medida que aparecen;
 * - busca lugares por nombre en el catálogo (sin IA, sin internet);
 * - interpreta una frase con la IA y propone dónde van los pines (el usuario los confirma).
 */
class BusquedaViewModel(
    private val routeRepository: RouteRepository,
    private val buscarRutaUseCase: BuscarRutaUseCase,
    private val buscarLugaresUseCase: BuscarLugaresUseCase,
    private val interpretarConsultaUseCase: InterpretarConsultaUseCase,
    private val obtenerNombreUbicacion: ObtenerNombreUbicacionUseCase
) : ViewModel() {

    private val _busquedaState = MutableStateFlow<BusquedaState>(BusquedaState.Idle)
    val busquedaState: StateFlow<BusquedaState> = _busquedaState

    private val _lugares = MutableStateFlow<List<LugarEncontrado>>(emptyList())
    val lugares: StateFlow<List<LugarEncontrado>> = _lugares

    private val _interpretacion = MutableStateFlow<InterpretacionState>(InterpretacionState.Inactiva)
    val interpretacion: StateFlow<InterpretacionState> = _interpretacion

    /** Nombres de calle de los puntos de subida y bajada de las opciones («Avenida Ejército, Cayma»). */
    private val _nombres = MutableStateFlow<Map<Coordenada, String>>(emptyMap())
    val nombres: StateFlow<Map<Coordenada, String>> = _nombres

    private var trabajoBusqueda: Job? = null
    private var trabajoLugares: Job? = null
    private var trabajoNombres: Job? = null
    private var trabajoInterpretacion: Job? = null

    // La sincronización de rutas se hace una sola vez, en MapScreen.

    fun buscarRuta(origen: Coordenada, destino: Coordenada, modo: ModoBusqueda) {
        trabajoBusqueda?.cancel()
        trabajoBusqueda = viewModelScope.launch {
            _busquedaState.value = BusquedaState.Calculando
            try {
                val rutas = routeRepository.obtenerRutas().first()
                if (rutas.isEmpty()) {
                    _busquedaState.value = BusquedaState.Error("Todavía no hay rutas guardadas en el teléfono. Conéctate a internet una vez para descargarlas.")
                    return@launch
                }
                buscarRutaUseCase(rutas, origen, destino, modo).collect { opciones ->
                    _busquedaState.value = BusquedaState.Resultados(opciones, modo)
                    nombrarPuntos(opciones)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _busquedaState.value = BusquedaState.Error("Hubo un problema al calcular las rutas: ${e.localizedMessage}")
            }
        }
    }

    /** Busca lugares por nombre mientras el usuario escribe (espera 250 ms entre teclas). */
    fun buscarLugares(texto: String, cerca: Coordenada?) {
        trabajoLugares?.cancel()
        if (texto.trim().length < BuscarLugaresUseCase.MINIMO_LETRAS) {
            _lugares.value = emptyList()
            return
        }
        trabajoLugares = viewModelScope.launch {
            delay(250)
            _lugares.value = buscarLugaresUseCase(texto, cerca, 6)
        }
    }

    fun limpiarLugares() {
        trabajoLugares?.cancel()
        _lugares.value = emptyList()
    }

    fun interpretar(frase: String, ubicacion: Coordenada?) {
        if (frase.isBlank()) return
        trabajoInterpretacion?.cancel()
        trabajoInterpretacion = viewModelScope.launch {
            _interpretacion.value = InterpretacionState.Interpretando
            _interpretacion.value = try {
                InterpretacionState.Lista(interpretarConsultaUseCase(frase, ubicacion))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AsistenteNoDisponibleException) {
                InterpretacionState.Error(e.message ?: "La IA no está disponible.")
            } catch (e: Exception) {
                InterpretacionState.Error("No se pudo interpretar la consulta. Ubica los puntos moviendo el mapa.")
            }
        }
    }

    fun limpiarInterpretacion() {
        trabajoInterpretacion?.cancel()
        _interpretacion.value = InterpretacionState.Inactiva
    }

    fun resetState() {
        trabajoBusqueda?.cancel()
        _busquedaState.value = BusquedaState.Idle
    }

    /** Pone nombre a los puntos de subida y bajada (consulta local, ~1 ms cada uno). */
    private fun nombrarPuntos(opciones: OpcionesBusqueda) {
        val puntos = (opciones.porTiempo + opciones.porCosto)
            .flatMap { it.tramos }
            .filter { it.tipo == TipoTramo.COMBI }
            .flatMap { listOf(it.desde, it.hasta) }
            .distinct()
            .filter { it !in _nombres.value }
        if (puntos.isEmpty()) return
        trabajoNombres?.cancel()
        trabajoNombres = viewModelScope.launch {
            for (p in puntos) {
                if (p in _nombres.value) continue
                _nombres.value = _nombres.value + (p to obtenerNombreUbicacion(p.lat, p.lng))
            }
        }
    }
}

sealed class BusquedaState {
    object Idle : BusquedaState()
    object Calculando : BusquedaState()
    /** Opciones encontradas hasta ahora; con `opciones.completa = false` el motor sigue buscando. */
    data class Resultados(val opciones: OpcionesBusqueda, val modo: ModoBusqueda) : BusquedaState()
    data class Error(val mensaje: String) : BusquedaState()
}

sealed class InterpretacionState {
    object Inactiva : InterpretacionState()
    object Interpretando : InterpretacionState()
    data class Lista(val resultado: ResultadoInterpretacion) : InterpretacionState()
    data class Error(val mensaje: String) : InterpretacionState()
}
