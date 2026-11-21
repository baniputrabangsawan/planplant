package com.jagadaun.app.ui.nearby

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.plantImageComponents
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regresi: loader gambar Jaga Daun harus tetap bisa memuat thumbnail `https://`. Komponen lokal
 * ditambahkan lewat registry; kalau registry itu menggantikan milik Coil, fetcher jaringan hilang
 * dan foto Tanaman di sekitar gagal dimuat tanpa error yang terlihat.
 */
@RunWith(RobolectricTestRunner::class)
class ImageLoaderFactoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun loaderKeepsNetworkFetchersAlongsideLocalPhotos() {
        val loader = ImageLoader.Builder(application)
            .components(plantImageComponents(PhotoStore(application)))
            .build()
        try {
            val fetchers = loader.components.fetcherFactories.map { it.first }
            assertTrue(
                "local photo fetcher missing",
                fetchers.any { it is com.jagadaun.app.data.photos.PlantPhotoFetcher.Factory },
            )
            assertTrue(
                "Coil network fetcher was dropped: https thumbnails cannot load",
                fetchers.any { it.javaClass.name.contains("HttpUriFetcher", ignoreCase = true) },
            )
        } finally {
            loader.shutdown()
        }
    }
}
