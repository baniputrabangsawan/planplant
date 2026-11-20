package com.jagadaun.app.ui.nearby

import com.jagadaun.app.domain.NearbyPlants
import com.jagadaun.app.domain.NearbySpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure state transitions behind the nearby screen: these are the rules a device test would
 * otherwise be the only witness to, so they are asserted directly.
 */
class NearbyStateTest {
    @Test
    fun `changing radius clears the previous result and selection`() {
        val loaded = NearbyUiState(
            latitude = -6.2,
            longitude = 106.816,
            radiusKm = 10,
            result = sample(10),
            selectedIndex = 1,
        )
        val changed = loaded.withRadius(25)
        assertEquals(25, changed.radiusKm)
        assertNull(changed.result)
        assertEquals(-1, changed.selectedIndex)
        assertTrue("coordinates are kept so the query can reuse them", changed.loading)
        assertEquals(-6.2, changed.latitude!!, 0.0)
    }

    @Test
    fun `changing radius before any coordinates does not start a query`() {
        val changed = NearbyUiState().withRadius(5)
        assertEquals(5, changed.radiusKm)
        assertFalse(changed.loading)
        assertNull(changed.result)
    }

    @Test
    fun `a finished search clears the selection`() {
        val result = NearbyUiState(loading = true, radiusKm = 25, selectedIndex = 1)
            .withResult(sample(25))
        assertFalse(result.loading)
        assertEquals(25, result.result?.radiusKm)
        assertEquals(-1, result.selectedIndex)
        assertNull(result.error)
    }

    @Test
    fun `selection accepts only indexes that exist`() {
        val base = NearbyUiState(result = sample(10))
        assertEquals(1, base.withSelection(1, 2).selectedIndex)
        assertEquals(-1, base.withSelection(2, 2).selectedIndex)
        assertEquals(-1, base.withSelection(-1, 2).selectedIndex)
        assertEquals(-1, base.withSelection(Int.MAX_VALUE, 2).selectedIndex)
    }

    @Test
    fun `a result never pairs with a radius it was not requested for`() {
        val tenKm = sample(10)
        val stale = NearbyUiState(radiusKm = 10, result = tenKm)
            .withRadius(25)
            .withResult(sample(25))
        assertEquals(stale.radiusKm, stale.result?.radiusKm)
    }

    @Test
    fun `manual coordinates outside the accepted range are rejected before any work`() {
        val base = NearbyUiState(radiusKm = 10)
        for (pair in listOf(91.0 to 0.0, -91.0 to 0.0, 0.0 to 181.0, 0.0 to -181.0)) {
            val rejected = base.withManualCoordinates(pair.first, pair.second)
            assertEquals(NearbyErrorKind.LOCATION_UNAVAILABLE, rejected.errorKind)
            assertNull(rejected.latitude)
        }
        val accepted = base.withManualCoordinates(-6.2, 106.816)
        assertEquals(-6.2, accepted.latitude!!, 0.0)
        assertEquals(106.816, accepted.longitude!!, 0.0)
        assertNull(accepted.errorKind)
    }

    @Test
    fun `a cached result is labelled and a fresh one is not`() {
        val cached = NearbyUiState().withResult(sample(10)).copy(fromCache = true)
        assertTrue(cached.fromCache)
        val fresh = NearbyUiState().withResult(sample(10)).copy(fromCache = false)
        assertFalse(fresh.fromCache)
    }

    private companion object {
        fun sample(radiusKm: Int) = NearbyPlants(
            species = listOf(
                NearbySpecies("Mimosa pudica", "Putri malu", 1, -6.21, 106.82),
                NearbySpecies("Codiaeum variegatum", "Puring", 1, -6.23, 106.84),
            ),
            radiusKm = radiusKm,
            totalRecords = 2,
        )
    }
}
