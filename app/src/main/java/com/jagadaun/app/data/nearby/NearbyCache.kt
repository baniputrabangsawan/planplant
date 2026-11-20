package com.jagadaun.app.data.nearby

import android.content.Context
import com.jagadaun.app.domain.NearbyPhoto
import com.jagadaun.app.domain.NearbyPlants
import com.jagadaun.app.domain.NearbySpecies
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the last successful search for one place, one radius, and one UTC day.
 *
 * Mirrors PictureThis's `HomeNearByPlantsCache` + `kHomeNearByPlantsDateCacheKey`, but this
 * store holds only public record data: no coordinates beyond the rounded lookup key, and no
 * user identity. Every read is re-validated by [NearbyClient.parseCached], so a corrupt or
 * edited file degrades to a miss instead of reaching the UI.
 */
class NearbyCache(context: Context, private val parser: (String, Int) -> NearbyPlants) {
    private val directory = File(context.applicationContext.filesDir, "nearby-cache")
    private val today: String get() = LocalDate.now(ZoneOffset.UTC).toString()

    /** Rounded to ~1 km so the key stores a neighbourhood, not an exact position. */
    private fun key(latitude: Double, longitude: Double, radiusKm: Int, day: String): String =
        String.format(java.util.Locale.ROOT, "%.2f_%.2f_%d_%s", latitude, longitude, radiusKm, day)

    fun read(latitude: Double, longitude: Double, radiusKm: Int, day: String = today): NearbyPlants? {
        val file = File(directory, "${key(latitude, longitude, radiusKm, day)}.json")
        if (!file.isFile || file.length() > MAX_BYTES) return null
        return try {
            parser(file.readText(Charsets.UTF_8), radiusKm)
        } catch (_: NearbyException) {
            file.delete()
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    fun write(latitude: Double, longitude: Double, radiusKm: Int, plants: NearbyPlants, day: String = today) {
        try {
            directory.mkdirs()
            // Old days and other radii are not useful once the location changes.
            directory.listFiles()?.forEach { file ->
                if (!file.name.startsWith(key(latitude, longitude, radiusKm, day))) file.delete()
            }
            val target = File(directory, "${key(latitude, longitude, radiusKm, day)}.json")
            val body = encode(plants, radiusKm)
            target.writeText(body, Charsets.UTF_8)
        } catch (_: java.io.IOException) {
            // A cache failure must never break a successful search.
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    /** Writes the same envelope the Worker returns so the shared parser can read it back. */
    private fun encode(plants: NearbyPlants, radiusKm: Int): String {
        val species = JSONArray()
        plants.species.forEach { item ->
            species.put(JSONObject().apply {
                put("scientificName", item.scientificName)
                put("commonName", item.commonName ?: JSONObject.NULL)
                put("observations", item.observations)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("description", item.description ?: JSONObject.NULL)
                put("wikiUrl", item.wikiUrl ?: JSONObject.NULL)
                put("photo", item.photo?.let { photo ->
                    JSONObject().apply {
                        put("thumbUrl", photo.thumbUrl)
                        put("url", photo.url)
                        put("license", photo.license)
                        put("credit", photo.credit)
                    }
                } ?: JSONObject.NULL)
            })
        }
        return JSONObject().apply {
            put("species", species)
            put("radiusKm", radiusKm)
            put("totalRecords", plants.totalRecords)
            put("attribution", JSONObject().apply {
                put("name", "GBIF")
                put("url", "https://www.gbif.org/")
            })
        }.toString()
    }

    private companion object {
        const val MAX_BYTES = 256L * 1024
    }
}
