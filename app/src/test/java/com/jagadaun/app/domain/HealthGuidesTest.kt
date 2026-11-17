package com.jagadaun.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthGuidesTest {
    @Test
    fun `verified catalogue codes return appropriate suspected cause guides`() {
        mapOf(
            "1APHIF" to "Dugaan kutu daun",
            "APHIGO" to "Dugaan kutu daun",
            "MYZUPE" to "Dugaan kutu daun",
            "1PSEUF" to "Dugaan kutu putih",
            "PSECCI" to "Dugaan kutu putih",
            "TETRUR" to "Dugaan tungau laba-laba",
            "1TETRF" to "Dugaan tungau laba-laba",
            "OIDISP" to "Dugaan embun tepung",
            "PODOXA" to "Dugaan embun tepung",
            "LEVETA" to "Dugaan embun tepung",
            "COLLGL" to "Dugaan antraknosa",
            "GLOMCI" to "Dugaan antraknosa",
            "CERCSP" to "Dugaan bercak daun akibat cendawan",
            "SEPTLY" to "Dugaan bercak daun akibat cendawan",
            "PHYTCC" to "Dugaan busuk akar atau pangkal Phytophthora",
            "PHYTFU" to "Dugaan busuk akar atau pangkal Phytophthora",
        ).forEach { (code, title) ->
            assertEquals(code, title, HealthGuides.forCode(code)?.title)
        }
    }

    @Test
    fun `codes require exact curated match not prefix or label classification`() {
        listOf("", "UNKNOWN", "APHIGO_NEW", "aphigo", " APHIGO ", "Aphis gossypii", "powdery mildew").forEach {
            assertNull(it, HealthGuides.forCode(it))
        }
    }

    @Test
    fun `Phytophthora genus and foliar diseases never receive root rot guidance`() {
        listOf("PHYTSP", "1PHYTG", "PHYTIN", "PHYTRA", "PHYTOO", "PHYTPL").forEach {
            assertNull(it, HealthGuides.forCode(it))
        }
    }

    @Test
    fun `unrelated organisms with superficially similar names do not get guides`() {
        // Aphid moth, red rot of sugarcane, and false mites are not the curated classes.
        listOf("EPINRA", "GLOMTU", "DOLCFL", "1OOMYC").forEach {
            assertNull(it, HealthGuides.forCode(it))
        }
    }

}
