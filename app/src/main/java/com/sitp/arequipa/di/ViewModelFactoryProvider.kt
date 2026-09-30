package com.sitp.arequipa.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider

/**
 * Provee la [ViewModelProvider.Factory] única del AppContainer desde cualquier
 * composable, sin pasar el contexto manualmente por cada pantalla.
 */
@Composable
fun provideViewModelFactory(): ViewModelProvider.Factory {
    val application = LocalContext.current.applicationContext
    return remember(application) { AppContainer.getInstance(application).viewModelFactory }
}