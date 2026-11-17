package com.jagadaun.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM health_records WHERE :plantId IS NULL OR plantId = :plantId ORDER BY createdAt DESC, id ASC")
    fun observe(plantId: String?): Flow<List<HealthRecordEntity>>

    @Query("SELECT * FROM health_records WHERE id = :id")
    suspend fun getById(id: String): HealthRecordEntity?

    @Query("SELECT * FROM health_records WHERE plantId = :plantId")
    suspend fun getByPlantId(plantId: String): List<HealthRecordEntity>

    @Query("SELECT COUNT(*) FROM health_records WHERE photoRef1 = :path OR photoRef2 = :path OR photoRef3 = :path")
    suspend fun photoReferences(path: String): Int

    @Insert
    suspend fun insert(record: HealthRecordEntity)

    @Query("UPDATE health_records SET status = :status, notes = :notes WHERE id = :id")
    suspend fun updateTracking(id: String, status: String, notes: String)

    @Query("DELETE FROM health_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM health_records WHERE plantId = :plantId")
    suspend fun deleteByPlantId(plantId: String)
}
