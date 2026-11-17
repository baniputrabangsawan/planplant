package com.jagadaun.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jagadaun.app.data.photos.PhotoStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoActions(
    val busy: Boolean,
    val error: String?,
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
)

@Composable
fun rememberPhotoActions(store: PhotoStore, onPhoto: (String) -> Unit): PhotoActions {
    val scope = rememberCoroutineScope()
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun receivePhoto(uri: Uri?, rawPath: String? = null, shouldImport: Boolean = true) {
        processing = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var importedPath: String? = null
            var consumed = false
            try {
                if (!shouldImport) return@launch
                withContext(Dispatchers.IO) {
                    val source = uri ?: store.cameraUri(File(requireNotNull(rawPath)))
                    // Assign before returning from IO: cancellation can discard its return value.
                    importedPath = store.importPhoto(source)
                }
                ensureActive()
                withContext(Dispatchers.Main.immediate) {
                    currentOnPhoto(requireNotNull(importedPath))
                    consumed = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                error = "Foto tidak dapat diakses. Izinkan akses atau pilih foto lain."
            } catch (_: Exception) {
                error = "Foto gagal diproses. Coba ambil atau pilih foto lain."
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (!consumed) discardDraftSafely(store, importedPath)
                    discardDraftSafely(store, rawPath)
                }
                processing = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val rawPath = pendingCameraPath
        pendingCameraPath = null
        if (rawPath != null) {
            receivePhoto(uri = null, rawPath = rawPath, shouldImport = saved)
        } else if (saved) {
            error = "Hasil kamera tidak ditemukan. Silakan ambil foto lagi."
        }
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pickerPending = false
        if (uri != null) receivePhoto(uri)
    }

    return PhotoActions(
        busy = processing || pendingCameraPath != null || pickerPending,
        error = error,
        takePhoto = {
            if (!processing && pendingCameraPath == null && !pickerPending) {
                processing = true
                error = null
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    var rawFile: File? = null
                    var launched = false
                    try {
                        val uri = withContext(Dispatchers.IO) {
                            val file = store.newCameraFile()
                            rawFile = file
                            store.cameraUri(file)
                        }
                        ensureActive()
                        pendingCameraPath = requireNotNull(rawFile).absolutePath
                        cameraLauncher.launch(uri)
                        launched = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: SecurityException) {
                        error = "Kamera tidak dapat diakses. Periksa izin kamera atau pilih foto."
                    } catch (_: Exception) {
                        error = "Kamera tidak dapat dibuka. Coba lagi atau pilih foto dari galeri."
                    } finally {
                        if (!launched) {
                            pendingCameraPath = null
                            withContext(NonCancellable + Dispatchers.IO) {
                                discardDraftSafely(store, rawFile?.absolutePath)
                            }
                        }
                        processing = false
                    }
                }
            }
        },
        pickPhoto = {
            if (!processing && pendingCameraPath == null && !pickerPending) {
                error = null
                pickerPending = true
                try {
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (cancelled: CancellationException) {
                    pickerPending = false
                    throw cancelled
                } catch (_: SecurityException) {
                    pickerPending = false
                    error = "Galeri tidak dapat diakses. Periksa izin foto lalu coba lagi."
                } catch (_: Exception) {
                    pickerPending = false
                    error = "Pemilih foto tidak dapat dibuka. Coba lagi atau gunakan kamera."
                }
            }
        },
    )
}

private fun discardDraftSafely(store: PhotoStore, path: String?) {
    if (path == null) return
    try {
        store.discardDraft(path)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Cleanup must not replace the original import or launch error.
    }
}
