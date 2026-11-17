package com.jagadaun.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun getById(id: String): PlantEntity?

    @Query("SELECT COUNT(*) FROM plants WHERE photoRef = :path")
    suspend fun photoReferences(path: String): Int

    @Query("DELETE FROM care_events WHERE reminderId IN (SELECT id FROM reminders WHERE plantId = :plantId)")
    suspend fun deleteCareEvents(plantId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun deleteById(id: String)
}
