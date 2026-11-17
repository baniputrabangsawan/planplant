package com.jagadaun.app.ui.garden

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.components.rememberPhotoActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PlantFormScreen(
    container: AppContainer,
    plantId: String?,
    initialPhotoRef: String?,
    initialName: String?,
    initialScientificName: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val existing = plantId?.let { id -> plants.firstOrNull { it.id == id } }
    if (plantId != null && existing == null) {
        Column {
            ScreenTopBar("Tanaman", onBack)
            Text("Memuat tanaman…", modifier = Modifier.padding(16.dp))
        }
        return
    }

    var name by rememberSaveable(plantId) { mutableStateOf(existing?.displayName ?: initialName ?: "") }
    var scientificName by rememberSaveable(plantId) { mutableStateOf(existing?.scientificName ?: initialScientificName ?: "") }
    var notes by rememberSaveable(plantId) { mutableStateOf(existing?.notes ?: "") }
    var photoRef by rememberSaveable(plantId) { mutableStateOf(existing?.photoRef ?: initialPhotoRef) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val photoActions = rememberPhotoActions(container.photos) { path ->
        photoRef?.takeIf(container.photos::isDraft)?.let(container.photos::discardDraft)
        photoRef = path
    }
    val busy = saving || photoActions.busy
    val leave: () -> Unit = {
        if (!busy) {
            photoRef?.takeIf(container.photos::isDraft)?.let(container.photos::discardDraft)
            onBack()
        }
    }
    BackHandler(onBack = leave)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(title = if (plantId == null) "Tambah Tanaman" else "Ubah Tanaman", onBack = leave)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PlantPhoto(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                iconSize = 64.dp,
                photoRef = photoRef,
                contentDescription = "Foto tanaman",
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Ambil Foto",
                icon = Icons.Filled.CameraAlt,
                onClick = photoActions.takePhoto,
                enabled = !busy,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Pilih dari Galeri",
                icon = Icons.Filled.PhotoLibrary,
                onClick = photoActions.pickPhoto,
                enabled = !busy,
            )
            if (photoRef != null) {
                TextButton(enabled = !busy, onClick = {
                    photoRef?.takeIf(container.photos::isDraft)?.let(container.photos::discardDraft)
                    photoRef = null
                }) { Text("Hapus foto") }
            }
            Text(
                if (photoActions.busy) "Menyiapkan foto…" else "Foto koleksi disimpan di perangkat. Menyimpan formulir ini tidak mengirim foto.",
                style = MaterialTheme.typography.bodyMedium,
            )
            photoActions.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                enabled = !busy,
                label = { Text("Nama") },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) ({ Text("Nama wajib diisi") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = scientificName,
                onValueChange = { scientificName = it },
                enabled = !busy,
                label = { Text("Nama ilmiah (opsional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                enabled = !busy,
                label = { Text("Catatan") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                text = if (saving) "Menyimpan…" else "Simpan",
                enabled = !busy,
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else if (!busy) {
                        saving = true
                        saveError = null
                        scope.launch {
                            try {
                                container.savePlant(
                                    plantId, name, scientificName, notes, photoRef,
                                    identified = initialScientificName != null,
                                )
                                onDone()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                saveError = "Tanaman belum tersimpan. Periksa ruang penyimpanan atau ganti foto, lalu coba lagi."
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        }
    }
}
