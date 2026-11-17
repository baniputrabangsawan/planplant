package com.jagadaun.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_records",
    foreignKeys = [ForeignKey(
        entity = PlantEntity::class,
        parentColumns = ["id"],
        childColumns = ["plantId"],
    )],
    indices = [Index("plantId")],
)
data class HealthRecordEntity(
    @PrimaryKey val id: String,
    val plantId: String?,
    val createdAt: Long,
    val candidateCode: String,
    val candidateLabel: String,
    val candidateScore: Double,
    val uncertain: Boolean,
    val photoRef1: String,
    val photoRef2: String?,
    val photoRef3: String?,
    val notes: String,
    val status: String,
) {
    fun photoRefs(): List<String> = listOfNotNull(photoRef1, photoRef2, photoRef3)
}
