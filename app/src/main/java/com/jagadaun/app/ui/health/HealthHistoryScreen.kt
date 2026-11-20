package com.jagadaun.app.ui.health

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.domain.HealthGuides
import com.jagadaun.app.domain.HealthRecord
import com.jagadaun.app.domain.HealthTrackingStatus
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun HealthHistoryScreen(container: AppContainer, plantId: String?, onBack: () -> Unit) {
    val history = remember(container, plantId) { container.healthRecords.observe(plantId) }
    val records by history.collectAsStateWithLifecycle(initialValue = null)
    val plants by container.plants.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable(plantId) { mutableStateOf<String?>(null) }
    val selected = records?.firstOrNull { it.id == selectedId }
    var busy by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!busy) {
            if (selectedId == null) onBack() else selectedId = null
        }
    }
    BackHandler(onBack = leave)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(if (selected == null) "Riwayat pemeriksaan" else "Pantau tanaman", leave)
        if (selected != null) {
            HealthRecordDetail(container, selected, onBusy = { busy = it }, onDeleted = { selectedId = null })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Foto, hasil, dan perkembangan tersimpan di HP.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                if (records == null) {
                    item { CircularProgressIndicator() }
                } else if (records.orEmpty().isEmpty()) {
                    item { Text("Belum ada pemeriksaan tersimpan. Setelah memeriksa foto, pilih Simpan pemeriksaan.") }
                }
                items(records.orEmpty(), key = { it.id }) { record ->
                    Card(onClick = { selectedId = record.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val name = plants.firstOrNull { it.id == record.plantId }?.displayName
                            name?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                            Text(HealthGuides.forCode(record.candidate.code)?.title?.removePrefix("Dugaan ")?.replaceFirstChar { it.uppercase() } ?: record.candidate.label, style = MaterialTheme.typography.titleMedium)
                            Text(record.candidate.label, color = TextSecondary)
                            Text(healthRecordDate(record.createdAt), color = TextSecondary)
                            Text(record.status.label)
                            if (record.uncertain) Text("Perlu foto tambahan", color = MaterialTheme.colorScheme.error)
                            Text("Buka hasil dan pantau →", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun HealthRecordDetail(
    container: AppContainer,
    record: HealthRecord,
    onBusy: (Boolean) -> Unit,
    onDeleted: () -> Unit,
) {
    var status by rememberSaveable(record.id, record.status) { mutableStateOf(record.status) }
    var notes by rememberSaveable(record.id, record.notes) { mutableStateOf(record.notes) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val knownGuide = remember(record.candidate.code) { HealthGuides.forCode(record.candidate.code) }
    val guide = knownGuide.takeIf { !record.uncertain && record.candidate.score >= 0.5 } ?: HealthGuides.general

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(healthRecordDate(record.createdAt), color = TextSecondary)
        Text(knownGuide?.title?.removePrefix("Dugaan ")?.replaceFirstChar { it.uppercase() } ?: record.candidate.label, style = MaterialTheme.typography.headlineSmall)
        Text(record.candidate.label, style = MaterialTheme.typography.bodyLarge)
        HealthCaution(
            if (record.uncertain) "Ambil foto gejala dari dekat untuk pemeriksaan berikutnya."
            else "Pantau perubahan tanaman dan catat perawatannya.",
        )
        record.photoRefs.forEachIndexed { index, path ->
            PlantPhoto(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                photoRef = path,
                contentDescription = "Foto pemeriksaan tersimpan ${index + 1}",
            )
        }
        HealthHeading("Perkembangan menurut Anda")
        Column(Modifier.selectableGroup()) {
            HealthTrackingStatus.entries.forEach { option ->
                Text(
                    text = (if (status == option) "● " else "○ ") + option.label,
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = status == option,
                        enabled = !busy,
                        role = Role.RadioButton,
                        onClick = { status = option; message = null },
                    ).heightIn(min = 48.dp).padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(2000); message = null },
            label = { Text("Catatan gejala dan perawatan") },
            supportingText = { Text("Misalnya: sejak kapan, cahaya, siram, dan perubahan daun. Catatan hanya disimpan di HP.") },
            minLines = 3,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = if (busy) "Menyimpan…" else "Simpan perkembangan",
            enabled = !busy,
            onClick = {
                busy = true
                onBusy(true)
                error = null
                scope.launch {
                    try {
                        container.healthRecords.updateTracking(record.id, status, notes)
                        message = "Perkembangan tersimpan di HP."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "Perubahan belum tersimpan. Coba lagi."
                    } finally {
                        busy = false
                        onBusy(false)
                    }
                }
            },
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Hapus pemeriksaan", color = MaterialTheme.colorScheme.error) }
        knownGuide?.let {
            HealthHeading("Cocokkan gejalanya")
            Text(it.symptoms)
        }
        HealthGuideContent(guide)
        HealthSourceLink("Analisis foto oleh Pl@ntNet", "https://plantnet.org/")
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Hapus pemeriksaan?") },
            text = { Text("Hasil, catatan, dan foto pemeriksaan yang tidak dipakai di tempat lain akan dihapus. Tanaman di kebun tetap ada.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        onBusy(true)
                        scope.launch {
                            try {
                                container.healthRecords.delete(record.id)
                                onDeleted()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "Pemeriksaan belum terhapus. Coba lagi."
                            } finally {
                                confirmDelete = false
                                busy = false
                                onBusy(false)
                            }
                        }
                    },
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Batal") } },
        )
    }
}

private val healthRecordFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
private fun healthRecordDate(timestamp: Long): String =
    healthRecordFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
