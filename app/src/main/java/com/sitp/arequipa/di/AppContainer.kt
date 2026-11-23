package com.sitp.arequipa.di

import android.content.Context
import com.sitp.arequipa.data.local.catalogo.CatalogoDatabase
import com.sitp.arequipa.data.local.catalogo.CatalogoLugaresSource
import com.sitp.arequipa.data.remote.FirebaseAuthSource
import com.sitp.arequipa.data.remote.FirestoreComentarioSource
import com.sitp.arequipa.data.remote.FirestoreHistorialSource
import com.sitp.arequipa.data.remote.GroqAISource
import com.sitp.arequipa.data.repository.AuthRepositoryImpl
import com.sitp.arequipa.data.repository.ComentarioRepositoryImpl
import com.sitp.arequipa.data.repository.HistorialRepositoryImpl
import com.sitp.arequipa.data.repository.LugaresRepositoryImpl
import com.sitp.arequipa.data.repository.RecomendacionRepositoryImpl
import com.sitp.arequipa.data.repository.RouteRepositoryImpl
import com.sitp.arequipa.data.repository.UsuarioRepositoryImpl
import com.sitp.arequipa.domain.repository.AuthRepository
import com.sitp.arequipa.domain.repository.ComentarioRepository
import com.sitp.arequipa.domain.repository.HistorialRepository
import com.sitp.arequipa.domain.repository.LugaresRepository
import com.sitp.arequipa.domain.repository.RecomendacionRepository
import com.sitp.arequipa.domain.repository.RouteRepository
import com.sitp.arequipa.domain.repository.UsuarioRepository
import com.sitp.arequipa.domain.usecase.ActualizarNombreUseCase
import com.sitp.arequipa.domain.usecase.BuscarRutaUseCase
import com.sitp.arequipa.domain.usecase.CargarComentariosUseCase
import com.sitp.arequipa.domain.usecase.CargarFavoritosUseCase
import com.sitp.arequipa.domain.usecase.CargarHistorialUseCase
import com.sitp.arequipa.domain.usecase.EliminarBusquedaUseCase
import com.sitp.arequipa.domain.usecase.EliminarFavoritoUseCase
import com.sitp.arequipa.domain.usecase.GuardarBusquedaUseCase
import com.sitp.arequipa.domain.usecase.GuardarFavoritoUseCase
import com.sitp.arequipa.domain.usecase.LoginUseCase
import com.sitp.arequipa.domain.usecase.LogoutUseCase
import com.sitp.arequipa.domain.usecase.ObtenerAtribucionDatosUseCase
import com.sitp.arequipa.domain.usecase.ObtenerNombreUbicacionUseCase
import com.sitp.arequipa.domain.usecase.ObtenerPerfilUseCase
import com.sitp.arequipa.domain.usecase.ObtenerSesionUseCase
import com.sitp.arequipa.domain.usecase.PublicarComentarioUseCase
import com.sitp.arequipa.domain.usecase.RecomendarRutaIAUseCase
import com.sitp.arequipa.domain.usecase.RecuperarPasswordUseCase
import com.sitp.arequipa.domain.usecase.ReenviarVerificacionUseCase
import com.sitp.arequipa.domain.usecase.RegisterUseCase
import com.sitp.arequipa.domain.usecase.SincronizarRutasUseCase
import com.sitp.arequipa.domain.usecase.VerificarSesionUseCase

/**
 * Contenedor manual de dependencias (Composition Root).
 * Construye DataSources → Repositorios → UseCases y provee la ViewModelFactory.
 * Inicializado en [com.sitp.arequipa.SITPApplication].
 */
class AppContainer private constructor(context: Context) {

    private val firebaseAuthSource = FirebaseAuthSource()
    private val firestoreHistorialSource = FirestoreHistorialSource()
    private val firestoreComentarioSource = FirestoreComentarioSource()
    // Catálogo local de lugares (ADR-001/002). La base se abre recién en la primera consulta.
    private val catalogoLugaresSource =
        CatalogoLugaresSource(CatalogoDatabase.getDatabase(context).catalogoDao())
    private val groqAISource = GroqAISource()

    val authRepository: AuthRepository = AuthRepositoryImpl(firebaseAuthSource)
    val usuarioRepository: UsuarioRepository = UsuarioRepositoryImpl(firebaseAuthSource)
    val historialRepository: HistorialRepository = HistorialRepositoryImpl(firestoreHistorialSource)
    val comentarioRepository: ComentarioRepository = ComentarioRepositoryImpl(firestoreComentarioSource)
    val lugaresRepository: LugaresRepository = LugaresRepositoryImpl(catalogoLugaresSource)
    val recomendacionRepository: RecomendacionRepository = RecomendacionRepositoryImpl(groqAISource)
    val routeRepository: RouteRepository = RouteRepositoryImpl.getInstance(context)

    val loginUseCase = LoginUseCase(authRepository)
    val registerUseCase = RegisterUseCase(authRepository)
    val recuperarPasswordUseCase = RecuperarPasswordUseCase(authRepository)
    val reenviarVerificacionUseCase = ReenviarVerificacionUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)
    val obtenerSesionUseCase = ObtenerSesionUseCase(authRepository)
    val verificarSesionUseCase = VerificarSesionUseCase(authRepository)

    val guardarBusquedaUseCase = GuardarBusquedaUseCase(historialRepository)
    val cargarHistorialUseCase = CargarHistorialUseCase(historialRepository)
    val eliminarBusquedaUseCase = EliminarBusquedaUseCase(historialRepository)
    val cargarFavoritosUseCase = CargarFavoritosUseCase(historialRepository)
    val guardarFavoritoUseCase = GuardarFavoritoUseCase(historialRepository)
    val eliminarFavoritoUseCase = EliminarFavoritoUseCase(historialRepository)

    val publicarComentarioUseCase = PublicarComentarioUseCase(comentarioRepository)
    val cargarComentariosUseCase = CargarComentariosUseCase(comentarioRepository)

    val obtenerNombreUbicacionUseCase = ObtenerNombreUbicacionUseCase(lugaresRepository)
    val obtenerAtribucionDatosUseCase = ObtenerAtribucionDatosUseCase(lugaresRepository)
    val recomendarRutaIAUseCase = RecomendarRutaIAUseCase(recomendacionRepository, lugaresRepository)
    val buscarRutaUseCase = BuscarRutaUseCase()
    val sincronizarRutasUseCase = SincronizarRutasUseCase(routeRepository)

    val obtenerPerfilUseCase = ObtenerPerfilUseCase(usuarioRepository)
    val actualizarNombreUseCase = ActualizarNombreUseCase(usuarioRepository)

    val viewModelFactory = AppViewModelFactory(this)

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            INSTANCE ?: synchronized(this) {
                val instance = AppContainer(context.applicationContext)
                INSTANCE = instance
                instance
            }
    }
}