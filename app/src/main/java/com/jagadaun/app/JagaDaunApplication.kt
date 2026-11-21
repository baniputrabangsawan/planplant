package com.jagadaun.app

import android.app.Application
import coil.ComponentRegistry
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.data.photos.PlantPhotoFetcher
import com.jagadaun.app.data.photos.PlantPhotoKeyer

/**
 * Registry gambar Jaga Daun: komponen bawaan Coil ditambah fetcher foto privat.
 *
 * Memakai `components { }` saja mengganti registry default; tanpa fetcher jaringan, thumbnail
 * `https://` di Tanaman di sekitar gagal dimuat tanpa error yang terlihat. Dipisah dari
 * [JagaDaunApplication] supaya bisa diuji tanpa membuka database.
 */
internal fun plantImageComponents(photos: PhotoStore): ComponentRegistry =
    ComponentRegistry().newBuilder()
        .add(PlantPhotoKeyer())
        .add(PlantPhotoFetcher.Factory(photos))
        .build()

class JagaDaunApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).components(plantImageComponents(container.photos)).build()
}
