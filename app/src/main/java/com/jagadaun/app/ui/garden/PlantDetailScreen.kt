package com.jagadaun.app.ui.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PlantKnowledgeSection
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.theme.Error
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PlantDetailScreen(
    container: AppContainer,
    plantId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (String) -> Unit,
    onCheckHealth: () -> Unit,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val reminders by container.reminders.collectAsStateWithLifecycle()
    val recordsFlow = remember(container, plantId) { container.healthRecords.observe(plantId) }
    val records by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    val galleryState = rememberLazyListState()
    val schedules = remember(reminders, plantId) {
        reminders.filter { it.plantId == plantId }.sortedBy { it.nextDue }
    }
    var showDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val leave: () -> Unit = { if (!deleting) onBack() }
    BackHandler(onBack = leave)
    val plant = plants.firstOrNull { it.id == plantId }
    if (plant == null) {
        Column {
            ScreenTopBar("Tanaman", leave)
            Text("Tanaman tidak ditemukan.", modifier = Modifier.padding(16.dp))
        }
        return
    }
    val photoRefs = remember(plant.photoRef, records) {
        buildList {
            plant.photoRef?.takeIf { it.isNotBlank() }?.let(::add)
            records.forEach { addAll(it.photoRefs) }
        }.distinct()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = plant.displayName, onBack = leave)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "photos") {
                if (photoRefs.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val photoWidth = maxWidth * 0.88f
                            LazyRow(
                                state = galleryState,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(photoRefs, key = { _, photoRef -> photoRef }) { index, photoRef ->
                                    PlantPhoto(
                                        modifier = Modifier.width(photoWidth).height(300.dp)
                                            .clip(RoundedCornerShape(24.dp)),
                                        iconSize = 72.dp,
                                        photoRef = photoRef,
                                        contentDescription = "Foto ${plant.displayName}, ${index + 1} dari ${photoRefs.size}",
                                    )
                                }
                            }
                        }
                        Text(
                            "${photoRefs.size} foto tersimpan · Geser untuk melihat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                } else {
                    PlantPhoto(
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        iconSize = 72.dp,
                        photoRef = photoRefs.firstOrNull(),
                        contentDescription = "Foto ${plant.displayName}",
                    )
                }
            }
            item(key = "identity") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        plant.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    plant.scientificName?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                    SecondaryButton(text = "Ubah tanaman", onClick = onEdit, enabled = !deleting)
                }
            }
            item(key = "knowledge") { PlantKnowledgeSection(plant.scientificName) }
            item(key = "reminder-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Jadwal perawatan")
                    if (schedules.isEmpty()) {
                        Text(
                            "Belum ada pengingat untuk tanaman ini.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                    SecondaryButton(
                        text = "Tambah pengingat",
                        onClick = onAddReminder,
                        icon = Icons.Filled.Notifications,
                        enabled = !deleting,
                    )
                }
            }
            items(schedules, key = { "reminder-${it.id}" }) { reminder ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(reminder.careType, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (reminder.active) "Aktif" else "Dijeda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Text(
                            "Setiap ${reminder.intervalDays} hari · ${reminder.preferredTime} (${reminder.zoneId})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                        val due = remember(reminder.nextDue, reminder.zoneId) {
                            Instant.ofEpochMilli(reminder.nextDue)
                                .atZone(ZoneId.of(reminder.zoneId))
                                .format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("id", "ID")))
                        }
                        Text(
                            "${if (reminder.active) "Jadwal berikutnya" else "Jadwal tersimpan"}: $due",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                        TextButton(
                            onClick = { onEditReminder(reminder.id) },
                            enabled = !deleting,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Ubah pengingat")
                        }
                    }
                }
            }
            plant.guide?.let { guide ->
                item(key = "care-guide") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SectionTitle("Panduan perawatan")
                            GuideRow(Icons.Filled.WbSunny, "Cahaya", guide.light)
                            GuideRow(Icons.Filled.WaterDrop, "Siram", guide.water)
                            GuideRow(Icons.Filled.Grass, "Media tanam", guide.soil)
                            Text("Sumber: ${guide.source}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
            if (plant.notes.isNotBlank()) {
                item(key = "notes") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SectionTitle("Catatan")
                            Text(plant.notes, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
            }
            item(key = "health") {
                PrimaryButton(
                    text = "Periksa penyakit & hama",
                    onClick = onCheckHealth,
                    icon = Icons.Filled.Healing,
                    enabled = !deleting,
                )
            }
            item(key = "delete") {
                Column {
                    deleteError?.let { Text(it, color = Error) }
                    TextButton(
                        enabled = !deleting,
                        onClick = { showDelete = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(if (deleting) "Menghapus…" else "Hapus tanaman", color = Error)
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus ${plant.displayName}?") },
            text = { Text("Tanaman, catatan, dan pengingatnya akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    deleting = true
                    scope.launch {
                        try {
                            container.deletePlant(plant.id)
                            onBack()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            deleteError = "Tanaman belum terhapus. Coba lagi."
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text("Hapus", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun GuideRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
    }
}
