package com.jagadaun.app.domain

import com.jagadaun.app.data.local.PlantEntity
import com.jagadaun.app.data.repository.toDomain
import com.jagadaun.app.data.repository.toEntity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlantNamesTest {
    @Test
    fun `hibiscus species retain distinct familiar names`() {
        assertEquals("Kembang sepatu", PlantNames.commonName("Hibiscus rosa-sinensis"))
        assertEquals("Kembang sepatu gantung", PlantNames.commonName("Hibiscus schizopetalus"))
    }

    @Test
    fun `species lookup trims and ignores case using root locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("Kembang sepatu", PlantNames.commonName("  HIBISCUS ROSA-SINENSIS  "))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `lookup never guesses genus species or cultivar`() {
        listOf("", "Hibiscus", "Hibiscus sabdariffa", "Hibiscus rosa-sinensis L.", "Hibiscus rosa-sinensis 'Red'").forEach {
            assertNull(it, PlantNames.commonName(it))
        }
    }

    @Test
    fun `garden crops and houseplants have curated names`() {
        mapOf(
            "Capsicum annuum" to "Cabai",
            "Solanum lycopersicum" to "Tomat",
            "Solanum melongena" to "Terong",
            "Carica papaya" to "Pepaya",
            "Psidium guajava" to "Jambu biji",
            "Citrus × aurantiifolia" to "Jeruk nipis",
            "Citrus maxima" to "Jeruk bali",
            "Jasminum sambac" to "Melati",
            "Codiaeum variegatum" to "Puring",
            "Aloe vera" to "Lidah buaya",
            "Dracaena trifasciata" to "Lidah mertua",
            "Sansevieria trifasciata" to "Lidah mertua",
        ).forEach { (scientificName, expected) ->
            assertEquals(expected, PlantNames.commonName(scientificName))
        }
    }

    @Test
    fun `candidate prefers Indonesian alias then provider name then scientific name`() {
        assertEquals("Kembang sepatu", PlantCandidate("Hibiscus rosa-sinensis", "Chinese hibiscus", 0.8).displayName)
        assertEquals("Provider name", PlantCandidate("Unknown species", "Provider name", 0.7).displayName)
        assertEquals("Unknown species", PlantCandidate("Unknown species", null, 0.6).displayName)
    }

    @Test
    fun `reading and saving preserves every stored name including legacy and customized names`() {
        listOf("MANUAL", "IDENTIFIED").forEach { origin ->
            listOf("Bunga Ayah", "Chinese hibiscus", "Hibiscus rosa-sinensis", "Kembang sepatu").forEach { name ->
                val entity = PlantEntity(
                    id = "p1",
                    displayName = name,
                    scientificName = "Hibiscus rosa-sinensis",
                    notes = "Nama pilihan Ayah",
                    photoRef = null,
                    origin = origin,
                    createdAt = 1000L,
                )
                assertEquals(name, entity.toDomain().displayName)
                assertEquals(entity, entity.toDomain().toEntity())
            }
        }
    }
}
