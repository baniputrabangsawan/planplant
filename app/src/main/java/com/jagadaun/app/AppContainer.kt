package com.jagadaun.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.data.repository.PlantRepository
import com.jagadaun.app.data.repository.ReminderRepository
import com.jagadaun.app.data.repository.HealthRecordRepository
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.data.identification.IdentificationClient
import com.jagadaun.app.domain.Origin
import com.jagadaun.app.domain.Plant
import com.jagadaun.app.domain.Reminder
import com.jagadaun.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Application-scoped local repositories; photos are never uploaded here. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = JagaDaunDatabase.getInstance(context)
    val photos = PhotoStore(context)
    val identificationClient = IdentificationClient(context, photos)
    private val plantRepository = PlantRepository(database, photos)
    val healthRecords = HealthRecordRepository(database, photos)
    private val reminderRepository = ReminderRepository(database)
    private val reminderScheduler = ReminderScheduler(context)

    val plants: StateFlow<List<Plant>> =
        plantRepository.observePlants().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val reminders: StateFlow<List<Reminder>> =
        reminderRepository.observeReminders()
            .onEach { reminderScheduler.sync(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // --- Kebun Saya ---

    suspend fun savePlant(
        id: String?,
        displayName: String,
        scientificName: String?,
        notes: String,
        photoRef: String?,
        identified: Boolean = false,
    ): Plant {
        val existing = id?.let { requireNotNull(plantRepository.find(it)) { "Tanaman tidak ditemukan" } }
        val plant = Plant(
            id = existing?.id ?: UUID.randomUUID().toString(),
            displayName = displayName.trim(),
            scientificName = scientificName?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes.trim(),
            photoRef = photoRef,
            origin = existing?.origin ?: if (identified) Origin.IDENTIFIED else Origin.MANUAL,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        require(plant.displayName.isNotBlank())
        return plantRepository.save(plant)
    }

    suspend fun deletePlant(id: String) = plantRepository.delete(id)

    // --- Pengingat ---

    fun addReminder(plantId: String, careType: String, intervalDays: Int, preferredTime: String, nextDue: Long, zoneId: String) {
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            plantId = plantId,
            careType = careType,
            intervalDays = intervalDays,
            preferredTime = preferredTime,
            zoneId = zoneId,
            active = true,
            nextDue = nextDue,
        )
        scope.launch { reminderRepository.add(reminder) }
    }

    fun updateReminder(reminder: Reminder) {
        scope.launch { reminderRepository.update(reminder) }
    }

    fun deleteReminder(id: String) {
        scope.launch { reminderRepository.delete(id) }
    }

    fun refreshReminderNotifications() {
        scope.launch { reminderScheduler.sync(reminders.value, force = true) }
    }

    /** Tandai selesai: geser jadwal berikutnya sebesar interval. */
    fun completeReminder(id: String, expectedDue: Long) {
        scope.launch { reminderRepository.complete(id, expectedDue) }
    }

}


@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember { (context.applicationContext as JagaDaunApplication).container }
}
