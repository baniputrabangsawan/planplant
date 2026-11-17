package com.jagadaun.app.data.local

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface CareEventDao {
    @Insert
    suspend fun insert(event: CareEventEntity)
}
