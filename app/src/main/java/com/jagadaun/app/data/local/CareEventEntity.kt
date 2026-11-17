package com.jagadaun.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Jejak selesainya kegiatan; identitas kejadian mencegah penggandaan. */
@Entity(tableName = "care_events")
data class CareEventEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val completedAt: Long,
)
