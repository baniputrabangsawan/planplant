package com.jagadaun.app.ui.identify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jagadaun.app.data.identification.IdentificationClient
import com.jagadaun.app.data.identification.IdentificationException
import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthRecord
import com.jagadaun.app.domain.HealthResult
import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantCandidate
import com.jagadaun.app.domain.PlantAnalysisResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Below this stored provider score a species name is a guess, so the UI asks for a retake. */
internal const val MIN_NAMEABLE_SCORE = 0.30

internal data class IdentificationUiState(
    val photoRefs: List<String> = emptyList(),
    val loading: Boolean = false,
    val result: IdentificationResult? = null,
    val errorCode: String? = null,
    val selectedCandidate: PlantCandidate? = null,
    val healthResult: HealthResult? = null,
    val healthErrorCode: String? = null,
    val selectedHealthCandidate: HealthCandidate? = null,
    val assessmentId: String? = null,
    val checkedAt: Long = 0,
    val healthNotes: String = "",
    val savingHealth: Boolean = false,
    val healthSaveError: String? = null,
    val savedHealthRecord: HealthRecord? = null,
    val savedFromPhotoRefs: List<String>? = null,
) {
    val photoRef: String? get() = photoRefs.firstOrNull()

    fun completed(analysis: PlantAnalysisResult, id: String, timestamp: Long) = IdentificationUiState(
        photoRefs = photoRefs,
        result = analysis.identification,
        errorCode = analysis.identificationErrorCode,
        // Below this provider score the label is a guess; show a retake prompt instead of a name.
        selectedCandidate = analysis.identification?.candidates
            ?.firstOrNull { it.score >= MIN_NAMEABLE_SCORE },
        healthResult = analysis.health,
        healthErrorCode = analysis.healthErrorCode,
        selectedHealthCandidate = analysis.health?.candidates?.firstOrNull(),
        assessmentId = id.takeIf { analysis.health != null },
        checkedAt = timestamp.takeIf { analysis.health != null } ?: 0,
    )

    fun withSavedHealthRecord(record: HealthRecord) = copy(
        photoRefs = record.photoRefs,
        selectedHealthCandidate = record.candidate,
        healthNotes = record.notes,
        savingHealth = false,
        healthSaveError = null,
        savedFromPhotoRefs = photoRefs,
        savedHealthRecord = record,
    )
}

internal class IdentificationViewModel(
    private val client: IdentificationClient,
    private val saveHealthRecord: suspend (HealthRecord) -> HealthRecord,
) : ViewModel() {
    private val mutableState = MutableStateFlow(IdentificationUiState())
    val state: StateFlow<IdentificationUiState> = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0L

    fun setPhotos(photoRefs: List<String>) {
        if (mutableState.value.savingHealth) return
        if (mutableState.value.photoRefs == photoRefs) return
        generation++
        request?.cancel()
        request = null
        mutableState.value = IdentificationUiState(photoRefs = photoRefs)
    }

    fun analyze(photoRefs: List<String>) {
        if (photoRefs.isEmpty() || mutableState.value.savingHealth) return
        setPhotos(photoRefs)
        if (mutableState.value.loading) return
        val requestGeneration = ++generation
        // An explicit retry replaces both prior outcomes and their local save snapshot.
        mutableState.value = IdentificationUiState(photoRefs = photoRefs, loading = true)
        request = viewModelScope.launch {
            try {
                val result = client.analyze(photoRefs)
                ensureActive()
                if (generation == requestGeneration) {
                    mutableState.value = mutableState.value.completed(
                        result,
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IdentificationException) {
                if (generation == requestGeneration) {
                    mutableState.value = IdentificationUiState(
                        photoRefs = photoRefs,
                        errorCode = error.code,
                        healthErrorCode = error.code,
                    )
                }
            }
        }
    }

    fun setHealthNotes(notes: String) {
        val current = mutableState.value
        if (!current.loading && !current.savingHealth && current.savedHealthRecord == null) {
            mutableState.value = current.copy(healthNotes = notes.take(2000))
        }
    }

    fun saveHealth() {
        val current = mutableState.value
        if (current.loading || current.savingHealth || current.savedHealthRecord != null) return
        val result = current.healthResult ?: return
        val candidate = current.selectedHealthCandidate ?: return
        val id = current.assessmentId ?: return
        if (current.photoRefs.isEmpty()) return
        val record = HealthRecord(
            id = id,
            plantId = null,
            createdAt = current.checkedAt,
            candidate = candidate,
            uncertain = result.uncertain,
            photoRefs = current.photoRefs,
            notes = current.healthNotes,
        )
        mutableState.value = current.copy(savingHealth = true, healthSaveError = null)
        viewModelScope.launch {
            // Once committing, finish adoption too: the repository consumes the draft on success.
            withContext(NonCancellable) {
                val saved = try {
                    saveHealthRecord(record)
                } catch (error: CancellationException) {
                    mutableState.value = mutableState.value.copy(
                        savingHealth = false,
                        healthSaveError = "Penyimpanan dibatalkan. Periksa riwayat sebelum mencoba lagi.",
                    )
                    throw error
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(
                        savingHealth = false,
                        healthSaveError = "Penyimpanan belum pasti. Coba simpan lagi tanpa duplikasi.",
                    )
                    return@withContext
                }
                mutableState.value = mutableState.value.withSavedHealthRecord(saved)
            }
        }
    }

    companion object {
        fun factory(
            client: IdentificationClient,
            saveHealthRecord: suspend (HealthRecord) -> HealthRecord,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(IdentificationViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return IdentificationViewModel(client, saveHealthRecord) as T
            }
        }
    }
}
