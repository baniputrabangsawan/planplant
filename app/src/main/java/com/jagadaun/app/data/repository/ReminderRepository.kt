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

    suspend fun update(reminder: Reminder): Boolean = database.withTransaction {
        if (reminderDao.getById(reminder.id) == null) return@withTransaction false
        reminderDao.upsert(reminder.toEntity())
        true
    }

    /** Change only the pause state; never restore a stale scheduled occurrence. */
    suspend fun setActive(id: String, active: Boolean): Boolean = database.withTransaction {
        val current = reminderDao.getById(id) ?: return@withTransaction false
        reminderDao.upsert(current.copy(active = active))
        true
    }

    suspend fun delete(id: String) = reminderDao.deleteById(id)

    suspend fun deleteByPlant(plantId: String) = reminderDao.deleteByPlantId(plantId)

    /** Compare the displayed occurrence atomically so repeated taps cannot record it twice. */
    suspend fun complete(reminderId: String, expectedDue: Long): Boolean = database.withTransaction {
        val entity = reminderDao.getById(reminderId) ?: return@withTransaction false
        if (!entity.active || entity.nextDue != expectedDue) return@withTransaction false
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
        true
    }

}
