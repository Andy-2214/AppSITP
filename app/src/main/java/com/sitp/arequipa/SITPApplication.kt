package com.sitp.arequipa

import android.app.Application
import com.sitp.arequipa.di.AppContainer

class SITPApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.getInstance(this)
    }
}