package com.jagadaun.app.data.repository

import androidx.room.withTransaction
import com.jagadaun.app.data.local.HealthRecordEntity
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.data.photos.PhotoStore
import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthRecord
import com.jagadaun.app.domain.HealthTrackingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HealthRecordRepository(
    private val database: JagaDaunDatabase,
    private val photos: PhotoStore,
) {
    private val records = database.healthRecordDao()

    /** Null observes all assessments; a plant id observes only that plant. */
    fun observe(plantId: String?): Flow<List<HealthRecord>> =
        records.observe(plantId).map { list -> list.map { it.toDomain() } }

    /** An existing id returns its original snapshot; only tracking fields can be edited. */
    suspend fun save(record: HealthRecord): HealthRecord = withContext(Dispatchers.IO + NonCancellable) {
        val promoted = mutableMapOf<String, String>()
        val saved = try {
            database.withTransaction {
                val existing = records.getById(record.id)
                if (existing != null) return@withTransaction existing.toDomain()
                require(record.plantId == null || database.plantDao().getById(record.plantId) != null) {
                    "Tanaman untuk catatan ini sudah tidak ada."
                }
                val refs = record.photoRefs.map { path ->
                    if (photos.isDraft(path)) {
                        promoted.getOrPut(path) { photos.persist(path) }
                    } else {
                        // Only app-owned saved photos already in the local database may be reused.
                        require(database.plantDao().photoReferences(path) > 0 || records.photoReferences(path) > 0) {
                            "Foto lokal untuk catatan ini tidak ditemukan."
                        }
                        path
                    }
                }
                record.copy(photoRefs = refs).also { records.insert(it.toEntity()) }
            }
        } catch (error: Exception) {
            // Promotion never consumes drafts, even when a later photo or the insert fails.
            promoted.values.forEach { path ->
                try {
                    photos.deleteSaved(path)
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
        promoted.keys.forEach(photos::discardDraft)
        saved
    }

    suspend fun updateTracking(id: String, status: HealthTrackingStatus, notes: String) =
        withContext(Dispatchers.IO + NonCancellable) {
            records.updateTracking(id, status.name, notes)
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        val refs = database.withTransaction {
            val existing = records.getById(id)
            records.deleteById(id)
            existing?.photoRefs().orEmpty()
        }
        deleteUnreferencedPhotos(database, photos, refs)
    }
}

/** Check both owners while holding Room's transaction lock; never remove a shared photo. */
internal suspend fun deleteUnreferencedPhotos(
    database: JagaDaunDatabase,
    photos: PhotoStore,
    paths: List<String>,
) {
    if (paths.isEmpty()) return
    database.withTransaction {
        for (path in paths.distinct()) {
            if (database.plantDao().photoReferences(path) == 0 &&
                database.healthRecordDao().photoReferences(path) == 0
            ) {
                photos.deleteSaved(path)
            }
        }
    }
}

private fun HealthRecordEntity.toDomain() = HealthRecord(
    id = id,
    plantId = plantId,
    createdAt = createdAt,
    candidate = HealthCandidate(candidateCode, candidateLabel, candidateScore),
    uncertain = uncertain,
    photoRefs = photoRefs(),
    notes = notes,
    status = HealthTrackingStatus.valueOf(status),
)

private fun HealthRecord.toEntity() = HealthRecordEntity(
    id = id,
    plantId = plantId,
    createdAt = createdAt,
    candidateCode = candidate.code,
    candidateLabel = candidate.label,
    candidateScore = candidate.score,
    uncertain = uncertain,
    photoRef1 = photoRefs[0],
    photoRef2 = photoRefs.getOrNull(1),
    photoRef3 = photoRefs.getOrNull(2),
    notes = notes,
    status = status.name,
)
