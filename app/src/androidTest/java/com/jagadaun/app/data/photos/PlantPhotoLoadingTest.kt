package com.jagadaun.app.data.photos

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlantPhotoLoadingTest {
    @Test
    fun sizedPhotoIsReusedAcrossRequestsWithoutAnotherDecode() = runBlocking {
        withLoader { context, root, loader ->
            val photo = File(root, "cache/photo-drafts/plant.jpg")
            writePhoto(photo)
            val request = ImageRequest.Builder(context)
                .data(PlantPhotoSource(photo.absolutePath)).size(128, 128).build()
            val first = loader.execute(request) as SuccessResult
            val bitmap = (first.drawable as BitmapDrawable).bitmap
            assertTrue(bitmap.width <= 128 && bitmap.height <= 128)
            val second = loader.execute(request) as SuccessResult
            assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
        }
    }

    @Test
    fun foreignPhotoNeverReachesTheRenderer() = runBlocking {
        withLoader { context, root, loader ->
            val foreign = File(root, "foreign.jpg")
            writePhoto(foreign)
            val result = loader.execute(ImageRequest.Builder(context)
                .data(PlantPhotoSource(foreign.absolutePath)).size(128, 128).build())
            assertTrue(result is ErrorResult)
        }
    }

    @Test
    fun symlinkInsideThePhotoDirectoryCannotEscapeIntoAnotherFile() = runBlocking {
        withLoader { context, root, loader ->
            val outside = File(root, "secret.jpg")
            writePhoto(outside)
            val link = File(root, "cache/photo-drafts/link.jpg")
            link.parentFile!!.mkdirs()
            java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
            val result = loader.execute(ImageRequest.Builder(context)
                .data(PlantPhotoSource(link.absolutePath)).size(128, 128).build())
            assertTrue(result is ErrorResult)
        }
    }

    private suspend fun withLoader(block: suspend (Context, File, ImageLoader) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "photo-loading-${UUID.randomUUID()}")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        }
        val loader = ImageLoader.Builder(context).components {
            add(PlantPhotoKeyer())
            add(PlantPhotoFetcher.Factory(PhotoStore(context)))
        }.build()
        try { block(context, root, loader) } finally {
            loader.shutdown()
            root.deleteRecursively()
        }
    }

    private fun writePhoto(file: File) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(512, 768, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally { bitmap.recycle() }
    }
}
