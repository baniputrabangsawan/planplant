package com.jagadaun.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jagadaun.app.JagaDaunApplication
import com.jagadaun.app.domain.Plant
import com.jagadaun.app.domain.Reminder
import com.jagadaun.app.ui.dayLabel
import com.jagadaun.app.ui.theme.PhotoPlaceholder
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.Surface
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PhotoImageState(val bitmap: ImageBitmap? = null, val loading: Boolean = true)

@Composable
fun PlantPhoto(
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
    photoRef: String? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { (context as JagaDaunApplication).container.photos }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PhotoPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        if (photoRef == null) {
            Icon(
                Icons.Filled.Eco,
                contentDescription = contentDescription,
                tint = Primary,
                modifier = Modifier.size(iconSize),
            )
        } else {
            val largeSurface = maxWidth > 128.dp || maxHeight > 128.dp
            val decodeSize = if (largeSurface) 1024 else 256
            key(store, photoRef, decodeSize) {
                val image by produceState(initialValue = PhotoImageState()) {
                    val bitmap = try {
                        withContext(Dispatchers.IO) {
                            store.readBitmap(photoRef, decodeSize)?.asImageBitmap()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    // Let GC release bitmaps after rendering; recycling here can race a frame.
                    value = PhotoImageState(bitmap = bitmap, loading = false)
                }
                val bitmap = image.bitmap
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    image.loading -> CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                    largeSurface -> Text(
                        "Foto tidak tersedia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> Icon(
                        Icons.Filled.Eco,
                        contentDescription = "Foto tidak tersedia",
                        tint = TextSecondary,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}

/** Kartu tanaman di Kebun Saya. */
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlantPhoto(Modifier.size(64.dp), photoRef = plant.photoRef)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plant.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                plant.scientificName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (nextReminder != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${nextReminder.careType} · ${nextReminder.nextDue.dayLabel()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
