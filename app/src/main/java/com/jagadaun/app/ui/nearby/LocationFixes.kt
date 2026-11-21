package com.jagadaun.app.ui.nearby

import android.Manifest
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
import com.jagadaun.app.data.nearby.NearbyException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lokasi perkiraan untuk fitur sekitar. Dipakai layar sekitar dan Beranda, jadi tinggal di sini
 * bersama logika lain fitur tersebut; tidak ada riwayat lokasi yang disimpan.
 */
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
