package com.jagadaun.app.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.domain.Plant
import com.jagadaun.app.domain.Reminder
import com.jagadaun.app.ui.components.EmptyState
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.theme.Error
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.Surface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ReminderAction { Complete, TogglePause, Delete }

@Composable
fun RemindersScreen(
    container: AppContainer,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminders by container.reminders.collectAsStateWithLifecycle()
    val plants by container.plants.collectAsStateWithLifecycle()
    val plantsById = remember(plants) { plants.associateBy { it.id } }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }
    var pendingActions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
    var fabHeight by remember { mutableStateOf(0) }
    val fabPadding = with(LocalDensity.current) { fabHeight.toDp() } + 32.dp

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val sections = remember(reminders, today) {
        val dueToday = mutableListOf<Reminder>()
        val upcoming = mutableListOf<Reminder>()
        val paused = mutableListOf<Reminder>()
        for (reminder in reminders.sortedBy { it.nextDue }) {
            when {
                !reminder.active -> paused.add(reminder)
                !Instant.ofEpochMilli(reminder.nextDue).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(today) ->
                    dueToday.add(reminder)
                else -> upcoming.add(reminder)
            }
        }
        listOf("Hari Ini" to dueToday, "Berikutnya" to upcoming, "Dijeda" to paused)
    }

    fun perform(reminder: Reminder, action: ReminderAction) {
        if (reminder.id in pendingActions) return
        pendingActions = pendingActions + reminder.id
        scope.launch {
            var message = ""
            var failed = false
            try {
                message = when (action) {
                    ReminderAction.Complete -> if (container.completeReminder(reminder.id, reminder.nextDue)) {
                        "Perawatan ditandai selesai. Jadwal berikutnya sudah disimpan."
                    } else {
                        "Jadwal sudah berubah atau dijeda. Periksa jadwal terbaru sebelum menandai selesai."
                    }
                    ReminderAction.TogglePause -> if (container.setReminderActive(reminder.id, !reminder.active)) {
                        if (reminder.active) "Pengingat dijeda." else "Pengingat dilanjutkan."
                    } else {
                        "Pengingat sudah tidak tersedia."
                    }
                    ReminderAction.Delete -> {
                        container.deleteReminder(reminder.id)
                        "Pengingat dihapus."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                message = "Perubahan belum tersimpan. Coba lagi."
            } finally {
                pendingActions = pendingActions - reminder.id
            }
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = if (failed) "Coba lagi" else null,
                withDismissAction = true,
            )
            if (failed && result == SnackbarResult.ActionPerformed) perform(reminder, action)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.widthIn(max = 280.dp)
                    .onSizeChanged { fabHeight = it.height }
                    .semantics { contentDescription = "Tambah pengingat" },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Tambah Pengingat", style = MaterialTheme.typography.labelLarge) },
                containerColor = Primary,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = fabPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title") {
                // Judul sudah ada di top bar rute; di sini cukup penjelasannya.
                Text(
                    "Rawat tanaman sesuai jadwalmu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
            item(key = "permission") { ReminderPermissionNotice(container) }
            if (reminders.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Notifications,
                        title = "Belum ada pengingat.",
                        body = "Tambahkan jadwal siram atau perawatan untuk tanaman di kebunmu.",
                        primaryText = "Tambah Pengingat",
                        onPrimary = onAdd,
                    )
                }
            } else {
                sections.forEach { (title, scheduled) ->
                    if (scheduled.isNotEmpty() || title == "Hari Ini") {
                        item(key = "header-$title") {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                            )
                        }
                        if (scheduled.isEmpty()) {
                            item(key = "empty-today") {
                                Text("Tidak ada perawatan terjadwal hari ini.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                            }
                        }
                        items(scheduled, key = { it.id }, contentType = { "reminder" }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                plant = plantsById[reminder.plantId],
                                now = now,
                                busy = reminder.id in pendingActions,
                                onComplete = { perform(reminder, ReminderAction.Complete) },
                                onEdit = { if (reminder.id !in pendingActions) onEdit(reminder.id) },
                                onTogglePause = { perform(reminder, ReminderAction.TogglePause) },
                                onDelete = { pendingDelete = reminder },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus pengingat?") },
            text = { Text("${reminder.careType} untuk ${plantsById[reminder.plantId]?.displayName ?: "tanaman ini"} akan dihapus. Tanaman tetap ada di kebun.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        perform(reminder, ReminderAction.Delete)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Hapus pengingat", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Batal") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderCard(
    reminder: Reminder,
    plant: Plant?,
    now: Long,
    busy: Boolean,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
) {
    val due = remember(reminder.nextDue, reminder.zoneId) {
        Instant.ofEpochMilli(reminder.nextDue).atZone(ZoneId.of(reminder.zoneId))
    }
    val overdue = reminder.nextDue < now
    val schedule = remember(due) {
        due.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy · HH:mm", Locale.forLanguageTag("id-ID")))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                PlantPhoto(modifier = Modifier.size(64.dp), iconSize = 28.dp, photoRef = plant?.photoRef)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plant?.displayName ?: "Tanaman", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(reminder.careType, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
            Text(
                when {
                    !reminder.active -> "Dijeda · $schedule"
                    overdue -> "Terlewat · $schedule"
                    else -> schedule
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (overdue && reminder.active) MaterialTheme.colorScheme.error else TextSecondary,
            )
            if (reminder.active) {
                PrimaryButton(
                    text = if (busy) "Menyimpan perubahan…" else "Tandai Selesai",
                    onClick = onComplete,
                    enabled = !busy,
                    icon = Icons.Filled.CheckCircle,
                )
            } else if (busy) {
                Text("Menyimpan perubahan…", style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionButton("Ubah", Icons.Filled.Edit, onEdit, enabled = !busy)
                ActionButton(
                    if (reminder.active) "Jeda" else "Lanjut",
                    if (reminder.active) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                    onTogglePause,
                    enabled = !busy,
                )
            }
            HorizontalDivider()
            ActionButton("Hapus pengingat", Icons.Filled.Delete, onDelete, enabled = !busy, color = Error)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
