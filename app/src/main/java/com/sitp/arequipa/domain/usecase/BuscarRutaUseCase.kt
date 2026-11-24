package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.model.ParametrosBusqueda
import com.sitp.arequipa.domain.model.Ruta
import com.sitp.arequipa.domain.repository.CacheBusquedasRepository
import com.sitp.arequipa.domain.ruteo.MotorRutas
import com.sitp.arequipa.domain.ruteo.RedTransporte
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

/**
 * HU-01 / Algoritmo central de la tesis (ADR-010): búsqueda por rondas basada en frecuencias con
 * líneas atractivas. Kotlin puro (sin Android): se prueba con pruebas unitarias, sin emulador.
 *
 * Devuelve un [Flow]: primero las 2 mejores opciones del modo elegido y, a medida que aparecen, el resto
 * (hasta 5 por modo). La última emisión trae `completa = true`. Si la misma búsqueda ya se hizo con las
 * mismas rutas y parámetros, responde al instante desde la caché.
 *
 * La IA no participa: el cálculo es 100 % de la app (ADR-011).
 */
class BuscarRutaUseCase(
    private val cache: CacheBusquedasRepository? = null,
    private val parametros: ParametrosBusqueda = ParametrosBusqueda()
) {
    /** Red armada para la última versión de las rutas (armarla cuesta; buscar en ella es rápido). */
    @Volatile private var redActual: Pair<Long, RedTransporte>? = null

    operator fun invoke(
        rutas: List<Ruta>,
        origen: Coordenada,
        destino: Coordenada,
        modo: ModoBusqueda
    ): Flow<OpcionesBusqueda> = flow {
        if (rutas.isEmpty()) {
            emit(OpcionesBusqueda.VACIA_COMPLETA)
            return@flow
        }
        val firma = firmaRutas(rutas)
        val clave = claveCache(firma, origen, destino)
        leerCache(clave)?.let {
            emit(it.copy(completa = true, desdeCache = true))
            return@flow
        }
        val red = obtenerRed(firma, rutas)
        val final = MotorRutas(red, parametros).buscar(origen, destino, modo) { emit(it) }
        if (!final.vacia) guardarCache(clave, final)
    }.flowOn(Dispatchers.Default)

    private fun obtenerRed(firma: Long, rutas: List<Ruta>): RedTransporte {
        redActual?.let { (f, red) -> if (f == firma) return red }
        return RedTransporte.construir(rutas, parametros).also { redActual = firma to it }
    }

    private suspend fun leerCache(clave: String): OpcionesBusqueda? = try {
        cache?.leer(clave)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // una caché dañada nunca impide buscar
    }

    private suspend fun guardarCache(clave: String, opciones: OpcionesBusqueda) {
        try {
            cache?.guardar(clave, opciones)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sin caché la app sigue funcionando
        }
    }

    /** Clave de la caché: versión del algoritmo y parámetros, versión de las rutas y pines redondeados (~11 m). */
    internal fun claveCache(firma: Long, origen: Coordenada, destino: Coordenada): String =
        String.format(
            Locale.US, "v%d|p%d|r%d|%.4f,%.4f|%.4f,%.4f",
            ParametrosBusqueda.VERSION_ALGORITMO, parametros.hashCode(), firma,
            origen.lat, origen.lng, destino.lat, destino.lng
        )

    companion object {
        /**
         * Huella de las rutas: cambia si cambia cualquier ruta (código, frecuencia o recorrido).
         * Es estable entre aperturas de la app, por eso sirve como «versión» para la caché.
         */
        fun firmaRutas(rutas: List<Ruta>): Long {
            var h = -3750763034362895579L // FNV-1a de 64 bits
            fun mezclar(v: Long) { h = (h xor v) * 1099511628211L }
            fun mezclarTexto(t: String) = t.forEach { mezclar(it.code.toLong()) }
            fun mezclarDouble(d: Double) = mezclar(java.lang.Double.doubleToLongBits(d))
            for (r in rutas.sortedBy { it.codigo }) {
                mezclarTexto(r.codigo); mezclarTexto("|"); mezclarTexto(r.frecuencia)
                for (lista in listOf(r.coordsIda, r.coordsVuelta)) {
                    mezclar(lista.size.toLong())
                    for ((la, ln) in lista) { mezclarDouble(la); mezclarDouble(ln) }
                }
            }
            return h
        }
    }
}
