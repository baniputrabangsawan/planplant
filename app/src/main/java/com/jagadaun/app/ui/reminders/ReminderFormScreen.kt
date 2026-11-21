package com.jagadaun.app.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.domain.nextReminderDue
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.theme.Surface
import com.jagadaun.app.ui.theme.TextSecondary
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val CARE_OPTIONS = listOf("Siram", "Pupuk", "Pangkas", "Lainnya")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderFormScreen(
    container: AppContainer,
    reminderId: String?,
    presetPlantId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val reminders by container.reminders.collectAsStateWithLifecycle()
    val existing = remember(reminders, reminderId) { reminders.firstOrNull { it.id == reminderId } }
    if (reminderId != null && existing == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Ubah Pengingat", onBack = onBack)
            Text("Pengingat tidak tersedia. Kembali untuk melihat jadwal terbaru.", modifier = Modifier.padding(16.dp))
        }
        return
    }

    var selectedPlantId by rememberSaveable(reminderId) { mutableStateOf(existing?.plantId ?: presetPlantId) }
    var careType by rememberSaveable(reminderId) { mutableStateOf(existing?.careType ?: "Siram") }
    var interval by rememberSaveable(reminderId) { mutableStateOf((existing?.intervalDays ?: 7).toString()) }
    var time by rememberSaveable(reminderId) { mutableStateOf(existing?.preferredTime ?: "07:00") }
    var intervalError by rememberSaveable { mutableStateOf(false) }
    var timeError by rememberSaveable { mutableStateOf(false) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val editable = !saving && !saved
    val selectedPlant = remember(plants, selectedPlantId) {
        if (selectedPlantId == null) plants.firstOrNull() else plants.firstOrNull { it.id == selectedPlantId }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(title = if (reminderId == null) "Tambah Pengingat" else "Ubah Pengingat", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Jadwal perawatan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Pilih tanaman, kegiatan, dan waktu. Periksa kondisi tanaman sebelum mengikuti jadwal.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tanaman", style = MaterialTheme.typography.titleMedium)
                    Box {
                        OutlinedButton(
                            onClick = { dropdownOpen = true },
                            enabled = editable && plants.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Text(selectedPlant?.displayName ?: "Pilih tanaman", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                            plants.forEach { plant ->
                                DropdownMenuItem(
                                    text = { Text(plant.displayName, style = MaterialTheme.typography.bodyLarge) },
                                    onClick = {
                                        selectedPlantId = plant.id
                                        dropdownOpen = false
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                    if (plants.isEmpty()) {
                        Text("Tambahkan tanaman di Kebun Saya terlebih dahulu.", style = MaterialTheme.typography.bodyLarge)
                    } else if (selectedPlant == null) {
                        Text("Tanaman sebelumnya tidak tersedia. Pilih tanaman lain.", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("Kegiatan", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CARE_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = careType == option,
                                onClick = { careType = option },
                                enabled = editable,
                                label = { Text(option, style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = interval,
                        onValueChange = {
                            interval = it.filter { c -> c.isDigit() }
                            intervalError = false
                        },
                        enabled = editable,
                        label = { Text("Ulangi setiap (hari)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = intervalError,
                        supportingText = { Text(if (intervalError) "Isi dengan angka minimal 1." else "Contoh: 7 untuk setiap tujuh hari.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it; timeError = false },
                        enabled = editable,
                        label = { Text("Waktu (HH:mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        isError = timeError,
                        supportingText = { Text(if (timeError) "Isi waktu 24 jam, misalnya 07:00." else "Gunakan format 24 jam, misalnya 07:00.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (saved || saveError != null) {
                Text(
                    if (saved) "Pengingat tersimpan." else saveError.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(
                text = when {
                    saved -> "Lihat Pengingat"
                    saving -> "Menyimpan…"
                    saveError != null -> "Coba Simpan Lagi"
                    else -> "Simpan Pengingat"
                },
                onClick = {
                    if (saved) {
                        onDone()
                    } else if (!saving) {
                        val plant = selectedPlant
                        val days = interval.toIntOrNull()
                        val validTime = time.length == 5 && time[2] == ':' && try {
                            LocalTime.parse(time)
                            true
                        } catch (_: DateTimeParseException) {
                            false
                        }
                        intervalError = days == null || days < 1
                        timeError = !validTime
                        if (!intervalError && validTime && plant != null && days != null) {
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    val zone = existing?.zoneId ?: ZoneId.systemDefault().id
                                    val nextDue = nextReminderDue(days, time, zone, System.currentTimeMillis())
                                    if (existing == null) {
                                        container.addReminder(
                                            plantId = plant.id,
                                            careType = careType,
                                            intervalDays = days,
                                            preferredTime = time,
                                            zoneId = zone,
                                            nextDue = nextDue,
                                        )
                                        saved = true
                                    } else if (container.updateReminder(
                                        existing.copy(
                                            plantId = plant.id,
                                            careType = careType,
                                            intervalDays = days,
                                            preferredTime = time,
                                            nextDue = nextDue,
                                        ),
                                    )) {
                                        saved = true
                                    } else {
                                        saveError = "Pengingat sudah dihapus. Kembali untuk melihat jadwal terbaru."
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    saveError = "Pengingat belum tersimpan. Coba simpan lagi."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                },
                enabled = !saving && (saved || selectedPlant != null),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
