package com.jagadaun.app.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlantKnowledgeTest {
    @Test fun speciesLookupDoesNotDependOnPhoneLanguage() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val knowledge = PlantKnowledgeSource.forScientificName("  MANGIFERA INDICA  ")
            assertEquals("Mangga", knowledge?.name)
            assertSame(PlantKnowledgeSource.forScientificName("mangifera indica"), knowledge)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun cultivarOrNicknameNeverMasqueradesAsRecognizedSpecies() {
        for (name in listOf(null, "", "Pepaya", "California", "Carica", "Carica papaya 'Calina'", "Carica papaya L.", "Carica papaya fake")) {
            assertNull(name, PlantKnowledgeSource.forScientificName(name))
        }
    }

    @Test fun relatedSpeciesDoesNotReceiveAnotherPlantsVarieties() {
        for (name in listOf("Vasconcellea pubescens", "Mangifera foetida", "Psidium guajava")) {
            assertNull(name, PlantKnowledgeSource.forScientificName(name))
        }
    }
}
