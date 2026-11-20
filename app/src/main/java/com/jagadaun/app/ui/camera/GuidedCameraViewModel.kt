package com.jagadaun.app.ui.camera

import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.jagadaun.app.data.photos.PhotoStore
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class GuidedCaptureState(
    val surround: Boolean = false,
    val refs: List<String> = List(MAX_CAPTURE_VIEWS) { "" },
    val selected: Int = 0,
    val retaking: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val count: Int get() = if (surround) MAX_CAPTURE_VIEWS else 1
    val reviewing: Boolean get() = refs[selected].isNotEmpty() && !retaking

    /** One supported view is enough; extra angles are optional, as Pl@ntNet documents. */
    val complete: Boolean get() = refs.take(count).any(String::isNotEmpty)
    val full: Boolean get() = refs.take(count).all(String::isNotEmpty)
    val captured: Int get() = refs.take(count).count(String::isNotEmpty)
}

internal const val MAX_CAPTURE_VIEWS = 5

/** Owns only this camera session's drafts until the caller accepts them. */
internal class GuidedCameraViewModel(
    private val photos: PhotoStore,
    private val saved: SavedStateHandle,
    private val allowRotation: Boolean,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var closed = false
    private var cancelled = false
    private var transferred = false
    private val mutableState = MutableStateFlow(
        GuidedCaptureState(
            surround = allowRotation && (saved["surround"] ?: false),
            refs = saved.get<ArrayList<String>>("refs")?.takeIf { it.size == MAX_CAPTURE_VIEWS }
                ?.toList() ?: List(MAX_CAPTURE_VIEWS) { "" },
            selected = (saved.get<Int>("selected") ?: 0).coerceIn(0, MAX_CAPTURE_VIEWS - 1),
            busy = true,
        ),
    )
    val state = mutableState.asStateFlow()

    init {
        // A raw capture left by process death is not an accepted photo.
        val abandoned = saved.get<String>("raw")
        saved["raw"] = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                val restored = mutableState.value
                val valid = withContext(Dispatchers.IO) {
                    discard(abandoned)
                    restored.refs.map { if (it.isNotEmpty() && File(it).isFile) it else "" }
                }
                if (!closed && !cancelled) {
                    update(restored.copy(
                        refs = valid,
                        selected = if (restored.surround) restored.selected else 0,
                        busy = false,
                        error = if (valid != restored.refs) "Foto lama tidak tersedia. Ambil ulang." else null,
                    ))
                }
            }
        }
    }

    fun select(index: Int) {
        val current = state.value
        if (unavailable() || current.busy || index !in 0 until current.count) return
        update(current.copy(selected = index, retaking = false, error = null))
    }

    fun setSurround(enabled: Boolean) {
        val current = state.value
        if (unavailable() || current.busy || enabled == current.surround || (enabled && !allowRotation)) return
        update(current.copy(busy = true, error = null))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                if (!enabled) withContext(Dispatchers.IO) { current.refs.drop(1).forEach(::discard) }
                if (!unavailable()) update(current.copy(
                    surround = enabled,
                    refs = if (enabled) current.refs else List(MAX_CAPTURE_VIEWS) { if (it == 0) current.refs[0] else "" },
                    selected = 0,
                    retaking = false,
                ))
            }
        }
    }

    fun retake() {
        if (!unavailable() && !state.value.busy) {
            update(state.value.copy(retaking = true, error = null))
        }
    }

    fun next() {
        val current = state.value
        if (unavailable() || current.busy) return
        val empty = (0 until current.count).firstOrNull { current.refs[it].isEmpty() } ?: return
        update(current.copy(selected = empty, retaking = false, error = null))
    }

    fun capture(capture: ImageCapture, callbackExecutor: Executor, rotation: Int) {
        val current = state.value
        if (unavailable() || current.busy || current.reviewing) return
        update(current.copy(busy = true, error = null))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // CameraX cannot cancel a file write. Wait for its callback before deleting the raw
            // file, even if the route is removed or configuration changes during capture.
            withContext(NonCancellable) {
                var raw: File? = null
                var imported: String? = null
                var adopted = false
                try {
                    val file = withContext(Dispatchers.IO) { photos.newCameraFile() }
                    raw = file
                    saved["raw"] = file.absolutePath
                    if (unavailable()) return@withContext
                    capture.targetRotation = rotation
                    saveCapture(capture, file, callbackExecutor)
                    if (unavailable()) return@withContext
                    val result = withContext(Dispatchers.IO) { photos.importPhoto(photos.cameraUri(file)) }
                    imported = result
                    if (unavailable()) return@withContext
                    val refs = current.refs.toMutableList().apply { this[current.selected] = result }
                    update(current.copy(refs = refs, retaking = false, busy = true))
                    adopted = true
                    withContext(Dispatchers.IO) { discard(current.refs[current.selected]) }
                } catch (_: Exception) {
                    if (!unavailable()) update(state.value.copy(error = "Foto gagal diambil. Coba lagi."))
                } finally {
                    saved["raw"] = null
                    withContext(Dispatchers.IO) {
                        if (!adopted) discard(imported)
                        discard(raw?.absolutePath)
                    }
                    if (!unavailable()) update(state.value.copy(busy = false))
                }
            }
        }
    }

    fun accept(onCaptured: (List<String>) -> Unit) {
        val current = state.value
        if (unavailable() || current.busy || !current.complete) return
        val captured = current.refs.take(current.count).filter(String::isNotEmpty)
        if (captured.isEmpty()) return
        update(current.copy(busy = true, error = null))
        // Navigation can clear this ViewModel synchronously inside the callback.
        transferred = true
        try {
            onCaptured(captured)
            saved["refs"] = arrayListOf<String>()
        } catch (_: Exception) {
            transferred = false
            update(current.copy(error = "Foto belum dapat digunakan. Coba lagi."))
            if (closed) cleanOwnedDrafts()
        }
    }

    fun cancel(onBack: () -> Unit) {
        if (cancelled || transferred) return
        cancelled = true
        update(state.value.copy(busy = true))
        val owned = state.value.refs
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) { owned.forEach(::discard) }
                saved["refs"] = arrayListOf<String>()
                if (!closed) onBack()
            }
        }
    }

    private fun unavailable() = closed || cancelled || transferred

    private fun update(value: GuidedCaptureState) {
        mutableState.value = value
        saved["surround"] = value.surround
        saved["refs"] = ArrayList(value.refs)
        saved["selected"] = value.selected
    }

    override fun onCleared() {
        closed = true
        if (!transferred) cleanOwnedDrafts()
        scope.cancel()
    }

    private fun cleanOwnedDrafts() {
        val owned = state.value.refs
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable + Dispatchers.IO) { owned.forEach(::discard) }
        }
    }

    private fun discard(path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            photos.discardDraft(path)
        } catch (error: Exception) {
            Log.w("GuidedCamera", "Could not remove a camera draft", error)
        }
    }
}

private suspend fun saveCapture(capture: ImageCapture, file: File, executor: Executor) {
    suspendCoroutine<Unit> { continuation ->
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    continuation.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }
}
