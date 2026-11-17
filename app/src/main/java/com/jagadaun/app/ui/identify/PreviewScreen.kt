package com.jagadaun.app.ui.identify

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jagadaun.app.domain.PlantCandidate
import com.jagadaun.app.rememberAppContainer
import com.jagadaun.app.ui.components.PlantPhoto
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.Surface as SurfaceColor
import com.jagadaun.app.ui.theme.TextSecondary
import com.jagadaun.app.ui.theme.Warning
import com.jagadaun.app.ui.theme.WarningContainer
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PreviewScreen(
    photoRef: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onReplacePhoto: () -> Unit,
    onSaveManual: () -> Unit,
    onCandidateSelected: (PlantCandidate) -> Unit,
) {
    val container = rememberAppContainer()
    val factory = remember(container) { IdentificationViewModel.factory(container.identificationClient) }
    val identification: IdentificationViewModel = viewModel(factory = factory)
    val observedState by identification.state.collectAsStateWithLifecycle()
    LaunchedEffect(photoRef) { identification.setPhoto(photoRef) }
    // Do not expose a prior photo's result during the frame before LaunchedEffect resets it.
    val state = observedState.takeIf { it.photoRef == photoRef } ?: IdentificationUiState(photoRef = photoRef)
    val processing = busy || state.loading
    val result = state.result
    val candidates = result?.candidates.orEmpty()
    val errorCode = state.errorCode
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val goBack = {
        identification.setPhoto(null)
        onBack()
    }
    BackHandler(onBack = goBack)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = if (result == null) "Pratinjau" else "Hasil pengenalan", onBack = goBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PlantPhoto(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                iconSize = 72.dp,
                photoRef = photoRef,
                contentDescription = "Foto tanaman yang dipilih",
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Foto dikirim melalui Cloudflare ke Pl@ntNet setelah Anda mengetuk Kenali Tanaman atau Coba lagi. Foto asli tetap di perangkat.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            if (state.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Sedang mengenali…")
                }
                Spacer(Modifier.height(16.dp))
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
                if (candidates.isEmpty()) {
                    Text(
                        "Tanaman belum bisa dikenali",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(
                        "Pilih kandidat",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Skor penyedia adalah urutan kecocokan, bukan probabilitas atau kepastian identitas tanaman.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (result.uncertain) {
                        Surface(color = WarningContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                "Belum bisa dipastikan. Periksa kandidat atau gunakan foto bagian tanaman yang lebih jelas.",
                                color = Warning,
                                modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.selectableGroup(),
                    ) {
                        candidates.forEach { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                selected = state.selectedCandidate == candidate,
                                enabled = !processing,
                                onClick = { identification.select(candidate) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (candidates.isNotEmpty()) {
                PrimaryButton(
                    text = "Gunakan kandidat",
                    onClick = { state.selectedCandidate?.let(onCandidateSelected) },
                    enabled = !processing && state.selectedCandidate != null,
                    icon = Icons.Filled.Add,
                )
            } else {
                PrimaryButton(
                    text = when {
                        state.loading -> "Sedang mengenali…"
                        busy -> "Menyiapkan foto…"
                        errorCode != null || result != null -> "Coba lagi"
                        else -> "Kenali Tanaman"
                    },
                    onClick = { photoRef?.let(identification::identify) },
                    enabled = photoRef != null && !processing,
                    icon = Icons.Filled.Eco,
                )
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Ganti Foto",
                onClick = onReplacePhoto,
                icon = Icons.Filled.Refresh,
                enabled = !processing,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Tambah manual",
                onClick = onSaveManual,
                icon = Icons.Filled.Add,
                enabled = !processing,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    try {
                        uriHandler.openUri("https://plantnet.org/")
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Tidak ada aplikasi untuk membuka tautan.", Toast.LENGTH_SHORT).show()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(context, "Tautan belum bisa dibuka.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Identifikasi oleh Pl@ntNet ↗")
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: PlantCandidate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val score = remember(candidate.score) {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).apply {
            maximumFractionDigits = 3
        }.format(candidate.score)
    }
    Surface(
        color = if (selected) SoftAccent else SurfaceColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(candidate.displayName, style = MaterialTheme.typography.titleLarge)
                if (candidate.displayName != candidate.scientificName) {
                    Text(candidate.scientificName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Text("Skor penyedia: $score", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
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
    "NOT_RECOGNIZED" -> "Tanaman belum bisa dikenali. Gunakan foto lebih jelas atau tambah manual."
    "INVALID_RESPONSE" -> "Hasil layanan tidak bisa dibaca. Silakan coba lagi atau tambah manual."
    "DEVICE_STORAGE" -> "Penyimpanan perangkat belum siap. Silakan coba lagi atau tambah manual."
    else -> "Layanan pengenalan sedang tidak tersedia. Coba lagi nanti atau tambah manual."
}
