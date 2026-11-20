package com.jagadaun.app.data.identification

import android.content.Context
import android.graphics.Bitmap
import com.jagadaun.app.BuildConfig
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.domain.HealthResult
import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantAnalysisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Pl@ntNet's documented maximum for one plant individual. */
const val MAX_ANALYSIS_PHOTOS = 5

/** Uploads freshly encoded bitmaps only for an explicit plant analysis or focused check. */
class IdentificationClient(context: Context, private val photos: PhotoStore) {
    private val preferences = context.applicationContext.getSharedPreferences("identification", Context.MODE_PRIVATE)
    suspend fun analyze(photoRefs: List<String>): PlantAnalysisResult {
        if (photoRefs.size !in 1..MAX_HEALTH_IMAGES) throw IdentificationException("INVALID_IMAGE")
        return request(photoRefs, "/v1/analyze", ::parsePlantAnalysisResponse)
    }

    suspend fun identify(photoRef: String): IdentificationResult =
        request(listOf(photoRef), null, ::parseIdentificationResponse)

    suspend fun checkHealth(photoRefs: List<String>): HealthResult {
        if (photoRefs.size !in 1..MAX_HEALTH_IMAGES) throw IdentificationException("INVALID_IMAGE")
        return request(photoRefs, "/v1/health-check", ::parseHealthResponse)
    }

    private suspend fun <T : Any> request(
        photoRefs: List<String>,
        path: String?,
        parseResponse: (Int, String) -> T,
    ): T = withTimeoutOrNull(45_000) {
        requestWithinDeadline(photoRefs, path, parseResponse)
    } ?: throw IdentificationException("TIMEOUT")

    private suspend fun <T : Any> requestWithinDeadline(
        photoRefs: List<String>,
        path: String?,
        parseResponse: (Int, String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val configuredEndpoint = try {
            URL(BuildConfig.IDENTIFICATION_URL)
        } catch (_: IllegalArgumentException) {
            throw IdentificationException("NOT_CONFIGURED")
        } catch (_: IOException) {
            throw IdentificationException("NOT_CONFIGURED")
        }
        if (configuredEndpoint.protocol != "https" || configuredEndpoint.host.isBlank() ||
            configuredEndpoint.userInfo != null || configuredEndpoint.ref != null ||
            BuildConfig.IDENTIFICATION_TOKEN.isBlank() ||
            BuildConfig.IDENTIFICATION_TOKEN.any { it.isISOControl() }
        ) {
            throw IdentificationException("NOT_CONFIGURED")
        }
        // A new path cannot change the configured HTTPS origin or retain a query/fragment.
        val endpoint = if (path == null) configuredEndpoint else {
            URL(configuredEndpoint.protocol, configuredEndpoint.host, configuredEndpoint.port, path)
        }
        val images = photoRefs.map { photoRef ->
            currentCoroutineContext().ensureActive()
            // Pl@ntNet resizes the smaller side to 1280 px and never upscales, so send the
            // stored 2048 px draft instead of a 1600 px cap that can fall below its target.
            val bitmap = photos.readBitmap(photoRef, 2048) ?: throw IdentificationException("INVALID_IMAGE")
            try {
                BoundedJpegOutput().also {
                    // Provider recommends JPEG quality 90 for identification uploads.
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) {
                        throw IdentificationException("INVALID_IMAGE")
                    }
                    if (it.size() == 0) throw IdentificationException("INVALID_IMAGE")
                }
            } finally {
                bitmap.recycle()
            }
        }
        val boundary = if (path == null) null else "JagaDaun-${UUID.randomUUID()}"
        val partHeaders = if (boundary == null) emptyList() else images.indices.map { index ->
            ("--$boundary\r\nContent-Disposition: form-data; name=\"images\"; " +
                "filename=\"plant-${index + 1}.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                .toByteArray(Charsets.US_ASCII)
        }
        val ending = boundary?.let { "--$it--\r\n".toByteArray(Charsets.US_ASCII) }
        val contentLength = if (boundary == null) images.single().size() else {
            images.indices.sumOf { partHeaders[it].size + images[it].size() + CRLF.size } +
                checkNotNull(ending).size
        }
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        val deviceId = deviceId()
        val connection = try {
            endpoint.openConnection() as HttpsURLConnection
        } catch (_: IOException) {
            throw IdentificationException("CONNECTION_ERROR")
        } catch (_: SecurityException) {
            throw IdentificationException("CONNECTION_ERROR")
        }
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { connection.disconnect() }
            var connected = false
            try {
                coroutineContext.ensureActive()
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 35_000
                    instanceFollowRedirects = false
                    useCaches = false
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.IDENTIFICATION_TOKEN}")
                    setRequestProperty("X-Device-Id", deviceId)
                    setRequestProperty(
                        "Content-Type",
                        if (boundary == null) "image/jpeg" else "multipart/form-data; boundary=$boundary",
                    )
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JagaDaun/0.1")
                    setRequestProperty("Connection", "close")
                    setFixedLengthStreamingMode(contentLength)
                }
                connection.outputStream.use { output ->
                    connected = true
                    for ((index, image) in images.withIndex()) {
                        coroutineContext.ensureActive()
                        if (boundary != null) output.write(partHeaders[index])
                        image.writeTo(output)
                        if (boundary != null) output.write(CRLF)
                    }
                    if (ending != null) output.write(ending)
                }
                coroutineContext.ensureActive()
                val status = connection.responseCode
                if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
                    throw IdentificationException("INVALID_RESPONSE")
                }
                val stream = if (status == 200) connection.inputStream else connection.errorStream
                val body = stream?.use { readBoundedResponse(it) { coroutineContext.ensureActive() } }.orEmpty()
                coroutineContext.ensureActive()
                continuation.resume(parseResponse(status, body))
            } catch (error: CancellationException) {
                continuation.cancel(error)
            } catch (error: IdentificationException) {
                continuation.resumeWithException(error)
            } catch (_: SocketTimeoutException) {
                continuation.resumeWithException(
                    IdentificationException(if (connected) "READ_TIMEOUT" else "CONNECT_TIMEOUT"),
                )
            } catch (_: UnknownHostException) {
                continuation.resumeWithException(IdentificationException("OFFLINE"))
            } catch (_: NoRouteToHostException) {
                continuation.resumeWithException(IdentificationException("OFFLINE"))
            } catch (_: ConnectException) {
                continuation.resumeWithException(IdentificationException("CONNECTION_ERROR"))
            } catch (_: IOException) {
                continuation.resumeWithException(IdentificationException("CONNECTION_ERROR"))
            } catch (_: SecurityException) {
                continuation.resumeWithException(IdentificationException("CONNECTION_ERROR"))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun deviceId(): String = synchronized(preferences) {
        preferences.getString("device_id", null)?.let { return@synchronized it }
        val id = UUID.randomUUID().toString()
        if (!preferences.edit().putString("device_id", id).commit()) {
            throw IdentificationException("DEVICE_STORAGE")
        }
        id
    }

    private class BoundedJpegOutput : ByteArrayOutputStream(256 * 1024) {
        override fun write(value: Int) {
            if (count >= MAX_IMAGE_BYTES) throw IdentificationException("IMAGE_TOO_LARGE")
            super.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > MAX_IMAGE_BYTES - count) throw IdentificationException("IMAGE_TOO_LARGE")
            super.write(bytes, offset, length)
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        const val MAX_HEALTH_IMAGES = MAX_ANALYSIS_PHOTOS
        val CRLF = byteArrayOf(13, 10)
    }
}
