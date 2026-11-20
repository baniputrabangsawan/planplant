package com.jagadaun.app.ui.nearby

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagadaun.app.data.nearby.NearbyException
import com.jagadaun.app.data.nearby.NearbySource
import com.jagadaun.app.domain.NearbyPlants
import com.jagadaun.app.domain.NearbySpecies
import com.jagadaun.app.ui.theme.JagaDaunTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screen flow on a device with canned data: the fake source records each request, so a wrong
 * radius or a selection that does not reach the UI fails the assertion.
 */
@RunWith(AndroidJUnit4::class)
class NearbyScreenTest {
    @get:Rule val compose = createComposeRule()

    private class FakeSource(var failure: String? = null) : NearbySource {
        val radii = mutableListOf<Int>()
        val points = mutableListOf<Pair<Double, Double>>()

        override suspend fun nearby(latitude: Double, longitude: Double, radiusKm: Int): NearbyPlants {
            radii += radiusKm
            points += latitude to longitude
            failure?.let { throw NearbyException(it) }
            return sample(radiusKm)
        }
    }

    private val fixes = object : LocationFixes {
        override suspend fun current(): Location = Location("test").apply {
            latitude = -6.2
            longitude = 106.816
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun showScreen(source: FakeSource) {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        compose.setContent {
            JagaDaunTheme {
                NearbyScreen(
                    onBack = {},
                    viewModelFactory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return NearbyViewModel(app, source, fixes) as T
                        }
                    },
                )
            }
        }
    }

    @Test
    fun openingTheScreenSearchesTheDefaultRadiusAndShowsThePlantNames() {
        val source = FakeSource()
        showScreen(source)
        compose.waitUntil(10_000) { source.radii.isNotEmpty() }
        assertEquals(listOf(10), source.radii)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("2 catatan publik dalam radius 10 km").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("2 catatan publik dalam radius 10 km").assertIsDisplayed()
        compose.onNodeWithText("Putri malu").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mimosa pudica").assertIsDisplayed()
    }

    @Test
    fun choosingAnotherRadiusRequestsThatRadiusWithTheSameCoordinates() {
        val source = FakeSource()
        showScreen(source)
        compose.waitUntil(10_000) { source.radii.isNotEmpty() }
        compose.onNodeWithText("25 km").performScrollTo().performClick()
        compose.waitUntil(10_000) { source.radii.size >= 2 }
        assertEquals(listOf(10, 25), source.radii)
        assertEquals(source.points[0], source.points[1])
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("2 catatan publik dalam radius 25 km").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("2 catatan publik dalam radius 25 km").assertIsDisplayed()
    }

    @Test
    fun selectingAMarkerOpensThatSpeciesDetail() {
        val source = FakeSource()
        showScreen(source)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Titik 2: Puring").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Titik 2: Puring").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tutup detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tutup detail").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buka di aplikasi peta").assertIsDisplayed()
    }

    @Test
    fun aFailedSearchShowsTheMessageAndARecoveryAction() {
        val source = FakeSource(failure = "Data tanaman sekitar tidak valid. Silakan coba lagi.")
        showScreen(source)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Pencarian belum berhasil").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Data tanaman sekitar tidak valid. Silakan coba lagi.").assertIsDisplayed()
        compose.onNodeWithText("Coba lagi").assertIsDisplayed()
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
