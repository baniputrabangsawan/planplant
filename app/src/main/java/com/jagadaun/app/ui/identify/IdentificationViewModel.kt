package com.jagadaun.app.ui.identify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jagadaun.app.data.identification.IdentificationClient
import com.jagadaun.app.data.identification.IdentificationException
import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class IdentificationUiState(
    val photoRef: String? = null,
    val loading: Boolean = false,
    val result: IdentificationResult? = null,
    val errorCode: String? = null,
    val selectedCandidate: PlantCandidate? = null,
)

internal class IdentificationViewModel(private val client: IdentificationClient) : ViewModel() {
    private val mutableState = MutableStateFlow(IdentificationUiState())
    val state: StateFlow<IdentificationUiState> = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0L

    fun setPhoto(photoRef: String?) {
        if (mutableState.value.photoRef == photoRef) return
        generation++
        request?.cancel()
        request = null
        mutableState.value = IdentificationUiState(photoRef = photoRef)
    }

    fun identify(photoRef: String) {
        setPhoto(photoRef)
        if (mutableState.value.loading) return
        val requestGeneration = ++generation
        mutableState.value = IdentificationUiState(photoRef = photoRef, loading = true)
        request = viewModelScope.launch {
            try {
                val result = client.identify(photoRef)
                ensureActive()
                if (generation == requestGeneration) {
                    mutableState.value = IdentificationUiState(photoRef = photoRef, result = result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IdentificationException) {
                if (generation == requestGeneration) {
                    mutableState.value = IdentificationUiState(photoRef = photoRef, errorCode = error.code)
                }
            }
        }
    }

    fun select(candidate: PlantCandidate) {
        val current = mutableState.value
        if (!current.loading && current.result?.candidates?.contains(candidate) == true) {
            mutableState.value = current.copy(selectedCandidate = candidate)
        }
    }

    companion object {
        fun factory(client: IdentificationClient): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(IdentificationViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return IdentificationViewModel(client) as T
            }
        }
    }
}
