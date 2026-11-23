package com.sitp.arequipa.presentation.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.usecase.ObtenerAtribucionDatosUseCase
import com.sitp.arequipa.domain.usecase.ObtenerNombreUbicacionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Nombres legibles de los pines de origen y destino ("Calle Moral, Arequipa") y el crédito
 * de la fuente de datos. No sabe de dónde salen los nombres: usa solo casos de uso del dominio.
 */
class UbicacionViewModel(
    private val obtenerNombreUbicacion: ObtenerNombreUbicacionUseCase,
    private val obtenerAtribucionDatos: ObtenerAtribucionDatosUseCase
) : ViewModel() {

    /** Nombre del punto, junto con las coordenadas a las que corresponde (para no mostrar uno viejo). */
    data class NombrePunto(val lat: Double, val lng: Double, val nombre: String)

    private val _origen = MutableStateFlow<NombrePunto?>(null)
    val origen: StateFlow<NombrePunto?> = _origen

    private val _destino = MutableStateFlow<NombrePunto?>(null)
    val destino: StateFlow<NombrePunto?> = _destino

    private val _atribucion = MutableStateFlow<AtribucionDatos?>(null)
    val atribucion: StateFlow<AtribucionDatos?> = _atribucion

    private var trabajoOrigen: Job? = null
    private var trabajoDestino: Job? = null

    init {
        viewModelScope.launch {
            _atribucion.value = try {
                obtenerAtribucionDatos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    fun nombrarOrigen(lat: Double?, lng: Double?) {
        trabajoOrigen?.cancel()
        trabajoOrigen = nombrar(lat, lng, _origen)
    }

    fun nombrarDestino(lat: Double?, lng: Double?) {
        trabajoDestino?.cancel()
        trabajoDestino = nombrar(lat, lng, _destino)
    }

    private fun nombrar(lat: Double?, lng: Double?, destinoEstado: MutableStateFlow<NombrePunto?>): Job? {
        if (lat == null || lng == null) {
            destinoEstado.value = null
            return null
        }
        if (destinoEstado.value?.let { it.lat == lat && it.lng == lng } == true) return null
        return viewModelScope.launch {
            // El repositorio nunca lanza excepción: en el peor caso devuelve "zona de Arequipa"
            destinoEstado.value = NombrePunto(lat, lng, obtenerNombreUbicacion(lat, lng))
        }
    }
}
