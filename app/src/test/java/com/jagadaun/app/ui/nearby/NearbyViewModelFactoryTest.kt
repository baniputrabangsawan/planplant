package com.jagadaun.app.ui.nearby

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.ViewModelInitializer
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regresi: layar memakai `viewModel()` tanpa factory, jadi `AndroidViewModelFactory` harus bisa
 * membuat [NearbyViewModel] hanya dari `Application`. Parameter default Kotlin tidak membuat
 * overload JVM; tanpa konstruktor `(Application)` aplikasi berhenti saat layar dibuka.
 */
@RunWith(RobolectricTestRunner::class)
class NearbyViewModelFactoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultAndroidFactoryCreatesViewModelFromApplicationAlone() {
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        val provider = ViewModelProvider(ViewModelStore(), factory)
        assertNotNull(provider[NearbyViewModel::class.java])
    }
}
