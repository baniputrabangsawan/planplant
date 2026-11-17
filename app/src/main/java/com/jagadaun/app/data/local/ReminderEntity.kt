package com.jagadaun.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val plantId: String,
    val careType: String,
    val intervalDays: Int,
    val preferredTime: String,
    val zoneId: String,
    val active: Boolean,
    val nextDue: Long,
)
