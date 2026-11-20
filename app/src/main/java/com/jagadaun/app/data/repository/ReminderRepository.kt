package com.jagadaun.app.data.repository

import androidx.room.withTransaction
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.domain.nextReminderDue
import com.jagadaun.app.data.local.CareEventEntity
import com.jagadaun.app.domain.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ReminderRepository(private val database: JagaDaunDatabase) {
    private val reminderDao = database.reminderDao()
    private val careEventDao = database.careEventDao()
    fun observeReminders(): Flow<List<Reminder>> =
        reminderDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(reminder: Reminder) = reminderDao.upsert(reminder.toEntity())

    suspend fun update(reminder: Reminder) = reminderDao.upsert(reminder.toEntity())

    suspend fun delete(id: String) = reminderDao.deleteById(id)

    suspend fun deleteByPlant(plantId: String) = reminderDao.deleteByPlantId(plantId)

    /** Compare the displayed occurrence atomically so repeated taps cannot record it twice. */
    suspend fun complete(reminderId: String, expectedDue: Long) = database.withTransaction {
        val entity = reminderDao.getById(reminderId) ?: return@withTransaction
        if (!entity.active || entity.nextDue != expectedDue) return@withTransaction
        reminderDao.upsert(entity.copy(nextDue = nextReminderDue(
            entity.intervalDays, entity.preferredTime, entity.zoneId, maxOf(System.currentTimeMillis(), entity.nextDue),
        )))
        careEventDao.insert(
            CareEventEntity(
                id = UUID.randomUUID().toString(),
                reminderId = reminderId,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

}
