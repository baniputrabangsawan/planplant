package com.jagadaun.app.ui.identify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jagadaun.app.BuildConfig
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.theme.Outline
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.Surface as AppSurface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary

@Composable
fun IdentifyScreen(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    busy: Boolean,
    onCheckHealth: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Surface(color = SoftAccent, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "Versi ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            if (busy) "Menyiapkan foto…" else "Kenali namanya, periksa gejalanya, pantau perkembangannya.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Surface(color = SoftAccent, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tanaman apa ini?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text("Cari nama tanaman dari foto daun atau bunga.", color = TextSecondary)
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
                    "Periksa 1–3 foto tanaman, lalu baca dugaan penyebab dan pencegahannya. Bukan diagnosis pasti.",
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
        Card(
            onClick = onOpenHistory,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, Outline),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Riwayat pemeriksaan", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Buka hasil, foto, dan catatan perkembangan.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Primary)
            }
        }
    }
}
