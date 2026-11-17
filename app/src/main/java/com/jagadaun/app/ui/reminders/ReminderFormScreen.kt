package com.jagadaun.app.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar

private const val DAY_MS = 24L * 60 * 60 * 1000
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
    val existing = reminderId?.let { id -> reminders.firstOrNull { it.id == id } }

    var selectedPlantId by remember { mutableStateOf(existing?.plantId ?: presetPlantId ?: plants.firstOrNull()?.id) }
    var careType by remember { mutableStateOf(existing?.careType ?: "Siram") }
    var interval by remember { mutableStateOf((existing?.intervalDays ?: 7).toString()) }
    var time by remember { mutableStateOf(existing?.preferredTime ?: "07:00") }
    var intervalError by remember { mutableStateOf(false) }
    var dropdownOpen by remember { mutableStateOf(false) }

    val selectedPlantName = plants.firstOrNull { it.id == selectedPlantId }?.displayName ?: ""

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = if (existing == null) "Tambah Pengingat" else "Ubah Pengingat", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = selectedPlantName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tanaman") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dropdownOpen = true },
            )
            DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                plants.forEach { plant ->
                    DropdownMenuItem(
                        text = { Text(plant.displayName) },
                        onClick = {
                            selectedPlantId = plant.id
                            dropdownOpen = false
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Kegiatan", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow {
                CARE_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = careType == option,
                        onClick = { careType = option },
                        label = { Text(option) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = interval,
                onValueChange = {
                    interval = it.filter { c -> c.isDigit() }
                    if (intervalError) intervalError = false
                },
                label = { Text("Interval (hari)") },
                singleLine = true,
                isError = intervalError,
                supportingText = if (intervalError) {
                    { Text("Isi dengan angka minimal 1") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text("Waktu (HH:mm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Simpan",
                onClick = {
                    val pid = selectedPlantId
                    val days = interval.toIntOrNull()
                    if (days == null || days < 1) {
                        intervalError = true
                    } else if (pid == null) {
                        // Tanpa tanaman, tidak bisa menyimpan.
                    } else {
                        val nextDue = System.currentTimeMillis() + days * DAY_MS
                        if (existing == null) {
                            container.addReminder(
                                plantId = pid,
                                careType = careType,
                                intervalDays = days,
                                preferredTime = time.ifBlank { "07:00" },
                                nextDue = nextDue,
                            )
                        } else {
                            container.updateReminder(
                                existing.copy(
                                    plantId = pid,
                                    careType = careType,
                                    intervalDays = days,
                                    preferredTime = time.ifBlank { "07:00" },
                                    nextDue = nextDue,
                                ),
                            )
                        }
                        onDone()
                    }
                },
                enabled = selectedPlantId != null,
            )
        }
    }
}
