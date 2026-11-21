package com.jagadaun.app.data.nearby

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jagadaun.app.domain.NearbyPhoto
import com.jagadaun.app.domain.NearbyPlants
import com.jagadaun.app.domain.NearbySpecies
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The cache must survive a round trip, reject tampered data through the shared parser, and
 * never keep a previous day's answer.
 */
@RunWith(RobolectricTestRunner::class)
class NearbyCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val parser = NearbyClient(context)::parseCached
    private val cache = NearbyCache(context, parser)
    private val directory = File(context.filesDir, "nearby-cache")

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `latest result survives for the home screen and is validated on read`() {
        cache.writeLatest(sample())
        val restored = cache.readLatest()
        assertEquals(10, restored?.radiusKm)
        assertEquals(sample().species.map { it.scientificName }, restored?.species?.map { it.scientificName })
        assertEquals("Putri malu", restored?.species?.first()?.commonName)
    }

    @Test
    fun `a corrupt latest file degrades to a miss instead of rendering bad data`() {
        directory.mkdirs()
        File(directory, "latest.json").writeText("{not json", Charsets.UTF_8)
        assertNull(cache.readLatest())
        assertTrue("corrupt cache must be deleted", !File(directory, "latest.json").exists())
    }

    @Test
    fun `latest file with an unsupported radius is rejected`() {
        directory.mkdirs()
        File(directory, "latest.json").writeText(
            JSONObject().put("species", org.json.JSONArray())
                .put("radiusKm", 999).put("totalRecords", 0).toString(),
            Charsets.UTF_8,
        )
        assertNull(cache.readLatest())
    }

    @Test
    fun `writing a new day keeps the latest file for the home screen`() {
        cache.writeLatest(sample())
        cache.write(-6.2, 106.816, 10, sample(), day = "2026-11-22")
        assertTrue("latest.json must survive day pruning", File(directory, "latest.json").isFile)
    }

    @Test
    fun `a written result reads back identically`() {
        cache.write(-6.2, 106.816, 10, sample(), day = "2026-11-21")
        val restored = cache.read(-6.2, 106.816, 10, day = "2026-11-21")
        assertEquals(sample().totalRecords, restored?.totalRecords)
        assertEquals(sample().species.map { it.scientificName }, restored?.species?.map { it.scientificName })
        assertEquals("Putri malu", restored?.species?.first()?.commonName)
        // The parser keeps the attribution URL verbatim; the UI shortens it for display.
        assertEquals(
            "creativecommons.org/licenses/by-nc/4.0",
            restored?.species?.first()?.photo?.license,
        )
    }

    @Test
    fun `a different day or radius is a miss`() {
        cache.write(-6.2, 106.816, 10, sample(), day = "2026-11-20")
        assertNull(cache.read(-6.2, 106.816, 10, day = "2026-11-21"))
        assertNull(cache.read(-6.2, 106.816, 25, day = "2026-11-20"))
    }

    @Test
    fun `nearby coordinates share one entry because the key is rounded`() {
        cache.write(-6.2001, 106.8161, 10, sample(), day = "2026-11-21")
        assertTrue(cache.read(-6.2012, 106.8174, 10, day = "2026-11-21") != null)
    }

    @Test
    fun `tampered cached json is discarded instead of shown`() {
        cache.write(-6.2, 106.816, 10, sample(), day = "2026-11-21")
        val file = directory.listFiles()!!.single()
        val broken = JSONObject(file.readText())
        broken.getJSONArray("species").getJSONObject(0).put("latitude", 999)
        file.writeText(broken.toString())

        assertNull(cache.read(-6.2, 106.816, 10, day = "2026-11-21"))
        assertTrue("corrupt entry is deleted", directory.listFiles()!!.isEmpty())
    }

    @Test
    fun `clear removes every cached day`() {
        cache.write(-6.2, 106.816, 10, sample(), day = "2026-11-21")
        cache.clear()
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    private fun sample() = NearbyPlants(
        species = listOf(
            NearbySpecies(
                scientificName = "Mimosa pudica",
                commonName = "Putri malu",
                observations = 2,
                latitude = -6.21,
                longitude = 106.82,
                description = "Tumbuhan yang daunnya menutup saat disentuh.",
                wikiUrl = "https://id.wikipedia.org/wiki/Putri_malu",
                photo = NearbyPhoto(
                    thumbUrl = "https://inaturalist-open-data.s3.amazonaws.com/photos/1/small.jpg",
                    url = "https://inaturalist-open-data.s3.amazonaws.com/photos/1/medium.jpg",
                    license = "creativecommons.org/licenses/by-nc/4.0",
                    credit = "Budi",
                ),
            ),
        ),
        radiusKm = 10,
        totalRecords = 2,
    )
}
