package com.jagadaun.app.data.nearby

import android.content.Context
import com.jagadaun.app.BuildConfig
import com.jagadaun.app.domain.NearbyPhoto
import com.jagadaun.app.domain.NearbyPlants
import com.jagadaun.app.domain.NearbySpecies
import com.jagadaun.app.domain.PlantNames
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject

/** Only safe Indonesian copy crosses the network boundary; never exposes upstream bodies. */
class NearbyException(message: String) : Exception(message)

/** The only data operation the nearby screen needs; a fake supplies canned results in tests. */
interface NearbySource {
    suspend fun nearby(latitude: Double, longitude: Double, radiusKm: Int): NearbyPlants
}

class NearbyClient(private val context: Context) : NearbySource {
    private val preferences = context.applicationContext
        .getSharedPreferences("identification", Context.MODE_PRIVATE)

    override suspend fun nearby(latitude: Double, longitude: Double, radiusKm: Int): NearbyPlants {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(radiusKm == 5 || radiusKm == 10 || radiusKm == 25)
        return withTimeoutOrNull(40_000) {
            request(latitude, longitude, radiusKm)
        } ?: throw NearbyException("Pencarian terlalu lama. Silakan coba lagi.")
    }

    /**
     * Pencarian cepat untuk Beranda: memakai lokasi perkiraan yang sudah tersedia di perangkat.
     * Pemanggil WAJIB memeriksa izin lebih dulu; fungsi ini tidak meminta izin.
     */
    suspend fun nearbyDefault(radiusKm: Int = 10): NearbyPlants {
        val location = com.jagadaun.app.ui.nearby.SystemLocationFixes(context).current()
        return nearby(location.latitude, location.longitude, radiusKm)
    }

    private suspend fun request(latitude: Double, longitude: Double, radiusKm: Int): NearbyPlants =
        withContext(Dispatchers.IO) {
            val configured = try {
                URL(BuildConfig.IDENTIFICATION_URL)
            } catch (_: IOException) {
                throw NearbyException("Layanan sekitar belum tersedia.")
            } catch (_: IllegalArgumentException) {
                throw NearbyException("Layanan sekitar belum tersedia.")
            }
            if (configured.protocol != "https" || configured.host.isBlank() ||
                configured.userInfo != null || configured.ref != null ||
                BuildConfig.IDENTIFICATION_TOKEN.isBlank() ||
                BuildConfig.IDENTIFICATION_TOKEN.any { it.isISOControl() }
            ) throw NearbyException("Layanan sekitar belum tersedia.")
            val endpoint = URL(
                configured.protocol, configured.host, configured.port,
                "/v1/nearby?lat=$latitude&lon=$longitude&radius=$radiusKm",
            )
            val coroutineContext = currentCoroutineContext()
            coroutineContext.ensureActive()
            val id = deviceId()
            val connection = try {
                endpoint.openConnection() as HttpsURLConnection
            } catch (_: IOException) {
                throw NearbyException("Tidak dapat terhubung. Periksa koneksi internet Anda.")
            } catch (_: SecurityException) {
                throw NearbyException("Tidak dapat terhubung. Periksa koneksi internet Anda.")
            }
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { connection.disconnect() }
                try {
                    coroutineContext.ensureActive()
                    connection.apply {
                        requestMethod = "POST"
                        connectTimeout = 12_000
                        readTimeout = 30_000
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("Authorization", "Bearer ${BuildConfig.IDENTIFICATION_TOKEN}")
                        setRequestProperty("X-Device-Id", id)
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "JagaDaun/0.1")
                        setRequestProperty("Connection", "close")
                    }
                    val status = connection.responseCode
                    if (status != 200) throw NearbyException(when (status) {
                        429 -> "Layanan sedang sibuk. Tunggu sebentar lalu coba lagi."
                        401, 403 -> "Akses layanan sekitar belum tersedia."
                        else -> "Data tanaman sekitar belum dapat dimuat. Silakan coba lagi."
                    })
                    if (connection.contentLengthLong > MAX_RESPONSE_BYTES) invalidResponse()
                    val body = connection.inputStream.use { input ->
                        val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
                        var count = 0
                        while (count < bytes.size) {
                            coroutineContext.ensureActive()
                            val read = input.read(bytes, count, bytes.size - count)
                            if (read == -1) break
                            count += read
                        }
                        if (count > MAX_RESPONSE_BYTES) invalidResponse()
                        String(bytes, 0, count, Charsets.UTF_8)
                    }
                    coroutineContext.ensureActive()
                    continuation.resume(parse(body, radiusKm))
                } catch (error: CancellationException) {
                    continuation.cancel(error)
                } catch (error: NearbyException) {
                    continuation.resumeWithException(error)
                } catch (_: SocketTimeoutException) {
                    continuation.resumeWithException(NearbyException("Pencarian terlalu lama. Silakan coba lagi."))
                } catch (_: UnknownHostException) {
                    continuation.resumeWithException(NearbyException("Tidak ada koneksi internet. Silakan coba lagi."))
                } catch (_: IOException) {
                    continuation.resumeWithException(NearbyException("Tidak dapat terhubung. Periksa koneksi internet Anda."))
                } catch (_: SecurityException) {
                    continuation.resumeWithException(NearbyException("Akses layanan sekitar tidak tersedia."))
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun deviceId(): String = synchronized(preferences) {
        preferences.getString("device_id", null)?.let { return@synchronized it }
        val id = UUID.randomUUID().toString()
        if (!preferences.edit().putString("device_id", id).commit()) {
            throw NearbyException("Identitas perangkat tidak dapat disimpan. Silakan coba lagi.")
        }
        id
    }

    /**
     * Re-validates cached JSON with the same rules as a live response, so a stale or edited
     * cache file can never bypass coordinate, count, or attribution checks.
     */
    internal fun parseCached(body: String, requestedRadius: Int): NearbyPlants = parse(body, requestedRadius)

    private fun parse(body: String, requestedRadius: Int): NearbyPlants = try {
        val root = JSONObject(body)
        val radius = integer(root, "radiusKm", 5, 25)
        if (radius != requestedRadius) invalidResponse()
        val total = integer(root, "totalRecords", 0, Int.MAX_VALUE)
        val attribution = root.getJSONObject("attribution")
        if (attribution.getString("name") != "GBIF" ||
            attribution.getString("url") != "https://www.gbif.org/"
        ) invalidResponse()
        val array = root.getJSONArray("species")
        if (array.length() > 12) invalidResponse()
        var sum = 0L
        val names = HashSet<String>()
        val species = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val scientific = name(item.get("scientificName"))
            if (!names.add(scientific)) invalidResponse()
            val suppliedCommon = if (item.isNull("commonName")) null else name(item.get("commonName"))
            val count = integer(item, "observations", 1, total)
            sum += count
            val lat = coordinate(item, "latitude", -90.0, 90.0)
            val lon = coordinate(item, "longitude", -180.0, 180.0)
            val descriptionText = description(item)
            val wikiUrl = wikipediaUrl(item)
            val photoRecord = photo(item)
            NearbySpecies(
                scientificName = scientific,
                commonName = PlantNames.commonName(scientific) ?: suppliedCommon,
                observations = count,
                latitude = lat,
                longitude = lon,
                description = descriptionText,
                wikiUrl = wikiUrl,
                photo = photoRecord,
            )
        }
        if (sum > total || (total == 0 && species.isNotEmpty())) invalidResponse()
        // Pengguna meminta setiap kartu punya gambar. Spesies tanpa foto berlisensi CC tidak
        // ditampilkan sama sekali; menyembunyikan kartu lebih jujur daripada ubin kosong.
        // Filter ada di parser agar cache lama ikut bersih tanpa menunggu kedaluwarsa.
        val withPhoto = species.filter { it.photo != null }
        NearbyPlants(withPhoto, radius, total)
    } catch (_: JSONException) {
        invalidResponse()
    }

    private fun integer(json: JSONObject, key: String, minimum: Int, maximum: Int): Int {
        val raw = json.get(key) as? Number ?: invalidResponse()
        val number = raw.toDouble()
        if (!number.isFinite() || number < minimum || number > maximum || number % 1.0 != 0.0) {
            invalidResponse()
        }
        return number.toInt()
    }

    private fun coordinate(json: JSONObject, key: String, minimum: Double, maximum: Double): Double {
        val number = (json.get(key) as? Number)?.toDouble() ?: invalidResponse()
        if (!number.isFinite() || number !in minimum..maximum) invalidResponse()
        return number
    }

    private fun name(raw: Any): String {
        val value = (raw as? String)?.trim() ?: invalidResponse()
        if (value.isEmpty() || value.length > 200 || value.any { it.isISOControl() }) invalidResponse()
        return value
    }

    private fun optionalText(json: JSONObject, key: String, maximum: Int): String? {
        val value = (json.opt(key) as? String)?.trim() ?: return null
        if (value.isEmpty() || value.length > maximum || value.any { it.isISOControl() }) return null
        return value
    }

    /** Collapses line breaks so a description can never break layout; oversize text is dropped. */
    private fun description(json: JSONObject): String? {
        val raw = json.opt("description") as? String ?: return null
        // Collapse first so a wrapped summary stays readable instead of being dropped for length.
        val value = raw.replace(WHITESPACE, " ").trim()
        if (value.isEmpty() || value.length > 600 || value.any { it.isISOControl() }) return null
        return value
    }

    private fun wikipediaUrl(json: JSONObject): String? {
        val value = optionalText(json, "wikiUrl", 300) ?: return null
        val host = parseHttps(value)?.host?.lowercase() ?: return null
        if (host != "id.wikipedia.org") return null
        return value
    }

    /**
     * A photo is dropped, never fatal, when its licence or credit cannot be shown to a reader.
     * Callers must render [NearbyPhoto.license] and [NearbyPhoto.credit] next to the image.
     */
    private fun photo(item: JSONObject): NearbyPhoto? {
        val raw = item.optJSONObject("photo") ?: return null
        val thumbUrl = photoUrl(raw.opt("thumbUrl")) ?: return null
        val url = photoUrl(raw.opt("url")) ?: return null
        val license = optionalText(raw, "license", 120) ?: return null
        val credit = optionalText(raw, "credit", 120) ?: return null
        // Compacting ignores separators: "CC BY-NC 4.0", "cc-by-sa" and "CCBY" all become ccby….
        val marker = license.lowercase().filter { it.isLetterOrDigit() }
        if (!marker.startsWith("ccby") && !isCreativeCommonsUrl(license)) return null
        return NearbyPhoto(thumbUrl, url, license, credit)
    }

    private fun photoUrl(raw: Any?): String? {
        val value = (raw as? String)?.trim() ?: return null
        if (value.length > 2048) return null
        val host = parseHttps(value)?.host?.lowercase() ?: return null
        return when (host) {
            "bs.plantnet.org", "static.inaturalist.org" -> value
            else -> if (host.endsWith(".amazonaws.com")) value else null
        }
    }

    /** The worker strips the scheme from licence URLs, so both forms must count as attribution. */
    private fun isCreativeCommonsUrl(value: String): Boolean {
        val host = parseHttps(value)?.host?.lowercase()
            ?: value.lowercase().removePrefix("https://").removePrefix("http://")
                .substringBefore('/').substringBefore(':')
        return host == "creativecommons.org" || host.endsWith(".creativecommons.org")
    }

    private fun parseHttps(value: String): URL? {
        if (value.isEmpty() || value.any { it.isISOControl() }) return null
        val url = try {
            URL(value)
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null) return null
        if (url.port != -1 && url.port != 443) return null
        return url
    }

    private fun invalidResponse(): Nothing =
        throw NearbyException("Data tanaman sekitar tidak valid. Silakan coba lagi.")

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
        val WHITESPACE = Regex("\\s+")
    }
}
