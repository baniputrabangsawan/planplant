package com.jagadaun.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jagadaun.app.domain.PlantKnowledgeSource
import com.jagadaun.app.ui.health.HealthSourceLink
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.TextPrimary
import com.jagadaun.app.ui.theme.TextSecondary

@Composable
fun PlantKnowledgeSection(scientificName: String?) {
    val knowledge = remember(scientificName) { PlantKnowledgeSource.forScientificName(scientificName) } ?: return
    var expanded by rememberSaveable(scientificName) { mutableStateOf(false) }
    Surface(color = SoftAccent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Tentang ${knowledge.name}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(knowledge.description, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.semantics { stateDescription = if (expanded) "Terbuka" else "Tertutup" },
            ) {
                Text(if (expanded) "Tutup jenis & ciri buah" else "Jenis & ciri buah (${knowledge.varieties.size})")
            }
            if (expanded) {
                Text(knowledge.observation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                knowledge.varieties.forEach { variety ->
                    Text(variety.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(variety.traits, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
                Text("Sumber bacaan", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                knowledge.sources.forEach { source -> HealthSourceLink(source.title, source.url) }
            }
        }
    }
}
