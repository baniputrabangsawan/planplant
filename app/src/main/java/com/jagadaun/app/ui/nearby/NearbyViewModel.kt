package com.jagadaun.app.ui.nearby

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagadaun.app.data.nearby.NearbyCache
import com.jagadaun.app.data.nearby.NearbyClient
import com.jagadaun.app.data.nearby.NearbySource
import com.jagadaun.app.data.nearby.NearbyException
import com.jagadaun.app.domain.NearbyPlants
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Why a search failed, so the screen can offer the right recovery instead of one generic sheet. */
enum class NearbyErrorKind { LOCATION_OFF, LOCATION_UNAVAILABLE, PERMISSION, NETWORK, PROVIDER, CACHE_MISS }

data class NearbyUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val errorKind: NearbyErrorKind? = null,
    /** True when the shown result came from the on-disk cache, not from a fresh request. */
    val fromCache: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Int = 10,
    val result: NearbyPlants? = null,
    /** Index into [result] species, or -1 when nothing is selected. */
    val selectedIndex: Int = -1,
)

/**
 * Radius transitions are pure so they can be asserted without a device: a new radius never
 * keeps the previous result, and it only re-queries once coordinates are known.
 */
internal fun NearbyUiState.withRadius(radiusKm: Int): NearbyUiState =
    copy(
        radiusKm = radiusKm,
        result = null,
        error = null,
        selectedIndex = -1,
        loading = latitude != null && longitude != null,
    )

/** A finished search always clears the selection so a marker cannot outlive its result. */
internal fun NearbyUiState.withResult(result: NearbyPlants): NearbyUiState =
    copy(loading = false, result = result, error = null, selectedIndex = -1)

/**
 * Manual coordinates are validated before they enter state, so an impossible latitude or
 * longitude can never reach the network, the cache, or the map projection.
 */
internal fun NearbyUiState.withManualCoordinates(latitude: Double, longitude: Double): NearbyUiState {
    val valid = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0
    return if (!valid) {
        copy(
            loading = false,
            error = "Koordinat tidak valid. Lintang -90 sampai 90, bujur -180 sampai 180.",
            errorKind = NearbyErrorKind.LOCATION_UNAVAILABLE,
        )
    } else {
        copy(
            loading = true,
            error = null,
            errorKind = null,
            selectedIndex = -1,
            latitude = latitude,
            longitude = longitude,
            result = null,
            fromCache = false,
        )
    }
}

/** Out-of-range indexes clear the selection rather than pointing at a species that is gone. */
internal fun NearbyUiState.withSelection(index: Int, speciesCount: Int): NearbyUiState =
    copy(selectedIndex = if (index in 0 until speciesCount) index else -1)

/** Location stays in memory and is requested only by an explicit UI action. */
class NearbyViewModel(
    application: Application,
    /** Overridden only by UI tests so no real location or network is used. */
    private val client: NearbySource = NearbyClient(application),
    private val fixes: LocationFixes = SystemLocationFixes(application),
    private val cache: NearbyCache? = NearbyCache(application, NearbyClient(application)::parseCached),
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = mutableState.asStateFlow()
    private var query: Job? = null

    fun locate() {
        query?.cancel()
        mutableState.value = mutableState.value.copy(
            loading = true, error = null, selectedIndex = -1,
        )
        query = viewModelScope.launch {
            try {
                val location = fixes.current()
                ensureActive()
                val radius = mutableState.value.radiusKm
                mutableState.value = mutableState.value.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    result = null,
                    selectedIndex = -1,
                )
                load(location.latitude, location.longitude, radius, allowCache = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: NearbyException) {
                mutableState.value = mutableState.value.copy(
                    loading = false, error = error.message, errorKind = kindOf(error),
                )
            } catch (_: SecurityException) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = "Izinkan akses lokasi perkiraan untuk mencari tanaman sekitar.",
                    errorKind = NearbyErrorKind.PERMISSION,
                )
            } catch (_: IllegalArgumentException) {
                mutableState.value = mutableState.value.copy(
                    loading = false, error = "Lokasi belum tersedia. Silakan coba lagi.",
                    errorKind = NearbyErrorKind.LOCATION_UNAVAILABLE,
                )
            }
        }
    }

    fun setRadius(radiusKm: Int) {
        if ((radiusKm != 5 && radiusKm != 10 && radiusKm != 25) ||
            radiusKm == mutableState.value.radiusKm
        ) return
        query?.cancel()
        val current = mutableState.value
        val latitude = current.latitude
        val longitude = current.longitude
        mutableState.value = current.withRadius(radiusKm)
        if (latitude == null || longitude == null) return
        query = viewModelScope.launch {
            try {
                load(latitude, longitude, radiusKm)
            } catch (error: CancellationException) {
                throw error
            } catch (error: NearbyException) {
                mutableState.value = mutableState.value.copy(loading = false, error = error.message)
            }
        }
    }

    /**
     * Manual coordinates, for a place the device cannot locate: a typed lat/lon pair.
     * Bounds are enforced here so an out-of-range value never reaches the network or cache.
     */
    fun searchAt(latitude: Double, longitude: Double) {
        query?.cancel()
        val next = mutableState.value.withManualCoordinates(latitude, longitude)
        mutableState.value = next
        if (next.latitude == null || next.longitude == null) return
        val radius = next.radiusKm
        query = viewModelScope.launch {
            try {
                load(latitude, longitude, radius, allowCache = false)
            } catch (error: CancellationException) {
                throw error
            }
        }
    }

    /** Deletes the daily cache for the current place, so the next search is definitely live. */
    fun forgetCachedResult() {
        val current = mutableState.value
        val latitude = current.latitude
        val longitude = current.longitude
        if (latitude != null && longitude != null) {
            cache?.clear()
        }
        mutableState.value = current.copy(fromCache = false)
    }

    private fun kindOf(error: NearbyException): NearbyErrorKind {
        val text = error.message.orEmpty()
        return when {
            text.contains("nonaktif", ignoreCase = true) -> NearbyErrorKind.LOCATION_OFF
            text.contains("lokasi", ignoreCase = true) -> NearbyErrorKind.LOCATION_UNAVAILABLE
            text.contains("internet", ignoreCase = true) ||
                text.contains("terhubung", ignoreCase = true) -> NearbyErrorKind.NETWORK
            text.contains("sibuk", ignoreCase = true) -> NearbyErrorKind.PROVIDER
            text.contains("valid", ignoreCase = true) -> NearbyErrorKind.PROVIDER
            else -> NearbyErrorKind.PROVIDER
        }
    }

    private suspend fun load(latitude: Double, longitude: Double, radiusKm: Int, allowCache: Boolean = true) {
        if (allowCache) {
            cache?.read(latitude, longitude, radiusKm)?.let { cached ->
                kotlin.coroutines.coroutineContext.ensureActive()
                mutableState.value = mutableState.value.withResult(cached).copy(fromCache = true)
                return
            }
        }
        val result = try {
            client.nearby(latitude, longitude, radiusKm)
        } catch (error: NearbyException) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                error = error.message,
                errorKind = NearbyErrorKind.CACHE_MISS,
            )
            return
        }
        kotlin.coroutines.coroutineContext.ensureActive()
        cache?.write(latitude, longitude, radiusKm, result)
        mutableState.value = mutableState.value.withResult(result).copy(fromCache = false)
    }

    /** Runs the same search as [locate] only when approximate location is already allowed. */
    fun locateIfPermitted() {
        if (query?.isActive == true) return
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        locate()
    }

    /** Out-of-range or absent results clear the selection instead of pointing at nothing. */
    fun select(speciesIndex: Int) {
        val species = mutableState.value.result?.species ?: return
        mutableState.value = mutableState.value.withSelection(speciesIndex, species.size)
    }

}

/** Coordinates for a search; overridden in tests so the UI never needs a real fix. */
interface LocationFixes {
    suspend fun current(): Location
}

/**
 * Production location source: coarse permission, enabled providers, and a bounded wait.
 * Nothing is cached, persisted, or requested in the background.
 */
class SystemLocationFixes(private val context: Context) : LocationFixes {
    override suspend fun current(): Location {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) throw SecurityException()
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw NearbyException("Lokasi tidak tersedia di perangkat ini.")
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw NearbyException("Lokasi perangkat nonaktif. Aktifkan lokasi lalu coba lagi.")
        }
        // GPS requires fine permission on older Android. Network/fused accept approximate location.
        val providers = manager.getProviders(true).filter {
            it == LocationManager.NETWORK_PROVIDER || it == "fused"
        }
        if (providers.isEmpty()) {
            throw NearbyException("Penyedia lokasi perkiraan belum tersedia. Aktifkan layanan lokasi lalu coba lagi.")
        }
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter(::isFresh)
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { return it }

        var listener: LocationListener? = null
        val location = try {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (continuation.isActive && isFresh(location)) continuation.resume(location)
                        }

                        override fun onProviderDisabled(provider: String) {
                            if (continuation.isActive && providers.none { manager.isProviderEnabled(it) }) {
                                continuation.resumeWithException(
                                    NearbyException("Lokasi perangkat nonaktif. Aktifkan lokasi lalu coba lagi."),
                                )
                            }
                        }

                        override fun onProviderEnabled(provider: String) = Unit

                        @Deprecated("Required for pre-Android R location callbacks")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }
                    listener = callback
                    continuation.invokeOnCancellation { manager.removeUpdates(callback) }
                    try {
                        for (provider in providers) {
                            if (!continuation.isActive) break
                            manager.requestLocationUpdates(provider, 0L, 0f, callback, Looper.getMainLooper())
                        }
                    } catch (error: SecurityException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } catch (_: IllegalArgumentException) {
                        if (continuation.isActive) continuation.resumeWithException(
                            NearbyException("Lokasi belum tersedia. Silakan coba lagi."),
                        )
                    }
                }
            }
        } finally {
            listener?.let { manager.removeUpdates(it) }
        }
        return location ?: throw NearbyException(
            "Lokasi belum ditemukan. Coba di area terbuka dan pastikan layanan lokasi aktif.",
        )
    }

    private fun isFresh(location: Location): Boolean {
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        // Approximate locations are throttled by Android; a two-minute limit rejects usable fixes.
        return age in 0..TimeUnit.MINUTES.toNanos(10) &&
            location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
            location.longitude.isFinite() && location.longitude in -180.0..180.0
    }
}
