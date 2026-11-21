package com.jagadaun.app.ui.identify

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.semantics.selected
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.Outline
import com.jagadaun.app.ui.theme.Surface
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jagadaun.app.data.identification.MAX_ANALYSIS_PHOTOS
import com.jagadaun.app.domain.PlantCandidate
import com.jagadaun.app.rememberAppContainer
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PlantKnowledgeSection
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.health.HealthSourceLink
import com.jagadaun.app.ui.theme.TextSecondary

@Composable
fun PreviewScreen(
    photoRefs: List<String>,
    busy: Boolean,
    onBack: () -> Unit,
    onReplacePhoto: () -> Unit,
    onAddAngle: () -> Unit,
    onSaveManual: () -> Unit,
    onCandidateSelected: (PlantCandidate) -> Unit,
    onPhotosSaved: (List<String>) -> Unit,
) {
    val container = rememberAppContainer()
    val factory = remember(container) {
        IdentificationViewModel.factory(container.identificationClient, container.healthRecords::save)
    }
    val identification: IdentificationViewModel = viewModel(factory = factory)
    val observedState by identification.state.collectAsStateWithLifecycle()
    val adoptingSavedPhotos = observedState.savedHealthRecord != null &&
        observedState.savedFromPhotoRefs == photoRefs && observedState.photoRefs != photoRefs
    // Adopt a completed commit across rotation without starting another upload.
    LaunchedEffect(photoRefs, observedState.savedHealthRecord?.id) {
        if (observedState.savedHealthRecord != null && observedState.savedFromPhotoRefs == photoRefs) {
            onPhotosSaved(observedState.photoRefs)
        } else {
            identification.setPhotos(photoRefs)
        }
    }
    val state = observedState.takeIf { it.photoRefs == photoRefs || adoptingSavedPhotos }
        ?: IdentificationUiState(photoRefs = photoRefs)
    val processing = busy || state.loading || state.savingHealth || adoptingSavedPhotos
    val result = state.result
    val candidate = state.selectedCandidate
    val errorCode = state.errorCode
    val hasOutcome = result != null || state.healthResult != null || errorCode != null || state.healthErrorCode != null
    val goBack = {
        if (!busy && !state.savingHealth && !adoptingSavedPhotos) {
            identification.setPhotos(emptyList())
            onBack()
        }
    }
    BackHandler(onBack = goBack)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Hasil tanaman", onBack = goBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.photoRefs, key = { it }) { ref ->
                    PlantPhoto(
                        modifier = Modifier.width(248.dp).height(if (hasOutcome) 144.dp else 248.dp),
                        iconSize = 64.dp,
                        photoRef = ref,
                        contentDescription = "Foto tanaman yang dipilih",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Foto dikirim melalui Cloudflare ke Pl@ntNet; 1 percobaan keluarga, 2 kredit penyedia (identitas + kondisi).",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = when {
                    state.loading -> "Sedang menganalisis…"
                    busy -> "Menyiapkan foto…"
                    hasOutcome -> "Analisis ulang"
                    else -> "Analisis tanaman"
                },
                onClick = { identification.analyze(state.photoRefs) },
                enabled = state.photoRefs.isNotEmpty() && !processing,
                icon = Icons.Filled.Eco,
            )
            Spacer(Modifier.height(16.dp))
            if (state.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Memeriksa identitas dan dugaan masalah…")
                }
                Spacer(Modifier.height(16.dp))
            }
            if (hasOutcome) {
                Text(
                    "Identitas tanaman",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (errorCode != null) {
                Text(
                    text = identificationErrorMessage(errorCode),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(16.dp))
            }
            if (result != null) {
                if (candidate == null) {
                    Text(
                        if (result.candidates.isNotEmpty()) {
                            "Nama belum yakin. Foto lebih dekat ke daun, bunga, atau buah."
                        } else {
                            "Ambil ulang: foto seluruh tanaman dan daun yang jelas."
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(candidate.displayName, style = MaterialTheme.typography.titleLarge)
                    if (candidate.displayName != candidate.scientificName) {
                        Text(
                            candidate.scientificName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    if (result.uncertain) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ambil ulang: dekatkan daun dan pastikan foto tajam.",
                            color = TextSecondary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                val choices = remember(result) { result.candidates.filter { it.score >= MIN_NAMEABLE_SCORE } }
                if (choices.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text("Pilih nama yang paling sesuai.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(choices, key = { it.scientificName }) { choice ->
                            val chosen = choice == candidate
                            Card(
                                onClick = { identification.selectCandidate(choice) },
                                enabled = !processing,
                                modifier = Modifier.width(248.dp).semantics { selected = chosen },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = if (chosen) SoftAccent else Surface),
                                border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) Primary else Outline),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(choice.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(choice.scientificName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(if (chosen) "Dipilih" else "Pilih kandidat", color = Primary,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (candidate != null) {
                PrimaryButton(
                    text = "Tambahkan ke koleksi",
                    onClick = { onCandidateSelected(candidate) },
                    enabled = !processing,
                    icon = Icons.Filled.Add,
                )
                Spacer(Modifier.height(16.dp))
                PlantKnowledgeSection(candidate.scientificName)
                Spacer(Modifier.height(16.dp))
            }
            if (hasOutcome) {
                AnalysisHealthSection(
                    state = state,
                    enabled = !processing,
                    onNotesChange = identification::setHealthNotes,
                    onSave = identification::saveHealth,
                )
                Spacer(Modifier.height(16.dp))
            }
            SecondaryButton(
                text = if (state.photoRefs.size < MAX_ANALYSIS_PHOTOS) {
                    "Tambah sudut foto (${state.photoRefs.size}/$MAX_ANALYSIS_PHOTOS)"
                } else {
                    "Ganti foto"
                },
                onClick = if (state.photoRefs.size < MAX_ANALYSIS_PHOTOS) onAddAngle else onReplacePhoto,
                icon = if (state.photoRefs.size < MAX_ANALYSIS_PHOTOS) Icons.Filled.Add else Icons.Filled.Refresh,
                enabled = !processing,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Tambah tanaman manual",
                onClick = onSaveManual,
                icon = Icons.Filled.Add,
                enabled = !processing,
            )
            Spacer(Modifier.height(8.dp))
            HealthSourceLink("Analisis foto oleh Pl@ntNet", "https://plantnet.org/")
        }
    }
}

private fun identificationErrorMessage(code: String): String = when (code) {
    "OFFLINE" -> "Pengenalan foto perlu internet. Periksa koneksi lalu coba lagi."
    "CONNECT_TIMEOUT" -> "Koneksi ke layanan terlalu lama. Periksa internet lalu coba lagi."
    "READ_TIMEOUT", "TIMEOUT" -> "Layanan terlalu lama mengenali foto. Silakan coba lagi."
    "CONNECTION_ERROR" -> "Layanan belum bisa dihubungi. Periksa koneksi lalu coba lagi."
    "DAILY_LIMIT" -> "Batas pengenalan hari ini tercapai. Anda tetap bisa menambah tanaman secara manual."
    "PROVIDER_QUOTA" -> "Kuota Pl@ntNet sedang habis. Coba nanti atau tambah tanaman secara manual."
    "UNAUTHORIZED" -> "Akses pengenalan tidak berlaku. Hubungi pengelola aplikasi."
    "PROVIDER_AUTH" -> "Akses layanan Pl@ntNet perlu diperbarui oleh pengelola aplikasi."
    "NOT_CONFIGURED" -> "Layanan pengenalan belum dikonfigurasi. Anda tetap bisa menambah tanaman secara manual."
    "INVALID_IMAGE" -> "Foto tidak bisa dibaca. Pilih foto tanaman lain."
    "IMAGE_TOO_LARGE" -> "Ukuran foto terlalu besar untuk dikirim. Pilih foto lain."
    "NOT_RECOGNIZED" -> "Ambil ulang: foto seluruh tanaman dan daun yang jelas."
    "INVALID_RESPONSE" -> "Hasil layanan tidak bisa dibaca. Silakan coba lagi atau tambah manual."
    "DEVICE_STORAGE" -> "Penyimpanan perangkat belum siap. Silakan coba lagi atau tambah manual."
    else -> "Layanan pengenalan sedang tidak tersedia. Coba lagi nanti atau tambah manual."
}
