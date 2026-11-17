package com.jagadaun.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val scientificName: String?,
    val notes: String,
    val photoRef: String?,
    val origin: String,
    val createdAt: Long,
)
