package com.jagadaun.app.data.repository

import com.jagadaun.app.data.local.PlantEntity
import com.jagadaun.app.data.local.ReminderEntity
import com.jagadaun.app.domain.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `plant maps to domain with curated guide`() {
        val entity = PlantEntity(
            id = "p1",
            displayName = "Janda Bolong",
            scientificName = "Monstera deliciosa",
            notes = "catatan",
            photoRef = null,
            origin = "IDENTIFIED",
            createdAt = 1000L,
        )
        val plant = entity.toDomain()
        assertEquals("p1", plant.id)
        assertEquals("Janda Bolong", plant.displayName)
        assertEquals("Monstera deliciosa", plant.scientificName)
        assertEquals(Origin.IDENTIFIED, plant.origin)
        assertNotNull(plant.guide)
        assertEquals("RHS Plant Finder", plant.guide?.source)
    }

    @Test
    fun `plant without curated species has no guide`() {
        val entity = PlantEntity(
            id = "p2",
            displayName = "Lidah Mertua",
            scientificName = "Sansevieria trifasciata",
            notes = "",
            photoRef = null,
            origin = "MANUAL",
            createdAt = 1000L,
        )
        assertNull(entity.toDomain().guide)
    }

    @Test
    fun `unknown origin falls back to manual`() {
        val entity = PlantEntity(
            id = "p3",
            displayName = "X",
            scientificName = null,
            notes = "",
            photoRef = null,
            origin = "GARBLED",
            createdAt = 0L,
        )
        assertEquals(Origin.MANUAL, entity.toDomain().origin)
    }

    @Test
    fun `reminder entity round trips through domain`() {
        val entity = ReminderEntity(
            id = "r1",
            plantId = "p1",
            careType = "Siram",
            intervalDays = 7,
            preferredTime = "07:00",
            zoneId = "Asia/Jakarta",
            active = true,
            nextDue = 123L,
        )
        assertEquals(entity, entity.toDomain().toEntity())
    }
}
