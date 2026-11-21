package com.jagadaun.app.ui.identify

import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthRecord
import com.jagadaun.app.domain.HealthResult
import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantAnalysisResult
import com.jagadaun.app.domain.PlantCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class IdentificationUiStateTest {
    private val plant = PlantCandidate("Mangifera indica", "Mango", 0.8)
    private val hypothesis = HealthCandidate("APHIGO", "Aphis gossypii", 0.6)
    private val identity = IdentificationResult(listOf(plant), uncertain = false)
    private val health = HealthResult(listOf(hypothesis), uncertain = true)

    @Test
    fun `species failure retains successful uncertain health and save identity`() {
        val state = IdentificationUiState(photoRefs = listOf("draft"), loading = true).completed(
            PlantAnalysisResult(null, "NOT_RECOGNIZED", health, null), "assessment", 123L,
        )

        assertFalse(state.loading)
        assertNull(state.result)
        assertEquals("NOT_RECOGNIZED", state.errorCode)
        assertSame(health, state.healthResult)
        assertNull(state.healthErrorCode)
        assertEquals("assessment", state.assessmentId)
        assertEquals(123L, state.checkedAt)
        assertEquals(hypothesis, state.selectedHealthCandidate)
    }

    @Test
    fun `health failure retains species and never offers a health save id`() {
        val state = IdentificationUiState(photoRefs = listOf("draft"), loading = true).completed(
            PlantAnalysisResult(identity, null, null, "PROVIDER_QUOTA"), "assessment", 123L,
        )

        assertSame(identity, state.result)
        assertEquals(plant, state.selectedCandidate)
        assertNull(state.errorCode)
        assertNull(state.healthResult)
        assertEquals("PROVIDER_QUOTA", state.healthErrorCode)
        assertNull(state.assessmentId)
        assertEquals(0L, state.checkedAt)
    }

    @Test
    fun `new completed attempt clears old candidate notes and saved snapshot`() {
        val oldRecord = record()
        val previous = IdentificationUiState(
            photoRefs = listOf("saved"),
            result = identity,
            selectedCandidate = plant,
            healthResult = health,
            selectedHealthCandidate = hypothesis,
            assessmentId = oldRecord.id,
            checkedAt = oldRecord.createdAt,
            healthNotes = oldRecord.notes,
            savedHealthRecord = oldRecord,
            savedFromPhotoRefs = listOf("old-draft"),
            healthSaveError = "old save error",
        )
        val state = previous.completed(
            PlantAnalysisResult(null, "NOT_RECOGNIZED", null, "NOT_RECOGNIZED"), "new-id", 456L,
        )

        assertEquals(listOf("saved"), state.photoRefs)
        assertNull(state.result)
        assertNull(state.healthResult)
        assertNull(state.selectedCandidate)
        assertNull(state.selectedHealthCandidate)
        assertNull(state.savedHealthRecord)
        assertNull(state.savedFromPhotoRefs)
        assertNull(state.assessmentId)
        assertEquals("", state.healthNotes)
        assertNull(state.healthSaveError)
    }

    @Test
    fun `committed save adopts all three permanent photos without clearing either analysis or plant selection`() {
        val saved = record().copy(photoRefs = listOf("saved-front", "saved-side", "saved-detail"))
        val drafts = listOf("draft-front", "draft-side", "draft-detail")
        val state = IdentificationUiState(
            photoRefs = drafts,
            result = identity,
            selectedCandidate = plant,
            healthResult = health,
            selectedHealthCandidate = hypothesis,
            assessmentId = saved.id,
            checkedAt = saved.createdAt,
            healthNotes = "draft notes",
            savingHealth = true,
        ).withSavedHealthRecord(saved)

        assertEquals(saved.photoRefs, state.photoRefs)
        assertEquals(drafts, state.savedFromPhotoRefs)
        assertSame(identity, state.result)
        assertSame(health, state.healthResult)
        assertEquals(plant, state.selectedCandidate)
        assertEquals(saved.candidate, state.selectedHealthCandidate)
        assertEquals(saved.notes, state.healthNotes)
        assertSame(saved, state.savedHealthRecord)
        assertFalse(state.savingHealth)
        assertNull(state.healthSaveError)
    }

    @Test
    fun `uncertain analysis selects only the top supplied candidate for each section`() {
        val alternative = plant.copy(scientificName = "Other plant", score = 0.1)
        val alternativeHealth = hypothesis.copy(code = "OTHER", score = 0.1)
        val state = IdentificationUiState(photoRefs = listOf("draft")).completed(
            PlantAnalysisResult(
                IdentificationResult(listOf(plant, alternative), uncertain = true),
                null,
                HealthResult(listOf(hypothesis, alternativeHealth), uncertain = true),
                null,
            ),
            "assessment",
            123L,
        )

        assertSame(plant, state.selectedCandidate)
        assertSame(hypothesis, state.selectedHealthCandidate)
    }

    @Test
    fun `empty uncertain outcomes never invent selections`() {
        val state = IdentificationUiState(photoRefs = listOf("draft")).completed(
            PlantAnalysisResult(
                IdentificationResult(emptyList(), uncertain = true),
                null,
                HealthResult(emptyList(), uncertain = true),
                null,
            ),
            "assessment",
            123L,
        )

        assertNull(state.selectedCandidate)
        assertNull(state.selectedHealthCandidate)
    }

    private fun record() = HealthRecord(
        id = "original-id",
        plantId = null,
        createdAt = 100L,
        candidate = hypothesis,
        uncertain = true,
        photoRefs = listOf("saved"),
        notes = "original notes",
    )

    @Test
    fun `low score guess is not offered as a name or save target`() {
        val weak = PlantCandidate("Alstonia actinophylla", null, 0.12)
        val state = IdentificationUiState(photoRefs = listOf("draft")).completed(
            PlantAnalysisResult(IdentificationResult(listOf(weak), uncertain = true), null, null, null),
            "assessment",
            1L,
        )
        assertNull(state.selectedCandidate)
        assertEquals(listOf(weak), state.result?.candidates)
    }

    @Test
    fun `a confident top candidate is still selected for saving`() {
        val state = IdentificationUiState(photoRefs = listOf("draft")).completed(
            PlantAnalysisResult(identity, null, null, null),
            "assessment",
            1L,
        )
        assertEquals(plant, state.selectedCandidate)
    }

    @Test
    fun `choosing another supplied candidate changes the saved identity without discarding health`() {
        val alternative = PlantCandidate("Carica papaya", "Pepaya", 0.45)
        val state = IdentificationUiState(
            photoRefs = listOf("draft.jpg"),
            result = IdentificationResult(listOf(plant, alternative), uncertain = true),
            selectedCandidate = plant,
            healthResult = health,
            healthNotes = "Tetap simpan catatan",
        )
        val selected = state.withCandidate(alternative)
        assertEquals(alternative, selected.selectedCandidate)
        assertSame(health, selected.healthResult)
        assertEquals(state.photoRefs, selected.photoRefs)
        assertEquals(state.healthNotes, selected.healthNotes)
    }

    @Test
    fun `unknown low-score and busy selections cannot replace the confirmed candidate`() {
        val weak = PlantCandidate("Carica papaya", "Pepaya", 0.29)
        val state = IdentificationUiState(
            result = IdentificationResult(listOf(plant, weak), uncertain = true),
            selectedCandidate = plant,
        )
        assertSame(state, state.withCandidate(weak))
        assertSame(state, state.withCandidate(plant.copy(scientificName = "Foreign species")))
        val busy = state.copy(savingHealth = true)
        assertSame(busy, busy.withCandidate(plant))
    }
}
