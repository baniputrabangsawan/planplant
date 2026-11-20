package com.jagadaun.app.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.domain.Reminder
import com.jagadaun.app.ui.dayLabel
import com.jagadaun.app.ui.isTodayOrOverdue
import com.jagadaun.app.ui.components.EmptyState
import com.jagadaun.app.ui.theme.Error
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.Surface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary

@Composable
fun RemindersScreen(
    container: AppContainer,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminders by container.reminders.collectAsStateWithLifecycle()
    val plants by container.plants.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }

    val today = reminders.filter { it.active && it.nextDue.isTodayOrOverdue() }
    val upcoming = reminders.filter { it.active && !it.nextDue.isTodayOrOverdue() }
    val paused = reminders.filter { !it.active }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Pengingat", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            ReminderPermissionNotice(container)
            Spacer(Modifier.height(12.dp))
            if (reminders.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Notifications,
                    title = "Belum ada pengingat.",
                    body = "Atur jadwal siram dan perawatan agar tanamanmu terawat.",
                    primaryText = "Tambah Pengingat",
                    onPrimary = onAdd,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupHeader("Hari ini", today.isNotEmpty())
                    items(today, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            plantName = plants.firstOrNull { it.id == reminder.plantId }?.displayName ?: "Tanaman",
                            onComplete = { container.completeReminder(reminder.id, reminder.nextDue) },
                            onEdit = { onEdit(reminder.id) },
                            onTogglePause = { container.updateReminder(reminder.copy(active = !reminder.active)) },
                            onDelete = { pendingDelete = reminder },
                        )
                    }
                    groupHeader("Berikutnya", upcoming.isNotEmpty())
                    items(upcoming, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            plantName = plants.firstOrNull { it.id == reminder.plantId }?.displayName ?: "Tanaman",
                            onComplete = { container.completeReminder(reminder.id, reminder.nextDue) },
                            onEdit = { onEdit(reminder.id) },
                            onTogglePause = { container.updateReminder(reminder.copy(active = !reminder.active)) },
                            onDelete = { pendingDelete = reminder },
                        )
                    }
                    groupHeader("Dijeda", paused.isNotEmpty())
                    items(paused, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            plantName = plants.firstOrNull { it.id == reminder.plantId }?.displayName ?: "Tanaman",
                            onComplete = { container.completeReminder(reminder.id, reminder.nextDue) },
                            onEdit = { onEdit(reminder.id) },
                            onTogglePause = { container.updateReminder(reminder.copy(active = !reminder.active)) },
                            onDelete = { pendingDelete = reminder },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Tambah Pengingat") },
            containerColor = Primary,
            contentColor = Color.White,
        )
    }

    pendingDelete?.let { reminder ->
        val plantName = plants.firstOrNull { it.id == reminder.plantId }?.displayName ?: "Tanaman"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus pengingat?") },
            text = { Text("${reminder.careType} untuk $plantName akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    container.deleteReminder(reminder.id)
                    pendingDelete = null
                }) { Text("Hapus", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupHeader(title: String, visible: Boolean) {
    if (visible) {
        item(key = "header-$title") {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    plantName: String,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(careIcon(reminder.careType), contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(plantName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "${reminder.careType} · ${reminder.preferredTime} · ${reminder.nextDue.dayLabel()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton("Selesai", Icons.Filled.CheckCircle, onComplete)
                ActionButton("Ubah", Icons.Filled.Edit, onEdit)
                if (reminder.active) {
                    ActionButton("Jeda", Icons.Filled.PauseCircle, onTogglePause)
                } else {
                    ActionButton("Lanjut", Icons.Filled.PlayCircle, onTogglePause)
                }
                ActionButton("Hapus", Icons.Filled.Delete, onDelete, color = Error)
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun careIcon(careType: String): ImageVector = when {
    careType.contains("Siram", ignoreCase = true) -> Icons.Filled.WaterDrop
    careType.contains("Pupuk", ignoreCase = true) -> Icons.Filled.Grass
    careType.contains("Pangkas", ignoreCase = true) -> Icons.Filled.ContentCut
    else -> Icons.Filled.Notifications
}
