package com.jagadaun.app.data.photos

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Referensi privat, bukan URL/File bebas; PhotoStore tetap memeriksa batas path dan dekode. */
internal data class PlantPhotoSource(val ref: String)

internal class PlantPhotoKeyer : Keyer<PlantPhotoSource> {
    override fun key(data: PlantPhotoSource, options: Options): String = data.ref
}

internal class PlantPhotoFetcher(
    private val data: PlantPhotoSource,
    private val photos: PhotoStore,
) : Fetcher {
    override suspend fun fetch(): SourceResult = withContext(Dispatchers.IO) {
        // Coil menentukan sampling/precision dari ukuran dan aspect ratio yang benar.
        // Bitmap yang dikecilkan di bawah target akan selalu ditolak oleh cache Coil.
        val file = photos.displayFile(data.ref)
        SourceResult(
            source = ImageSource(file = file.toOkioPath()),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val photos: PhotoStore) : Fetcher.Factory<PlantPhotoSource> {
        override fun create(data: PlantPhotoSource, options: Options, imageLoader: ImageLoader): Fetcher =
            PlantPhotoFetcher(data, photos)
    }
}
