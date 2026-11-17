package com.jagadaun.app.data.repository

import androidx.room.withTransaction
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.domain.Plant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlantRepository(
    private val database: JagaDaunDatabase,
    private val photos: PhotoStore,
) {
    private val plantDao = database.plantDao()

    fun observePlants(): Flow<List<Plant>> =
        plantDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun find(id: String): Plant? = plantDao.getById(id)?.toDomain()

    // A photo is promoted before the transaction, but the draft survives until commit.
    // Finish this short disk transaction even if the screen is being recreated.
    suspend fun save(plant: Plant): Plant = withContext(Dispatchers.IO + NonCancellable) {
        val draft = plant.photoRef?.takeIf(photos::isDraft)
        val promoted = draft?.let(photos::persist)
        val saved = plant.copy(photoRef = promoted ?: plant.photoRef)
        var previousPhoto: String? = null
        try {
            database.withTransaction {
                previousPhoto = plantDao.getById(plant.id)?.photoRef
                plantDao.upsert(saved.toEntity())
            }
        } catch (error: Exception) {
            photos.deleteSaved(promoted)
            throw error
        }
        photos.discardDraft(draft)
        deleteUnreferencedPhotos(database, photos, listOfNotNull(previousPhoto?.takeIf { it != saved.photoRef }))
        saved
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        val photoRefs = mutableListOf<String>()
        database.withTransaction {
            plantDao.getById(id)?.photoRef?.let(photoRefs::add)
            val healthRecords = database.healthRecordDao()
            healthRecords.getByPlantId(id).forEach { photoRefs.addAll(it.photoRefs()) }
            healthRecords.deleteByPlantId(id)
            plantDao.deleteCareEvents(id)
            database.reminderDao().deleteByPlantId(id)
            plantDao.deleteById(id)
        }
        deleteUnreferencedPhotos(database, photos, photoRefs)
    }
}
