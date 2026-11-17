package com.jagadaun.app.ui.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jagadaun.app.AppContainer
import com.jagadaun.app.ui.components.EmptyState
import com.jagadaun.app.ui.components.PlantCard
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.TextPrimary

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

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Kebun Saya", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            if (plants.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Eco,
                    title = "Belum ada tanaman tersimpan.",
                    primaryText = "Kenali Tanaman",
                    onPrimary = onIdentify,
                    secondaryText = "Tambah manual",
                    onSecondary = onAddManual,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(plants, key = { it.id }) { plant ->
                        val next = reminders
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
