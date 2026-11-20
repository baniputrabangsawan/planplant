package com.jagadaun.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.theme.Outline
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import com.jagadaun.app.ui.theme.Surface as AppSurface

@Composable
fun HealthHubScreen(
    container: AppContainer,
    onCheckHealth: (String?) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val plants by container.plants.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Kesehatan tanaman",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Surface(color = SoftAccent, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Siapkan 1–5 foto tanaman yang sama",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text("Tanaman utuh → gejala dekat → sisi lain.", color = TextSecondary)
                    Text("Gunakan cahaya terang; satu foto cukup untuk mulai.", color = TextSecondary)
                    PrimaryButton(
                        text = "Periksa tanaman",
                        icon = Icons.Filled.Healing,
                        onClick = { onCheckHealth(null) },
                    )
                }
            }
        }
        item {
            SecondaryButton(
                text = "Riwayat pemeriksaan",
                icon = Icons.Filled.History,
                onClick = onOpenHistory,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Periksa tanaman di kebun",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (plants.isEmpty()) {
                        "Belum ada tanaman tersimpan. Anda tetap bisa memeriksa tanaman lewat tombol di atas."
                    } else {
                        "Pilih tanaman agar pemeriksaan dapat disimpan bersama catatannya."
                    },
                    color = TextSecondary,
                )
            }
        }
        items(plants, key = { it.id }) { plant ->
            Card(
                onClick = { onCheckHealth(plant.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, Outline),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlantPhoto(modifier = Modifier.size(64.dp), photoRef = plant.photoRef)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(plant.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        plant.scientificName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Text("Periksa gejala", style = MaterialTheme.typography.bodyMedium, color = Primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Primary)
                }
            }
        }
    }
}
