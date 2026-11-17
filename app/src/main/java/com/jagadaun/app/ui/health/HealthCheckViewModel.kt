package com.jagadaun.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jagadaun.app.data.identification.IdentificationClient
import com.jagadaun.app.data.identification.IdentificationException
import com.jagadaun.app.domain.HealthResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class HealthCheckState(
    val photoRefs: List<String> = emptyList(),
    val loading: Boolean = false,
    val result: HealthResult? = null,
    val errorCode: String? = null,
    val assessmentId: String? = null,
    val checkedAt: Long = 0,
)

internal class HealthCheckViewModel(private val client: IdentificationClient) : ViewModel() {
    private val mutableState = MutableStateFlow(HealthCheckState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0L

    fun setPhotos(photoRefs: List<String>) {
        if (mutableState.value.photoRefs == photoRefs) return
        generation++
        request?.cancel()
        request = null
        mutableState.value = HealthCheckState(photoRefs = photoRefs.toList())
    }

    fun adoptSavedPhotos(photoRefs: List<String>) {
        mutableState.value = mutableState.value.copy(photoRefs = photoRefs.toList())
    }

    fun check(photoRefs: List<String>) {
        require(photoRefs.size in 1..3)
        setPhotos(photoRefs)
        if (mutableState.value.loading) return
        val requestGeneration = ++generation
        val snapshot = photoRefs.toList()
        mutableState.value = HealthCheckState(photoRefs = snapshot, loading = true)
        request = viewModelScope.launch {
            try {
                val result = client.checkHealth(snapshot)
                ensureActive()
                if (generation == requestGeneration) {
                    mutableState.value = HealthCheckState(
                        photoRefs = snapshot,
                        result = result,
                        assessmentId = UUID.randomUUID().toString(),
                        checkedAt = System.currentTimeMillis(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IdentificationException) {
                if (generation == requestGeneration) {
                    mutableState.value = HealthCheckState(photoRefs = snapshot, errorCode = error.code)
                }
            }
        }
    }

    companion object {
        fun factory(client: IdentificationClient): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HealthCheckViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return HealthCheckViewModel(client) as T
            }
        }
    }
}
