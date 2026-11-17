package com.jagadaun.app.ui.garden

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.theme.Error
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PlantDetailScreen(
    container: AppContainer,
    plantId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddReminder: () -> Unit,
    onCheckHealth: () -> Unit,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
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

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = plant.displayName, onBack = leave)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PlantPhoto(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                iconSize = 72.dp,
                photoRef = plant.photoRef,
                contentDescription = "Foto ${plant.displayName}",
            )
            Spacer(Modifier.height(16.dp))
            Text(plant.displayName, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            plant.scientificName?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (plant.notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Catatan")
                Spacer(Modifier.height(4.dp))
                Text(plant.notes, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            plant.guide?.let { guide ->
                Spacer(Modifier.height(16.dp))
                SectionTitle("Panduan perawatan")
                Spacer(Modifier.height(8.dp))
                GuideRow(Icons.Filled.WbSunny, "Cahaya", guide.light)
                GuideRow(Icons.Filled.WaterDrop, "Siram", guide.water)
                GuideRow(Icons.Filled.Grass, "Media tanam", guide.soil)
                Spacer(Modifier.height(4.dp))
                Text("Sumber: ${guide.source}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(Modifier.height(24.dp))
            SecondaryButton(
                text = "Periksa penyakit & hama",
                onClick = onCheckHealth,
                icon = Icons.Filled.Healing,
                enabled = !deleting,
            )
            Spacer(Modifier.height(12.dp))
            deleteError?.let { Text(it, color = Error) }
            PrimaryButton(text = "Ubah", onClick = onEdit, enabled = !deleting)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "Atur Pengingat", onClick = onAddReminder, icon = Icons.Filled.Notifications, enabled = !deleting)
            Spacer(Modifier.height(8.dp))
            TextButton(enabled = !deleting, onClick = { showDelete = true }) {
                Text(if (deleting) "Menghapus…" else "Hapus tanaman", color = Error)
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
    Text(text, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
}

@Composable
private fun GuideRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}
