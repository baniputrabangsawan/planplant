package com.jagadaun.app.ui.nearby

import android.Manifest
import java.io.FileInputStream
import org.junit.Before
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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

    @Before
    fun grantLocationForTheRealPermissionGate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant $packageName ${Manifest.permission.ACCESS_COARSE_LOCATION}",
        ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }

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
                            return NearbyViewModel(app, source, fixes, cache = null) as T
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
        compose.onNodeWithText("2 catatan publik dalam radius 10 km").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Putri malu"))
        compose.onNodeWithText("Putri malu").assertIsDisplayed()
        compose.onNodeWithText("Mimosa pudica").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("2 catatan publik dalam radius 25 km").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectingAMarkerOpensThatSpeciesDetail() {
        val source = FakeSource()
        showScreen(source)
        compose.waitUntil(10_000) { source.radii.isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Titik 2: Puring"))
        compose.onNodeWithContentDescription("Titik 2: Puring").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tutup detail"))
        compose.onNodeWithText("Tutup detail").assertIsDisplayed()
        compose.onNodeWithText("Buka di aplikasi peta").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFailedSearchShowsTheMessageAndARecoveryAction() {
        val source = FakeSource(failure = "Data tanaman sekitar tidak valid. Silakan coba lagi.")
        showScreen(source)
        compose.waitUntil(10_000) { source.radii.isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasText("Data tanaman sekitar tidak valid. Silakan coba lagi."),
        )
        compose.onNodeWithText("Data tanaman sekitar tidak valid. Silakan coba lagi.").assertIsDisplayed()
        compose.onNodeWithText("Coba lagi").performScrollTo().assertIsDisplayed()
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

    /**
     * Jalur produksi: tanpa factory, layar memakai `viewModel()` dan `AndroidViewModelFactory`.
     * Sebelum perbaikan, test ini tidak perlu assertion apa pun — pembuatan ViewModel langsung
     * melempar RuntimeException dan seluruh aplikasi berhenti.
     */
    @Test
    fun screenOpensWithTheDefaultViewModelFactory() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        // Cache dibersihkan agar tidak ada hasil nyata yang dipakai; lokasi memakai izin nyata.
        java.io.File(app.filesDir, "nearby-cache").deleteRecursively()
        compose.setContent {
            JagaDaunTheme {
                NearbyScreen(onBack = {})
            }
        }
        compose.onNodeWithText("Tanaman di sekitar").assertIsDisplayed()
    }
}
