package com.sitp.arequipa.data.local.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import com.sitp.arequipa.domain.model.Coordenada
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Ubicación del teléfono con el GPS propio de Android (LocationManager): sin Google Play Services,
 * sin internet y sin costo. Usa el GPS y, si está disponible, la red del teléfono para una primera
 * posición más rápida. Sin permiso devuelve null / un flujo vacío (nunca lanza SecurityException).
 */
class UbicacionDispositivoSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun tienePermiso(): Boolean =
        PERMISOS.any { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }

    private fun proveedoresActivos(): List<String> =
        PROVEEDORES.filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission") // se verifica con tienePermiso()
    private fun ultimaConocida(): Location? {
        if (!tienePermiso()) return null
        return PROVEEDORES.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Última posición si es reciente; si no, pide una lectura nueva (máximo [ESPERA_MS] ms). */
    @SuppressLint("MissingPermission")
    suspend fun ubicacionActual(): Coordenada? {
        if (!tienePermiso()) return null
        val ultima = ultimaConocida()
        if (ultima != null && System.currentTimeMillis() - ultima.time < RECIENTE_MS) return ultima.aCoordenada()
        val proveedor = proveedoresActivos().firstOrNull() ?: return ultima?.aCoordenada()
        val nueva = withTimeoutOrNull(ESPERA_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancelar = CancellationSignal()
                cont.invokeOnCancellation { cancelar.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager, proveedor, cancelar, ContextCompat.getMainExecutor(appContext)
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
        return (nueva ?: ultima)?.aCoordenada()
    }

    /** Posiciones del GPS (y de la red, si está activa) mientras se escuche el flujo. */
    @SuppressLint("MissingPermission")
    fun seguirUbicacion(): Flow<Coordenada> {
        if (!tienePermiso()) return emptyFlow()
        return callbackFlow {
            ultimaConocida()?.let { trySend(it.aCoordenada()) }
            val oyente = LocationListenerCompat { location -> trySend(location.aCoordenada()) }
            for (proveedor in proveedoresActivos()) {
                runCatching {
                    locationManager.requestLocationUpdates(proveedor, INTERVALO_MS, DISTANCIA_M, oyente, Looper.getMainLooper())
                }
            }
            awaitClose { locationManager.removeUpdates(oyente) }
        }
    }

    private fun Location.aCoordenada() = Coordenada(latitude, longitude)

    private companion object {
        val PERMISOS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val PROVEEDORES = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val RECIENTE_MS = 2 * 60 * 1000L
        const val ESPERA_MS = 10_000L
        const val INTERVALO_MS = 3_000L
        const val DISTANCIA_M = 5f
    }
}
