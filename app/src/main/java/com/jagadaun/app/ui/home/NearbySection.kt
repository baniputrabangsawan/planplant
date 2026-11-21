package com.jagadaun.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jagadaun.app.data.nearby.NearbyCache
import com.jagadaun.app.data.nearby.NearbyClient
import com.jagadaun.app.domain.NearbyPlants
import com.jagadaun.app.domain.NearbySpecies
import com.jagadaun.app.ui.theme.Divider
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.Surface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kartu seragam: lebar, tinggi, dan tinggi area teks dipatok agar semua kartu identik. */
private val CARD_WIDTH = 168.dp
private val CARD_PHOTO_HEIGHT = 126.dp
private val CARD_TEXT_HEIGHT = 76.dp
private val CARD_HEIGHT = CARD_PHOTO_HEIGHT + CARD_TEXT_HEIGHT

/**
 * Tanaman di sekitar langsung tampil di Beranda sebagai deret horizontal: foto, nama Indonesia,
 * dan nama ilmiah. Hasil terakhir dibaca dari cache tanpa menunggu lokasi dan tanpa menekan
 * tombol; pencarian baru hanya berjalan bila izin lokasi sudah diberikan.
 */
@Composable
fun NearbySection(
    onOpenNearby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cache = remember(context) {
        // Parser yang sama dengan jaringan: isi cache rusak menjadi miss, bukan tampil.
        NearbyCache(context, NearbyClient(context)::parseCached)
    }
    var latest by remember { mutableStateOf<NearbyPlants?>(null) }
    var loading by remember { mutableStateOf(true) }
    val permitted = remember(context) { hasCoarseLocation(context) }

    LaunchedEffect(permitted) {
        val cached = withContext(Dispatchers.IO) { cache.readLatest() }
        if (cached != null && cached.species.isNotEmpty()) {
            latest = cached
            loading = false
            return@LaunchedEffect
        }
        if (permitted) {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { NearbyClient(context).nearbyDefault(radiusKm = 10) }.getOrNull()
            }
            if (fresh != null && fresh.species.isNotEmpty()) {
                latest = fresh
                withContext(Dispatchers.IO) { cache.writeLatest(fresh) }
            }
        }
        loading = false
    }

    val species = latest?.species.orEmpty()
    if (species.isEmpty() && !loading) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tanaman di sekitar",
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            TextButton(onClick = onOpenNearby, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Peta & detail")
            }
        }
        if (species.isEmpty()) {
            Text("Mencari tanaman di sekitar…", color = TextSecondary)
        } else {
            Text(
                "${latest?.totalRecords ?: 0} catatan publik dalam radius ${latest?.radiusKm} km · " +
                    "catatan historis, bukan jumlah tanaman",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // Tinggi baris dipatok agar geser mendatar tidak mengubah posisi seksi lain.
                modifier = Modifier.height(CARD_HEIGHT),
            ) {
                items(species, key = { it.scientificName }) { item ->
                    NearbyMiniCard(item, onOpenNearby)
                }
            }
        }
    }
}

/**
 * Judul kartu SELALU nama Indonesia. Nama ilmiah hanya muncul di baris kedua, jadi tidak pernah
 * menggantikan nama Indonesia meski artikel Indonesia tidak tersedia.
 */
@Composable
private fun NearbyMiniCard(species: NearbySpecies, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.width(CARD_WIDTH).height(CARD_HEIGHT),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Divider),
    ) {
        val photo = species.photo?.thumbUrl
        if (photo != null) {
            AsyncImage(
                model = remember(photo) { ImageRequest.Builder(context).data(photo).crossfade(false).build() },
                contentDescription = "Foto ${species.commonName ?: species.scientificName}",
                contentScale = ContentScale.Crop,
                // Ukuran tetap, bukan fillMaxWidth: lebar LazyRow masih diukur saat item
                // pertama dipasang, dan fillMaxWidth dapat menghasilkan lebar nol.
                modifier = Modifier.width(CARD_WIDTH).height(CARD_PHOTO_HEIGHT),
            )
        } else {
            // Tanpa foto berlisensi: ubin daun, bukan kotak rusak atau gambar tanpa kredit.
            Box(
                Modifier.width(CARD_WIDTH).height(CARD_PHOTO_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Eco,
                    contentDescription = "Foto tidak tersedia",
                    tint = Primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().height(CARD_TEXT_HEIGHT)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                species.commonName ?: "Nama Indonesia belum tersedia",
                style = MaterialTheme.typography.titleMedium,
                color = if (species.commonName != null) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                species.scientificName,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun hasCoarseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
