package com.jagadaun.app.ui.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Kebun Saya",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (plants.isEmpty()) {
                item {
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
                item {
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
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        "${matches.size} dari ${plants.size} tanaman",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                if (matches.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = "Tidak ada tanaman yang cocok.",
                            body = "Coba nama panggilan atau nama ilmiah lain.",
                            primaryText = "Hapus pencarian",
                            onPrimary = { query = "" },
                        )
                    }
                }
                items(matches, key = { it.id }) { plant ->
                    val next = reminders
                        .asSequence()
                        .filter { it.plantId == plant.id && it.active }
                        .minByOrNull { it.nextDue }
                    PlantCard(
                        plant = plant,
                        nextReminder = next,
                        onClick = { onOpenDetail(plant.id) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddManual,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Tambah manual") },
            containerColor = Primary,
            contentColor = Color.White,
        )
    }
}
