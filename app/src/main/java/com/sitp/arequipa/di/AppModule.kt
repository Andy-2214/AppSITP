package com.sitp.arequipa.di

import com.sitp.arequipa.application.usecases.auth.LoginUseCase
import com.sitp.arequipa.application.usecases.auth.LogoutUseCase
import com.sitp.arequipa.application.usecases.auth.RegisterUseCase
import com.sitp.arequipa.application.usecases.auth.ResetPasswordUseCase
import com.sitp.arequipa.application.usecases.routes.GetOfficialRoutesUseCase
import com.sitp.arequipa.application.usecases.routes.FindOptimalRouteUseCase
import com.sitp.arequipa.application.usecases.routes.QueryRouteWithAIUseCase
import com.sitp.arequipa.application.usecases.search.SaveSearchHistoryUseCase
import com.sitp.arequipa.application.usecases.search.GetSearchHistoryUseCase
import com.sitp.arequipa.application.usecases.favorites.SaveFavoriteRouteUseCase
import com.sitp.arequipa.application.usecases.favorites.GetFavoriteRoutesUseCase
import com.sitp.arequipa.application.usecases.favorites.DeleteFavoriteRouteUseCase
import com.sitp.arequipa.application.usecases.comments.SubmitCommentUseCase
import com.sitp.arequipa.application.usecases.comments.GetRouteCommentsUseCase
import com.sitp.arequipa.domain.repositories.AuthRepository
import com.sitp.arequipa.domain.repositories.CommentRepository
import com.sitp.arequipa.domain.repositories.FavoriteRepository
import com.sitp.arequipa.domain.repositories.RouteRepository
import com.sitp.arequipa.domain.repositories.SearchHistoryRepository
import com.sitp.arequipa.domain.repositories.UserRepository
import com.sitp.arequipa.domain.services.AIRouteService
import com.sitp.arequipa.domain.services.RouteOptimizerService
import com.sitp.arequipa.infrastructure.ai.GeminiRouteService
import com.sitp.arequipa.infrastructure.algorithm.GraphRouteOptimizer
import com.sitp.arequipa.infrastructure.firebase.FirebaseAuthRepository
import com.sitp.arequipa.infrastructure.firebase.FirebaseCommentRepository
import com.sitp.arequipa.infrastructure.firebase.FirebaseFavoriteRepository
import com.sitp.arequipa.infrastructure.firebase.FirebaseHistoryRepository
import com.sitp.arequipa.infrastructure.firebase.FirebaseRouteRepository
import com.sitp.arequipa.infrastructure.firebase.FirebaseUserRepository
import com.sitp.arequipa.presentation.auth.AuthViewModel
import com.sitp.arequipa.presentation.map.MapViewModel
import com.sitp.arequipa.presentation.perfil.PerfilViewModel

/**
 * Módulo de inyección de dependencias manual.
 * Construye el grafo completo de dependencias: infraestructura → dominio → casos de uso → ViewModels.
 *
 * Para cambiar Firebase por otra implementación basta con cambiar aquí, sin tocar nada más.
 */
object AppModule {

    // ─── Infrastructure ───────────────────────────────────────────────────────
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }
    val routeRepository: RouteRepository by lazy { FirebaseRouteRepository() }
    val userRepository: UserRepository by lazy { FirebaseUserRepository() }
    val searchHistoryRepository: SearchHistoryRepository by lazy { FirebaseHistoryRepository() }
    val favoriteRepository: FavoriteRepository by lazy { FirebaseFavoriteRepository() }
    val commentRepository: CommentRepository by lazy { FirebaseCommentRepository() }

    val routeOptimizerService: RouteOptimizerService by lazy {
        GraphRouteOptimizer(routeRepository)
    }
    val aiRouteService: AIRouteService by lazy { GeminiRouteService() }

    // ─── Use Cases ────────────────────────────────────────────────────────────
    val loginUseCase by lazy { LoginUseCase(authRepository) }
    val registerUseCase by lazy { RegisterUseCase(authRepository) }
    val logoutUseCase by lazy { LogoutUseCase(authRepository) }
    val resetPasswordUseCase by lazy { ResetPasswordUseCase(authRepository) }

    val getOfficialRoutesUseCase by lazy { GetOfficialRoutesUseCase(routeRepository) }
    val findOptimalRouteUseCase by lazy { FindOptimalRouteUseCase(routeOptimizerService) }
    val queryRouteWithAIUseCase by lazy { QueryRouteWithAIUseCase(aiRouteService) }

    val saveSearchHistoryUseCase by lazy { SaveSearchHistoryUseCase(searchHistoryRepository) }
    val getSearchHistoryUseCase by lazy { GetSearchHistoryUseCase(searchHistoryRepository) }

    val saveFavoriteRouteUseCase by lazy { SaveFavoriteRouteUseCase(favoriteRepository) }
    val getFavoriteRoutesUseCase by lazy { GetFavoriteRoutesUseCase(favoriteRepository) }
    val deleteFavoriteRouteUseCase by lazy { DeleteFavoriteRouteUseCase(favoriteRepository) }

    val submitCommentUseCase by lazy { SubmitCommentUseCase(commentRepository) }
    val getRouteCommentsUseCase by lazy { GetRouteCommentsUseCase(commentRepository) }

    // ─── ViewModels ───────────────────────────────────────────────────────────
    fun provideAuthViewModel() = AuthViewModel(
        loginUseCase,
        registerUseCase,
        logoutUseCase,
        resetPasswordUseCase
    )

    fun provideMapViewModel() = MapViewModel(getOfficialRoutesUseCase)

    fun providePerfilViewModel() = PerfilViewModel(userRepository, authRepository)
}
