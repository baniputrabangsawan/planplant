package com.jagadaun.app

import android.app.Application

class JagaDaunApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
