package com.jagadaun.app.ui.identify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthGuide
import com.jagadaun.app.domain.HealthGuides
import com.jagadaun.app.domain.HealthResult
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.health.HealthGuideContent
import com.jagadaun.app.ui.health.HealthHeading
import com.jagadaun.app.ui.theme.TextSecondary

/** Only curated, sufficiently matched hypotheses receive cause-specific first steps. */
internal fun analysisHealthGuide(result: HealthResult?, selected: HealthCandidate?): HealthGuide {
    if (result == null || result.uncertain || selected == null || selected !in result.candidates ||
        !(selected.score >= 0.5)
    ) return HealthGuides.general
    return HealthGuides.forCode(selected.code) ?: HealthGuides.general
}

@Composable
internal fun AnalysisHealthSection(
    state: IdentificationUiState,
    enabled: Boolean,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val result = state.healthResult
    val selected = state.selectedHealthCandidate
    val editable = enabled && state.savedHealthRecord == null
    val guide = remember(result, selected) { analysisHealthGuide(result, selected) }
    val knownGuide = remember(selected?.code) { selected?.let { HealthGuides.forCode(it.code) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HealthHeading("Kondisi tanaman")
        state.healthErrorCode?.let {
            Text(
                analysisHealthErrorMessage(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (result != null) {
            if (selected == null) {
                Text(
                    "Ambil ulang: dekatkan bagian bergejala. Belum ada kesimpulan kesehatan.",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Text(
                    knownGuide?.title?.removePrefix("Dugaan ")?.replaceFirstChar { it.uppercase() } ?: selected.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (knownGuide != null) {
                    Text(selected.label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (result.uncertain) {
                    Text(
                        "Ambil ulang: foto gejala dari dekat dan seluruh tanaman.",
                        color = TextSecondary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        HealthGuideContent(guide)
        if (selected != null) {
            if (state.savedHealthRecord == null) {
                OutlinedTextField(
                    value = state.healthNotes,
                    onValueChange = onNotesChange,
                    label = { Text("Catatan (opsional, hanya di HP)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    minLines = 2,
                )
                PrimaryButton(
                    text = if (state.savingHealth) "Menyimpan…" else "Simpan hasil ke riwayat",
                    onClick = onSave,
                    enabled = editable && state.assessmentId != null,
                )
            } else {
                Text(
                    "Tersimpan di Riwayat.",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            state.healthSaveError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

private fun analysisHealthErrorMessage(code: String): String = when (code) {
    "OFFLINE" -> "Pemeriksaan perlu internet. Sambungkan internet lalu coba Analisis ulang."
    "CONNECT_TIMEOUT", "READ_TIMEOUT", "TIMEOUT", "CONNECTION_ERROR" -> "Layanan pemeriksaan belum bisa dihubungi. Periksa koneksi lalu coba lagi."
    "DAILY_LIMIT", "PROVIDER_QUOTA" -> "Kuota analisis belum tersedia. Panduan pencegahan tetap bisa dibaca."
    "UNAUTHORIZED", "PROVIDER_AUTH", "NOT_CONFIGURED" -> "Akses pemeriksaan perlu diperbarui oleh pengelola aplikasi."
    "NOT_RECOGNIZED" -> "Ambil ulang: dekatkan bagian bergejala. Belum ada kesimpulan kesehatan."
    "INVALID_IMAGE", "IMAGE_TOO_LARGE", "DEVICE_STORAGE" -> "Foto belum dapat diperiksa. Pilih foto lain yang jelas."
    else -> "Pemeriksaan belum berhasil. Coba analisis ulang."
}
