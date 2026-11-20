package com.jagadaun.app.ui.identify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.jagadaun.app.BuildConfig
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.dayLabel
import com.jagadaun.app.ui.isTodayOrOverdue
import com.jagadaun.app.ui.toLocalDate
import com.jagadaun.app.ui.theme.Outline
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.Surface as AppSurface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    modifier: Modifier = Modifier,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val reminders by container.reminders.collectAsStateWithLifecycle()
    val historyFlow = remember(container) { container.healthRecords.observe(null) }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
    val dueCare = remember(reminders, today) {
        reminders.filter { it.active && it.nextDue.isTodayOrOverdue(today) }.sortedBy { it.nextDue }
    }
    val carePreview = remember(dueCare) { dueCare.take(3) }
    val plantNames = remember(plants) { plants.associate { it.id to it.displayName } }
    val latest = remember(history) { history.maxByOrNull { it.createdAt } }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Eco, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Jaga Daun",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            "Versi ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            if (busy) "Menyiapkan foto…" else "Kenali namanya, amati gejalanya, rawat setiap hari.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Surface(color = SoftAccent, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Kenali tanaman dari foto",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Lihat dugaan nama dan kondisi tanaman dalam satu hasil. Foto bukan kepastian diagnosis.",
                    color = TextSecondary,
                )
                PrimaryButton(
                    text = "Ambil Foto",
                    onClick = onTakePhoto,
                    icon = Icons.Filled.CameraAlt,
                    enabled = !busy,
                )
                SecondaryButton(
                    text = "Pilih dari Galeri",
                    onClick = onPickFromGallery,
                    icon = Icons.Filled.PhotoLibrary,
                    enabled = !busy,
                )
            }
        }
        HomeLink(
            title = "Tanaman di sekitar",
            description = "Lihat tanaman yang paling sering tercatat di sekitar lokasi Anda dan peta sebarannya.",
            icon = Icons.Filled.Eco,
            enabled = !busy,
            onClick = onOpenNearby,
        )
        Surface(
            color = AppSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Outline),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Healing, contentDescription = null, tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Penyakit atau hama?",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                Text(
                    "Periksa 1–5 foto tanaman, lalu baca dugaan penyebab dan pencegahannya. Bukan diagnosis pasti.",
                    color = TextSecondary,
                )
                PrimaryButton(
                    text = "Periksa penyakit & hama",
                    onClick = onCheckHealth,
                    icon = Icons.Filled.Healing,
                    enabled = !busy,
                )
            }
        }
        Surface(
            color = AppSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Outline),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Perawatan hari ini · ${dueCare.size}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                if (dueCare.isEmpty()) {
                    Text(
                        "Tidak ada pengingat aktif yang jatuh tempo hari ini atau terlambat.",
                        color = TextSecondary,
                    )
                } else {
                    carePreview.forEach { reminder ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${reminder.careType} · ${plantNames[reminder.plantId] ?: "Tanaman"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                "${reminder.nextDue.dayLabel(today)} · ${reminder.preferredTime}",
                                color = TextSecondary,
                            )
                        }
                    }
                    if (dueCare.size > carePreview.size) {
                        Text(
                            "${dueCare.size - carePreview.size} perawatan lain menunggu.",
                            color = TextSecondary,
                        )
                    }
                }
                SecondaryButton(
                    text = "Buka pengingat",
                    onClick = onOpenReminders,
                    icon = Icons.Filled.Notifications,
                    enabled = !busy,
                )
            }
        }
        HomeLink(
            title = "Kebun Saya",
            description = "${plants.size} tanaman tersimpan. Buka koleksi dan catatan perawatannya.",
            icon = Icons.Filled.Eco,
            enabled = !busy,
            onClick = onOpenGarden,
        )
        HomeLink(
            title = "Riwayat pemeriksaan",
            description = latest?.let {
                "${history.size} pemeriksaan tersimpan. Terakhir ${it.createdAt.toLocalDate().format(homeDateFormat)} · ${it.status.label}."
            } ?: "Belum ada pemeriksaan tersimpan. Buka riwayat untuk melihat hasil yang disimpan.",
            icon = Icons.Filled.History,
            enabled = !busy,
            onClick = onOpenHistory,
        )
    }
}

@Composable
private fun HomeLink(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, Outline),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Primary)
        }
    }
}

private val homeDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))
