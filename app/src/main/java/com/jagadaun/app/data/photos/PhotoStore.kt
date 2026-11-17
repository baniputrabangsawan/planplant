package com.jagadaun.app.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.UUID
import kotlin.math.max

/** App-private photo IO. Construct and call on an IO dispatcher, never the main thread. */
class PhotoStore(context: Context) {
    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.photos"
    private val drafts = privateDirectory(appContext.cacheDir, "photo-drafts")
    private val captures = privateDirectory(appContext.cacheDir, "photo-captures")
    private val saved = privateDirectory(appContext.filesDir, "photos")

    init {
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MILLIS
        cleanupOldFiles(drafts, cutoff)
        cleanupOldFiles(captures, cutoff)
    }

    fun newCameraFile(): File = newJpeg(captures)

    fun cameraUri(file: File): Uri {
        val capture = confinedFile(file.path, captures)
        require(capture.isFile) { "Camera capture does not exist" }
        return FileProvider.getUriForFile(appContext, authority, capture)
    }

    /** Consumes owned camera captures; picker originals are never modified. */
    fun importPhoto(uri: Uri): String {
        val capture = captureForUri(uri)
        var raw: File? = null
        var output: File? = null
        var bitmap: Bitmap? = null
        var complete = false
        try {
            require(uri.scheme == "content" || (uri.scheme == "file" && capture != null)) {
                "Only content URIs or private camera captures can be imported"
            }
            raw = newFile(drafts, "raw")
            val input = if (capture != null) {
                capture.inputStream()
            } else {
                appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open photo")
            }
            input.use { source ->
                FileOutputStream(raw).use { destination -> copyBounded(source, destination) }
            }
            bitmap = decode(raw, MAX_PHOTO_SIZE)
            output = newJpeg(drafts)
            FileOutputStream(output).use { destination ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, destination)) {
                    throw IOException("Cannot encode photo")
                }
                destination.fd.sync()
            }
            if (output.length() == 0L) throw IOException("Photo is empty")
            complete = true
            return output.absolutePath
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to import photo", error)
        } finally {
            bitmap?.recycle()
            raw?.delete()
            capture?.delete()
            if (!complete) output?.delete()
        }
    }

    /** Keeps the draft until the caller's database commit has succeeded. */
    fun persist(draftPath: String): String {
        val draft = confinedFile(draftPath, drafts)
        require(draft.extension == "jpg" && draft.isFile) { "Photo draft does not exist" }
        var output: File? = null
        var complete = false
        try {
            output = newJpeg(saved)
            draft.inputStream().use { source ->
                FileOutputStream(output).use { destination ->
                    copyBounded(source, destination)
                    destination.fd.sync()
                }
            }
            complete = true
            return output.absolutePath
        } finally {
            if (!complete) output?.delete()
        }
    }

    fun discardDraft(path: String?) {
        if (path == null) return
        deleteFile(confinedFile(path, drafts, captures))
    }

    fun deleteSaved(path: String?) {
        if (path == null) return
        deleteFile(confinedFile(path, saved))
    }

    fun isDraft(path: String): Boolean = try {
        confinedFile(path, drafts)
        true
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Missing, corrupt, foreign, or memory-constrained images have no display bitmap. */
    fun readBitmap(path: String, maxSize: Int): Bitmap? {
        require(maxSize > 0) { "Bitmap size must be positive" }
        return try {
            decode(confinedFile(path, drafts, saved, captures), maxSize.coerceAtMost(MAX_PHOTO_SIZE))
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun decode(file: File, maxSize: Int): Bitmap {
        if (!file.isFile || file.length() !in 1..MAX_IMPORT_BYTES) {
            throw IOException("Photo is missing, empty, or too large")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width > MAX_INPUT_DIMENSION ||
            height > MAX_INPUT_DIMENSION || width.toLong() * height > MAX_INPUT_PIXELS
        ) {
            throw IOException("Photo dimensions are invalid or too large")
        }
        var sample = 1
        while ((width.toLong() + sample - 1) / sample > maxSize ||
            (height.toLong() + sample - 1) / sample > maxSize
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options)
            ?: throw IOException("Photo cannot be decoded")
        try {
            val orientation = readOrientation(file)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            }
            val longest = max(bitmap.width, bitmap.height)
            if (longest > maxSize) {
                val scale = maxSize.toFloat() / longest
                matrix.postScale(scale, scale)
            }
            if (matrix.isIdentity) return bitmap
            val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (normalized !== bitmap) bitmap.recycle()
            return normalized
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun readOrientation(file: File): Int = try {
        file.inputStream().use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    } catch (_: IOException) {
        // Valid formats without framework-supported EXIF still decode normally.
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun captureForUri(uri: Uri): File? {
        if (uri.scheme == "file") {
            return confinedFile(uri.path ?: throw IllegalArgumentException("Missing capture path"), captures)
        }
        if (uri.scheme != "content" || uri.authority != authority) return null
        val name = uri.lastPathSegment ?: throw IllegalArgumentException("Missing capture name")
        val file = confinedFile(File(captures, name).path, captures)
        require(FileProvider.getUriForFile(appContext, authority, file) == uri) { "Invalid capture URI" }
        return file
    }

    private fun confinedFile(
        path: String,
        firstRoot: File,
        secondRoot: File? = null,
        thirdRoot: File? = null,
    ): File {
        val original = File(path)
        require(original.isAbsolute && !Files.isSymbolicLink(original.toPath())) { "Invalid photo path" }
        val file = original.canonicalFile
        val parent = file.parentFile
        require(
            parent == firstRoot ||
                (secondRoot != null && parent == secondRoot) ||
                (thirdRoot != null && parent == thirdRoot),
        ) { "Foreign photo path" }
        require(!file.isDirectory) { "Photo path is a directory" }
        return file
    }

    private fun privateDirectory(parent: File, name: String): File {
        val base = parent.canonicalFile
        val directory = File(base, name)
        if (Files.isSymbolicLink(directory.toPath()) || directory.canonicalFile.parentFile != base) {
            throw IOException("Invalid photo directory")
        }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create photo directory")
        return directory.canonicalFile
    }

    private fun newJpeg(directory: File): File = newFile(directory, "jpg")

    private fun newFile(directory: File, extension: String): File {
        val file = confinedFile(File(directory, "${UUID.randomUUID()}.$extension").path, directory)
        if (!file.createNewFile()) throw IOException("Cannot create photo file")
        return file
    }

    private fun copyBounded(source: InputStream, destination: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = source.read(buffer, 0, minOf(buffer.size.toLong(), MAX_IMPORT_BYTES - total + 1).toInt())
            if (count == -1) break
            if (count == 0) throw IOException("Cannot read photo")
            total += count
            if (total > MAX_IMPORT_BYTES) throw IOException("Photo exceeds 30 MiB")
            destination.write(buffer, 0, count)
        }
        if (total == 0L) throw IOException("Photo is empty")
    }

    private fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Cannot delete photo")
    }

    private fun cleanupOldFiles(directory: File, cutoff: Long) {
        try {
            Files.newDirectoryStream(directory.toPath()).use { entries ->
                for (entry in entries) {
                    if (Files.isSymbolicLink(entry)) continue
                    val file = entry.toFile().canonicalFile
                    if (file.parentFile == directory && file.isFile) {
                        val modified = file.lastModified()
                        if (modified > 0 && modified < cutoff) file.delete()
                    }
                }
            }
        } catch (_: IOException) {
            // Cache eviction is best-effort; never remove saved photos to recover space.
        } catch (_: java.nio.file.DirectoryIteratorException) {
            // A directory can become inaccessible while iterating its entries.
        } catch (_: SecurityException) {
            // A later explicit IO operation reports an inaccessible directory to its caller.
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 30L * 1024 * 1024
        const val MAX_PHOTO_SIZE = 2048
        const val MAX_INPUT_DIMENSION = 32_768
        const val MAX_INPUT_PIXELS = 100_000_000L
        const val JPEG_QUALITY = 90
        const val ORPHAN_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
