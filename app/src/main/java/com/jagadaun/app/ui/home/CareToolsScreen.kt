package com.jagadaun.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jagadaun.app.domain.HealthGuides
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.health.HealthGuideContent
import com.jagadaun.app.ui.health.HealthHeading
import com.jagadaun.app.ui.theme.Outline
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import com.jagadaun.app.ui.theme.Surface as AppSurface

@Composable
fun CareToolsScreen(
    onOpenReminders: () -> Unit,
    onOpenHistory: () -> Unit,
    onCheckHealth: () -> Unit,
    onOpenNearby: () -> Unit,
) {
    var guideExpanded by rememberSaveable { mutableStateOf(false) }
    val guide = HealthGuides.general

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Lainnya",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text("Catatan dan panduan untuk merawat tanaman Anda.", color = TextSecondary)
        SecondaryButton(
            text = "Tanaman di sekitar",
            icon = Icons.Filled.Eco,
            onClick = onOpenNearby,
        )
        SecondaryButton(
            text = "Pengingat perawatan",
            icon = Icons.Filled.Notifications,
            onClick = onOpenReminders,
        )
        SecondaryButton(
            text = "Riwayat pemeriksaan",
            icon = Icons.Filled.History,
            onClick = onOpenHistory,
        )
        SecondaryButton(
            text = "Periksa penyakit & hama",
            icon = Icons.Filled.Healing,
            onClick = onCheckHealth,
        )
        Surface(
            color = AppSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Outline),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HealthHeading("Panduan perawatan umum")
                Text(guide.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Isi panduan dapat dibaca tanpa internet. Tautan sumber memerlukan internet dan dibuka di peramban.",
                    color = TextSecondary,
                )
                TextButton(
                    onClick = { guideExpanded = !guideExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            stateDescription = if (guideExpanded) "Panduan terbuka" else "Panduan tertutup"
                        },
                ) {
                    Text(if (guideExpanded) "Tutup panduan" else "Baca panduan")
                }
                if (guideExpanded) {
                    HealthHeading("Mengenali gejala")
                    Text(guide.symptoms, style = MaterialTheme.typography.bodyLarge)
                    HealthGuideContent(guide)
                }
            }
        }
    }
}
