package com.jagadaun.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ReminderScheduleTest {
    @Test fun respectsChosenClockTimeRatherThanCurrentMinute() {
        assertEquals(
            Instant.parse("2026-11-18T22:00:00Z").toEpochMilli(),
            nextReminderDue(2, "07:00", "Asia/Jayapura", Instant.parse("2026-11-17T05:43:00Z").toEpochMilli()),
        )
    }

    @Test fun calendarDaysPreserveClockAcrossDaylightSaving() {
        assertEquals(
            Instant.parse("2026-03-08T11:00:00Z").toEpochMilli(),
            nextReminderDue(1, "07:00", "America/New_York", Instant.parse("2026-03-07T12:00:00Z").toEpochMilli()),
        )
    }

    @Test fun lateCompletionSchedulesInFutureNotAtAnOldOverdueSlot() {
        val now = Instant.parse("2026-11-17T15:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-11-19T00:00:00Z").toEpochMilli(), nextReminderDue(2, "07:00", "Asia/Jakarta", now))
    }

    @Test fun rejectsUnusableClockValuesAndIntervals() {
        for (time in listOf("25:00", "07:60")) {
            assertThrows(java.time.format.DateTimeParseException::class.java) { nextReminderDue(1, time, "Asia/Jakarta", 0) }
        }
        assertThrows(IllegalArgumentException::class.java) { nextReminderDue(1, "7:00", "Asia/Jakarta", 0) }
        assertThrows(IllegalArgumentException::class.java) { nextReminderDue(0, "07:00", "Asia/Jakarta", 0) }
    }
}
