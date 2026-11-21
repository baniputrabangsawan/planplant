package com.jagadaun.app.ui.identify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.components.PlantCard
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.home.NearbySection
import com.jagadaun.app.ui.dayLabel
import com.jagadaun.app.ui.isTodayOrOverdue
import com.jagadaun.app.ui.theme.Divider
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.Surface as AppSurface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun IdentifyScreen(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    busy: Boolean,
    onCheckHealth: () -> Unit,
    onOpenHistory: () -> Unit,
    container: AppContainer,
    onOpenGarden: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenPlant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val reminders by container.reminders.collectAsStateWithLifecycle()
    val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
    val nextByPlant = remember(reminders) {
        reminders.filter { it.active }.groupBy { it.plantId }.mapValues { (_, list) -> list.minBy { it.nextDue } }
    }
    val latestPlants = remember(plants) { plants.take(4) }
    val plantsById = remember(plants) { plants.associateBy { it.id } }
    val care = remember(reminders, today) {
        reminders.filter { it.active }.sortedBy { it.nextDue }
    }
    val dueCount = remember(care, today) { care.count { it.nextDue.isTodayOrOverdue(today) } }
    val carePreview = remember(care) { care.take(2) }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Eco, null, tint = Primary, modifier = Modifier.size(32.dp))
                Column {
                    Text("Jaga Daun", style = MaterialTheme.typography.headlineSmall, color = TextPrimary,
                        modifier = Modifier.semantics { heading() })
                    Text("Kenali dan rawat tanaman Anda", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }
        }
        item(key = "identify") {
            Surface(color = SoftAccent, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tanaman apa ini?", style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary, modifier = Modifier.semantics { heading() })
                    Text("Ambil foto daun, bunga, atau buah yang jelas.", style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary)
                    PrimaryButton("Kenali Tanaman", onTakePhoto, icon = Icons.Filled.CameraAlt, enabled = !busy)
                    Text("Galeri tersedia di dalam kamera.", style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary)
                    if (busy) Text("Menyiapkan foto…", color = TextSecondary)
                }
            }
        }
        item(key = "health") {
            HomeLink("Periksa Masalah", "Dugaan penyakit atau hama. Bukan diagnosis pasti.",
                Icons.Filled.Healing, true, onCheckHealth)
        }
        item(key = "garden-heading") {
            HomeSectionHeader("Tanaman Anda", "Lihat Semua", onOpenGarden)
        }
        item(key = "garden-preview") {
            if (latestPlants.isEmpty()) {
                Surface(color = AppSurface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Divider)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Belum ada tanaman tersimpan.", style = MaterialTheme.typography.titleMedium)
                        Text("Kenali dari foto atau tambahkan sendiri ke Kebun Saya.", color = TextSecondary)
                        SecondaryButton("Buka Kebun Saya", onOpenGarden, icon = Icons.Filled.Eco)
                    }
                }
            } else {
                LazyRow(
                    state = rememberLazyListState(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(latestPlants, key = { it.id }) { plant ->
                        PlantCard(plant, nextByPlant[plant.id], { onOpenPlant(plant.id) }, Modifier.width(224.dp))
                    }
                }
            }
        }
        item(key = "nearby-live") {
            NearbySection(onOpenNearby = onOpenNearby)
        }
        item(key = "care-heading") {
            HomeSectionHeader(
                if (dueCount > 0) "Perawatan Hari Ini · $dueCount" else "Pengingat Terdekat",
                "Lihat Semua",
                onOpenReminders,
            )
        }
        if (carePreview.isEmpty()) {
            item(key = "care-empty") {
                HomeLink("Belum ada jadwal aktif", "Atur pengingat siram atau kegiatan lainnya.",
                    Icons.Filled.Notifications, true, onOpenReminders)
            }
        } else {
            items(carePreview, key = { "care-${it.id}" }) { reminder ->
                val plant = plantsById[reminder.plantId]
                Card(
                    onClick = onOpenReminders,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlantPhoto(Modifier.size(64.dp), photoRef = plant?.photoRef)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(plant?.displayName ?: "Tanaman", style = MaterialTheme.typography.titleMedium)
                            Text("${reminder.careType} · ${reminder.nextDue.dayLabel(today)} · ${reminder.preferredTime}",
                                color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        item(key = "history") {
            HomeLink("Riwayat pemeriksaan", "Baca kembali hasil dan catatan yang tersimpan.",
                Icons.Filled.History, true, onOpenHistory)
        }
        item(key = "tools") {
            HomeLink("Panduan & alat", "Bacaan perawatan, pengingat, dan pemeriksaan.",
                Icons.Filled.MenuBook, true, onOpenTools)
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
private fun HomeLink(title: String, description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, Divider),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Primary)
        }
    }
}
