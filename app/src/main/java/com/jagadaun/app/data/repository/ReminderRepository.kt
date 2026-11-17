package com.jagadaun.app.data.repository

import com.jagadaun.app.data.local.CareEventDao
import com.jagadaun.app.data.local.CareEventEntity
import com.jagadaun.app.data.local.ReminderDao
import com.jagadaun.app.domain.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val careEventDao: CareEventDao,
) {
    fun observeReminders(): Flow<List<Reminder>> =
        reminderDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(reminder: Reminder) = reminderDao.upsert(reminder.toEntity())

    suspend fun update(reminder: Reminder) = reminderDao.upsert(reminder.toEntity())

    suspend fun delete(id: String) = reminderDao.deleteById(id)

    suspend fun deleteByPlant(plantId: String) = reminderDao.deleteByPlantId(plantId)

    /** Tandai selesai: geser jadwal berikutnya dan catat kejadian agar tidak ganda. */
    suspend fun complete(reminderId: String) {
        val entity = reminderDao.getById(reminderId) ?: return
        reminderDao.upsert(entity.copy(nextDue = entity.nextDue + entity.intervalDays * DAY_MS))
        careEventDao.insert(
            CareEventEntity(
                id = UUID.randomUUID().toString(),
                reminderId = reminderId,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
