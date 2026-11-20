package com.jagadaun.app.ui.health

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jagadaun.app.AppContainer
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.domain.HealthGuide
import com.jagadaun.app.domain.HealthGuides
import com.jagadaun.app.domain.HealthRecord
import com.jagadaun.app.domain.HealthTrackingStatus
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.components.rememberPhotoActions
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.TextSecondary
import com.jagadaun.app.ui.theme.Warning
import com.jagadaun.app.ui.theme.WarningContainer
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HealthScreen(
    container: AppContainer,
    plantId: String?,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    cameraPhotos: List<String>,
    onTakePhoto: () -> Unit,
    onCameraPhotosConsumed: () -> Unit,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val plant = plants.firstOrNull { it.id == plantId }
    var photoRefs by rememberSaveable(plantId) {
        mutableStateOf(listOf(plant?.photoRef, null, null))
    }
    var activeSlot by rememberSaveable { mutableStateOf(0) }
    val attached = remember(photoRefs) { photoRefs.filterNotNull() }
    val factory = remember(container) { HealthCheckViewModel.factory(container.identificationClient) }
    val model: HealthCheckViewModel = viewModel(factory = factory)
    val observedState by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(attached) { model.setPhotos(attached) }
    val state = observedState.takeIf { it.photoRefs == attached } ?: HealthCheckState(photoRefs = attached)
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember(state.assessmentId) { mutableStateOf<String?>(null) }
    var saved by rememberSaveable(state.assessmentId) { mutableStateOf(false) }
    var notes by rememberSaveable(state.assessmentId) { mutableStateOf("") }
    val photos = rememberPhotoActions(container.photos) { replacement ->
        val previous = photoRefs[activeSlot]
        val updated = photoRefs.mapIndexed { index, path -> if (index == activeSlot) replacement else path }
        model.setPhotos(updated.filterNotNull())
        photoRefs = updated
        if (previous !in updated) {
            scope.launch(Dispatchers.IO) { discardHealthDraft(container.photos, previous) }
        }
    }
    val busy = photos.busy || leaving || saving
    val leave: () -> Unit = {
        if (!busy) {
            leaving = true
            model.setPhotos(emptyList())
            scope.launch {
                withContext(Dispatchers.IO) { attached.forEach { discardHealthDraft(container.photos, it) } }
                onBack()
            }
        }
    }
    BackHandler(onBack = leave)
    val result = state.result
    val selected = result?.candidates?.firstOrNull()
    val knownGuide = remember(selected?.code) { selected?.let { HealthGuides.forCode(it.code) } }
    val specificGuide = knownGuide.takeIf { result?.uncertain == false && (selected?.score ?: 0.0) >= 0.5 }

    LaunchedEffect(cameraPhotos) {
        if (cameraPhotos.isNotEmpty()) {
            val updated: List<String?> = if (cameraPhotos.size > 1) cameraPhotos else {
                photoRefs.mapIndexed { index, path -> if (index == activeSlot) cameraPhotos.single() else path }
            }
            val discarded = attached.filter { it !in updated }
            model.setPhotos(updated.filterNotNull())
            photoRefs = updated
            saved = false
            onCameraPhotosConsumed()
            withContext(Dispatchers.IO) { discarded.forEach { discardHealthDraft(container.photos, it) } }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Periksa tanaman", leave)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            plant?.let { Text(it.displayName, style = MaterialTheme.typography.titleLarge) }
            SecondaryButton("Riwayat pemeriksaan", onOpenHistory, enabled = !busy && !state.loading)
            Text(
                "Foto tanaman utuh, bagian bermasalah, dan sisi lain. Gunakan tanaman yang sama.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                healthPhotoTitles.forEachIndexed { index, title ->
                    Surface(
                        color = if (activeSlot == index) SoftAccent else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.selectable(
                                selected = activeSlot == index,
                                enabled = !busy && !state.loading,
                                role = Role.RadioButton,
                                onClick = { activeSlot = index },
                            ).heightIn(min = 64.dp).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = activeSlot == index, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. $title", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (photoRefs[index] == null) "Belum ada foto" else "Foto siap",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            Text(healthPhotoHints[activeSlot], style = MaterialTheme.typography.bodyLarge)
            photoRefs[activeSlot]?.let { path ->
                PlantPhoto(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    photoRef = path,
                    contentDescription = healthPhotoTitles[activeSlot],
                )
            }
            SecondaryButton(
                text = "Ambil foto: ${healthPhotoTitles[activeSlot].lowercase()}",
                onClick = onTakePhoto,
                enabled = !busy && !state.loading,
                icon = Icons.Filled.CameraAlt,
            )
            SecondaryButton(
                text = "Pilih foto: ${healthPhotoTitles[activeSlot].lowercase()}",
                onClick = photos.pickPhoto,
                enabled = !busy && !state.loading,
                icon = Icons.Filled.PhotoLibrary,
            )
            if (photoRefs[activeSlot] != null) {
                TextButton(
                    enabled = !busy && !state.loading,
                    onClick = {
                        val previous = photoRefs[activeSlot]
                        val updated = photoRefs.mapIndexed { index, path -> if (index == activeSlot) null else path }
                        model.setPhotos(updated.filterNotNull())
                        photoRefs = updated
                        if (previous !in updated) {
                            scope.launch(Dispatchers.IO) { discardHealthDraft(container.photos, previous) }
                        }
                    },
                ) { Text("Hapus foto ini") }
            }
            Text(
                "${attached.size} foto • 1 percobaan keluarga (1 kredit penyedia). Foto dikirim ke Pl@ntNet setelah Anda mengetuk Periksa foto.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            PrimaryButton(
                text = when {
                    state.loading -> "Sedang memeriksa…"
                    state.errorCode != null || result != null -> "Coba lagi"
                    else -> "Periksa foto"
                },
                onClick = { model.check(attached) },
                enabled = attached.isNotEmpty() && !busy && !state.loading,
                icon = Icons.Filled.Healing,
            )
            if (state.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Mencari kemungkinan penyebab…")
                }
            }
            (photos.error ?: state.errorCode?.let(::healthErrorMessage))?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (selected != null) {
                HealthHeading(knownGuide?.title?.removePrefix("Dugaan ")?.replaceFirstChar { it.uppercase() } ?: selected.label)
                if (result?.uncertain == true) {
                    Text("Ambil ulang bagian bergejala dari dekat; gunakan langkah awal berikut.", color = Warning)
                }
                HealthGuideContent(specificGuide ?: HealthGuides.general)
            }
            if (result != null && selected != null) {
                HealthHeading("Pantau perkembangan")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(2000) },
                    label = { Text("Catatan gejala dan perawatan") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !saved,
                    minLines = 2,
                )
                PrimaryButton(
                    text = when {
                        saving -> "Menyimpan pemeriksaan…"
                        saved -> "Pemeriksaan tersimpan"
                        else -> "Simpan pemeriksaan"
                    },
                    enabled = !busy && !saved,
                    onClick = {
                        val id = state.assessmentId
                        if (id != null) {
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    val record = container.healthRecords.save(
                                        HealthRecord(
                                            id = id,
                                            plantId = plantId,
                                            createdAt = state.checkedAt,
                                            candidate = selected,
                                            uncertain = result.uncertain,
                                            photoRefs = attached,
                                            notes = notes,
                                            status = HealthTrackingStatus.WATCHING,
                                        ),
                                    )
                                    var index = 0
                                    val updated = photoRefs.map { if (it == null) null else record.photoRefs[index++] }
                                    model.adoptSavedPhotos(record.photoRefs)
                                    photoRefs = updated
                                    saved = true
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    saveError = "Pemeriksaan belum tersimpan. Periksa ruang penyimpanan lalu coba lagi."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                )
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            HealthSourceLink("Analisis foto oleh Pl@ntNet", "https://plantnet.org/")
        }
    }
}

@Composable
internal fun HealthHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun HealthCaution(text: String) {
    Surface(color = WarningContainer, shape = RoundedCornerShape(12.dp)) {
        Text(text, color = Warning, modifier = Modifier.padding(16.dp))
    }
}

@Composable
internal fun HealthGuideContent(guide: HealthGuide) {
    HealthHeading("Lakukan sekarang")
    guide.firstSteps.take(2).forEachIndexed { index, text ->
        Text("${index + 1}. $text", style = MaterialTheme.typography.bodyLarge)
    }
    var expanded by rememberSaveable(guide.title) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Tutup panduan" else "Lihat pencegahan dan sumber")
    }
    if (expanded) {
        guide.firstSteps.drop(2).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        HealthHeading("Pencegahan")
        guide.prevention.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        guide.sources.forEach { HealthSourceLink(it.title, it.url) }
        Text("Jika kerusakan meluas, hubungi penyuluh. Jangan memakai pestisida hanya berdasarkan foto.", color = TextSecondary)
    }
}

@Composable
internal fun HealthSourceLink(title: String, url: String) {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    TextButton(
        onClick = {
            try {
                handler.openUri(url)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Tidak ada aplikasi untuk membuka tautan.", Toast.LENGTH_SHORT).show()
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, "Tautan belum bisa dibuka.", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text("$title ↗") }
}

private fun discardHealthDraft(store: PhotoStore, path: String?) {
    if (path == null || !store.isDraft(path)) return
    try {
        store.discardDraft(path)
    } catch (_: IOException) {
        // Cache cleanup is best effort; never delete saved plant photos.
    } catch (_: SecurityException) {
        // PhotoStore also expires abandoned drafts during normal startup cleanup.
    }
}

private fun healthErrorMessage(code: String): String = when (code) {
    "OFFLINE" -> "Pemeriksaan foto perlu internet. Foto tetap ada; sambungkan internet lalu coba lagi."
    "CONNECT_TIMEOUT", "READ_TIMEOUT", "TIMEOUT", "CONNECTION_ERROR" -> "Layanan belum bisa dihubungi. Foto tetap ada; periksa internet lalu coba lagi."
    "DAILY_LIMIT", "PROVIDER_QUOTA" -> "Batas analisis foto hari ini tercapai. Panduan pencegahan tetap bisa dibaca."
    "UNAUTHORIZED", "PROVIDER_AUTH", "NOT_CONFIGURED" -> "Akses pemeriksaan perlu diperbarui oleh pengelola aplikasi."
    "NOT_RECOGNIZED" -> "Belum bisa dipastikan dari foto ini. Ini bukan berarti tanaman sehat. Ambil foto bagian bergejala yang lebih jelas."
    "INVALID_IMAGE", "IMAGE_TOO_LARGE" -> "Foto belum bisa diperiksa. Ambil foto baru atau pilih foto lain."
    "DEVICE_STORAGE" -> "Foto tidak dapat dibaca dari perangkat. Pilih lagi foto gejalanya."
    else -> "Pemeriksaan belum berhasil. Foto tetap ada; coba lagi nanti."
}

private val healthPhotoTitles = listOf("Seluruh tanaman", "Gejala dari dekat", "Sisi lain daun")
private val healthPhotoHints = listOf(
    "Mundur sedikit agar daun, batang, dan bentuk seluruh tanaman terlihat.",
    "Dekatkan kamera ke bercak, kutu, atau bagian rusak. Pastikan tajam dan tidak terlalu gelap.",
    "Foto bagian bawah daun atau gejala dari sudut lain. Jangan memotret tanaman yang berbeda.",
)
