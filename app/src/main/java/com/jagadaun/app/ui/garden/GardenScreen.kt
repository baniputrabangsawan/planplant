package com.jagadaun.app.ui.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.EmptyState
import com.jagadaun.app.ui.components.PlantCard
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun GardenScreen(
    container: AppContainer,
    onOpenDetail: (String) -> Unit,
    onAddManual: () -> Unit,
    onIdentify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plants by container.plants.collectAsStateWithLifecycle()
    val reminders by container.reminders.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(plants, query) {
        val term = query.trim()
        if (term.isEmpty()) plants else plants.filter {
            it.displayName.contains(term, ignoreCase = true) ||
                it.scientificName?.contains(term, ignoreCase = true) == true
        }
    }
    val gridState = rememberLazyGridState()
    val minimumCardWidth = 148.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val recentState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val recent = remember(plants) {
        if (plants.size > 1) plants.take(4) else emptyList()
    }
    val nextReminderByPlant = remember(reminders) {
        reminders.filter { it.active }.groupBy { it.plantId }
            .mapValues { (_, schedules) -> schedules.minByOrNull { it.nextDue } }
    }
    val showRecent = query.isBlank() && recent.isNotEmpty()
    // Title, search, recent group, collection heading.
    val collectionIndex = if (showRecent) 3 else 2

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minimumCardWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Kebun Saya",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (plants.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Filled.Eco,
                        title = "Belum ada tanaman tersimpan.",
                        primaryText = "Kenali Tanaman",
                        onPrimary = onIdentify,
                        secondaryText = "Tambah manual",
                        onSecondary = onAddManual,
                    )
                }
            } else {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Cari nama tanaman") },
                        supportingText = { Text("Nama panggilan atau nama ilmiah") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian")
                                }
                            }
                        },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showRecent) {
                    item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Terbaru ditambahkan",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                modifier = Modifier.semantics { heading() },
                            )
                            TextButton(
                                onClick = { scope.launch { gridState.animateScrollToItem(collectionIndex) } },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Lihat Semua") }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val cardWidth = maxOf(160.dp, maxWidth * 0.78f)
                                LazyRow(
                                    state = recentState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(recent, key = { it.id }) { plant ->
                                        PlantCard(
                                            plant = plant,
                                            nextReminder = nextReminderByPlant[plant.id],
                                            onClick = { onOpenDetail(plant.id) },
                                            modifier = Modifier.width(cardWidth),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "collection", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Semua tanaman",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            "${matches.size} dari ${plants.size} tanaman",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
                if (matches.isEmpty()) {
                    item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = "Tidak ada tanaman yang cocok.",
                            body = "Coba nama panggilan atau nama ilmiah lain.",
                            primaryText = "Hapus pencarian",
                            onPrimary = { query = "" },
                        )
                    }
                }
                gridItems(matches, key = { "plant-${it.id}" }) { plant ->
                    PlantCard(
                        plant = plant,
                        nextReminder = nextReminderByPlant[plant.id],
                        onClick = { onOpenDetail(plant.id) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddManual,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                // Teks anak FAB tidak terpublikasi ke pohon aksesibilitas; beri nama eksplisit.
                .semantics { contentDescription = "Tambah tanaman manual" },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Tambah manual") },
            containerColor = Primary,
            contentColor = Color.White,
        )
    }
}
