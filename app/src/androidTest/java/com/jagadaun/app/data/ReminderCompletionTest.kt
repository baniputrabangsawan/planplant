package com.jagadaun.app.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.data.local.ReminderEntity
import com.jagadaun.app.data.repository.ReminderRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderCompletionTest {
    @Test fun doubleCompletionAdvancesOnceAndRecordsOneEvent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, JagaDaunDatabase::class.java).build()
        try {
            val due = System.currentTimeMillis() - 40L * 86_400_000
            database.reminderDao().upsert(ReminderEntity("test-reminder", "test-plant", "Siram", 7, "07:00", "Asia/Jakarta", true, due))
            val repository = ReminderRepository(database)
            repository.complete("test-reminder", due)
            val advanced = database.reminderDao().getById("test-reminder")!!.nextDue
            repository.complete("test-reminder", due)
            assertTrue(advanced > System.currentTimeMillis())
            assertEquals(advanced, database.reminderDao().getById("test-reminder")!!.nextDue)
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM care_events").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    @Test fun earlyCompletionAdvancesBeyondAlreadyScheduledOccurrence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, JagaDaunDatabase::class.java).build()
        try {
            val due = com.jagadaun.app.domain.nextReminderDue(7, "07:00", "Asia/Jakarta", System.currentTimeMillis())
            database.reminderDao().upsert(ReminderEntity("test-reminder", "test-plant", "Siram", 7, "07:00", "Asia/Jakarta", true, due))
            val repository = ReminderRepository(database)
            repository.complete("test-reminder", due)
            val advanced = database.reminderDao().getById("test-reminder")!!.nextDue
            assertTrue(advanced > due)
            repository.complete("test-reminder", due)
            assertEquals(advanced, database.reminderDao().getById("test-reminder")!!.nextDue)
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM care_events").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    @Test fun pausedReminderCannotBeCompletedByAnOutdatedScreen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, JagaDaunDatabase::class.java).build()
        try {
            database.reminderDao().upsert(ReminderEntity("test-reminder", "test-plant", "Siram", 7, "07:00", "Asia/Jakarta", false, 1000))
            ReminderRepository(database).complete("test-reminder", 1000)
            assertEquals(1000L, database.reminderDao().getById("test-reminder")!!.nextDue)
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM care_events").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        } finally {
            database.close()
        }
    }
}
