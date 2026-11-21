package com.jagadaun.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jagadaun.app.data.photos.PlantPhotoSource
import com.jagadaun.app.domain.Plant
import com.jagadaun.app.domain.Reminder
import com.jagadaun.app.ui.dayLabel
import com.jagadaun.app.ui.theme.Divider
import com.jagadaun.app.ui.theme.PhotoPlaceholder
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.Surface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary

/** Coil berbagi cache memori antarhalaman; dekode tetap lewat PhotoStore di dispatcher IO. */
@Composable
fun PlantPhoto(
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
    photoRef: String? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var failed by remember(photoRef) { mutableStateOf(false) }
    val request = remember(context, photoRef) {
        photoRef?.let {
            ImageRequest.Builder(context).data(PlantPhotoSource(it)).crossfade(false).build()
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(PhotoPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Eco,
            contentDescription = if (photoRef == null) contentDescription else null,
            tint = Primary,
            modifier = Modifier.size(iconSize),
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = if (failed) "Foto tidak tersedia" else contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
                onSuccess = { failed = false },
            )
        }
    }
}

/** Kartu berlabel: foto di atas, nama/perawatan tetap terbaca di luar foto. */
@Composable
fun PlantCard(
    plant: Plant,
    nextReminder: Reminder?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Divider),
    ) {
        PlantPhoto(
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            photoRef = plant.photoRef,
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(plant.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            plant.scientificName?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Text(
                nextReminder?.let { "${it.careType} · ${it.nextDue.dayLabel()}" }
                    ?: "Belum ada jadwal perawatan",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}
