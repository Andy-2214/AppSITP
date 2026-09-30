package com.sitp.arequipa.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sitp.arequipa.presentation.auth.AuthViewModel
import com.sitp.arequipa.presentation.busqueda.BusquedaViewModel
import com.sitp.arequipa.presentation.comentarios.ComentarioViewModel
import com.sitp.arequipa.presentation.historial.HistorialViewModel
import com.sitp.arequipa.presentation.perfil.PerfilViewModel

class AppViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(
                    loginUseCase = container.loginUseCase,
                    registerUseCase = container.registerUseCase,
                    recuperarPasswordUseCase = container.recuperarPasswordUseCase,
                    reenviarVerificacionUseCase = container.reenviarVerificacionUseCase,
                    logoutUseCase = container.logoutUseCase,
                    verificarSesionUseCase = container.verificarSesionUseCase
                ) as T

            modelClass.isAssignableFrom(HistorialViewModel::class.java) ->
                HistorialViewModel(
                    obtenerSesionUseCase = container.obtenerSesionUseCase,
                    guardarBusquedaUseCase = container.guardarBusquedaUseCase,
                    cargarHistorialUseCase = container.cargarHistorialUseCase,
                    eliminarBusquedaUseCase = container.eliminarBusquedaUseCase,
                    cargarFavoritosUseCase = container.cargarFavoritosUseCase,
                    guardarFavoritoUseCase = container.guardarFavoritoUseCase,
                    eliminarFavoritoUseCase = container.eliminarFavoritoUseCase
                ) as T

            modelClass.isAssignableFrom(ComentarioViewModel::class.java) ->
                ComentarioViewModel(
                    obtenerSesionUseCase = container.obtenerSesionUseCase,
                    publicarComentarioUseCase = container.publicarComentarioUseCase,
                    cargarComentariosUseCase = container.cargarComentariosUseCase
                ) as T

            modelClass.isAssignableFrom(PerfilViewModel::class.java) ->
                PerfilViewModel(
                    obtenerSesionUseCase = container.obtenerSesionUseCase,
                    obtenerPerfilUseCase = container.obtenerPerfilUseCase,
                    actualizarNombreUseCase = container.actualizarNombreUseCase
                ) as T

            modelClass.isAssignableFrom(BusquedaViewModel::class.java) ->
                BusquedaViewModel(
                    routeRepository = container.routeRepository,
                    buscarRutaUseCase = container.buscarRutaUseCase,
                    recomendarRutaIAUseCase = container.recomendarRutaIAUseCase,
                    sincronizarRutasUseCase = container.sincronizarRutasUseCase
                ) as T

            else -> throw IllegalArgumentException("ViewModel no soportado: ${modelClass.name}")
        }
    }
}