package com.jagadaun.app.data.photos

import android.content.Context
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.data.local.ReminderEntity
import com.jagadaun.app.data.repository.HealthRecordRepository
import com.jagadaun.app.data.repository.PlantRepository
import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthRecord
import com.jagadaun.app.domain.HealthTrackingStatus
import com.jagadaun.app.domain.Origin
import com.jagadaun.app.domain.Plant
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthHistoryPersistenceTest {
    private lateinit var context: Context
    private lateinit var photos: PhotoStore
    private lateinit var database: JagaDaunDatabase
    private lateinit var databaseName: String
    private val ownedPhotos = mutableListOf<String>()
    private val ownedDrafts = mutableListOf<String>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        photos = PhotoStore(context)
        databaseName = "health-history-test-${UUID.randomUUID()}.db"
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        ownedPhotos.forEach(photos::deleteSaved)
        ownedDrafts.forEach(photos::discardDraft)
    }

    @Test
    fun versionOneMigrationPreservesGardenRemindersAndCareEvents() = runBlocking {
        database.close()
        val photo = photos.persist(newDraft(Color.GREEN)).also { ownedPhotos += it }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL("CREATE TABLE IF NOT EXISTS plants (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, scientificName TEXT, notes TEXT NOT NULL, photoRef TEXT, origin TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE IF NOT EXISTS reminders (id TEXT NOT NULL PRIMARY KEY, plantId TEXT NOT NULL, careType TEXT NOT NULL, intervalDays INTEGER NOT NULL, preferredTime TEXT NOT NULL, zoneId TEXT NOT NULL, active INTEGER NOT NULL, nextDue INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE IF NOT EXISTS care_events (id TEXT NOT NULL PRIMARY KEY, reminderId TEXT NOT NULL, completedAt INTEGER NOT NULL)")
            legacy.execSQL("INSERT INTO plants VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf("legacy-plant", "Sirih lama", "Epipremnum aureum", "Catatan tetap", photo, "MANUAL", 1234L))
            legacy.execSQL("INSERT INTO reminders VALUES ('legacy-reminder', 'legacy-plant', 'Siram', 7, '07:00', 'Asia/Jakarta', 1, 5678)")
            legacy.execSQL("INSERT INTO care_events VALUES ('legacy-event', 'legacy-reminder', 4321)")
            legacy.version = 1
        }
        database = openDatabase()
        val plant = requireNotNull(database.plantDao().getById("legacy-plant"))
        assertEquals("Sirih lama", plant.displayName)
        assertEquals("Epipremnum aureum", plant.scientificName)
        assertEquals("Catatan tetap", plant.notes)
        assertEquals(photo, plant.photoRef)
        assertEquals("MANUAL", plant.origin)
        assertEquals(1234L, plant.createdAt)
        assertEquals(
            ReminderEntity("legacy-reminder", "legacy-plant", "Siram", 7, "07:00", "Asia/Jakarta", true, 5678),
            database.reminderDao().getById("legacy-reminder"),
        )
        database.openHelper.readableDatabase.query("SELECT id, reminderId, completedAt FROM care_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-event", cursor.getString(0))
            assertEquals("legacy-reminder", cursor.getString(1))
            assertEquals(4321L, cursor.getLong(2))
            assertFalse(cursor.moveToNext())
        }
        val history = HealthRecordRepository(database, photos)
        assertTrue(history.observe(null).first().isEmpty())
        val saved = history.save(record(listOf(photo), plant.id))
        assertEquals(saved, history.observe(plant.id).first().single())
        assertTrue(File(photo).isFile)
        assertEquals(2, database.openHelper.readableDatabase.version)
    }

    @Test
    fun savesThreePhotosOnceAndOnlyTrackingCanChangeAcrossReopen() = runBlocking {
        var history = HealthRecordRepository(database, photos)
        val draft = record(listOf(newDraft(Color.GREEN), newDraft(Color.YELLOW), newDraft(Color.RED)))
        val saved = history.save(draft).also { ownedPhotos += it.photoRefs }
        assertEquals(3, saved.photoRefs.size)
        assertTrue(saved.photoRefs.all { File(it).isFile })
        assertTrue(draft.photoRefs.none { File(it).exists() })
        val filesBeforeRepeat = savedFiles()
        assertEquals(saved, history.save(draft.copy(candidate = HealthCandidate("other", "Tidak mengganti hasil", 0.1))))
        assertEquals(1, history.observe(null).first().size)
        assertEquals(filesBeforeRepeat, savedFiles())
        history.updateTracking(saved.id, HealthTrackingStatus.RECOVERED, "Daun baru tampak baik menurut saya")
        database.close()
        database = openDatabase()
        history = HealthRecordRepository(database, photos)
        val reopened = history.observe(null).first().single()
        assertEquals(saved.copy(status = HealthTrackingStatus.RECOVERED, notes = "Daun baru tampak baik menurut saya"), reopened)
        assertEquals(reopened, history.save(draft))
        history.delete(saved.id)
        assertTrue(history.observe(null).first().isEmpty())
        assertTrue(saved.photoRefs.none { File(it).exists() })
    }

    @Test
    fun plantPhotoReplacementKeepsHistoryAndLastHistoryDeleteCleansSharedPhoto() = runBlocking {
        val plants = PlantRepository(database, photos)
        val history = HealthRecordRepository(database, photos)
        val initial = plants.save(plant(newDraft(Color.GREEN))).also { ownedPhotos += requireNotNull(it.photoRef) }
        val oldPhoto = requireNotNull(initial.photoRef)
        val attached = history.save(record(listOf(oldPhoto), initial.id))
        val unattached = history.save(record(listOf(oldPhoto)))
        assertEquals(listOf(attached), history.observe(initial.id).first())
        assertEquals(2, history.observe(null).first().size)
        val replacement = plants.save(initial.copy(photoRef = newDraft(Color.BLUE))).also { ownedPhotos += requireNotNull(it.photoRef) }
        assertTrue(File(oldPhoto).isFile)
        assertEquals(attached, history.observe(initial.id).first().single())
        history.delete(attached.id)
        assertTrue(File(oldPhoto).isFile)
        history.delete(unattached.id)
        assertFalse(File(oldPhoto).exists())
        assertTrue(File(requireNotNull(replacement.photoRef)).isFile)
        plants.delete(initial.id)
        assertFalse(File(requireNotNull(replacement.photoRef)).exists())
    }

    @Test
    fun deletingHistoryNeverRemovesTheCurrentPlantPhoto() = runBlocking {
        val plants = PlantRepository(database, photos)
        val history = HealthRecordRepository(database, photos)
        val savedPlant = plants.save(plant(newDraft(Color.GREEN))).also { ownedPhotos += requireNotNull(it.photoRef) }
        val photo = requireNotNull(savedPlant.photoRef)
        val assessment = history.save(record(listOf(photo), savedPlant.id))
        history.delete(assessment.id)
        assertTrue(File(photo).isFile)
        assertEquals(photo, plants.find(savedPlant.id)?.photoRef)
        plants.delete(savedPlant.id)
        assertFalse(File(photo).exists())
    }

    @Test
    fun deletingPlantRemovesItsHistoryAndOnlyUnreferencedPhotos() = runBlocking {
        val plants = PlantRepository(database, photos)
        val history = HealthRecordRepository(database, photos)
        val target = plants.save(plant(newDraft(Color.GREEN))).also { ownedPhotos += requireNotNull(it.photoRef) }
        val other = plants.save(plant(newDraft(Color.BLUE))).also { ownedPhotos += requireNotNull(it.photoRef) }
        val targetPhoto = requireNotNull(target.photoRef)
        val otherPhoto = requireNotNull(other.photoRef)
        val attached = history.save(record(listOf(targetPhoto, otherPhoto, newDraft(Color.RED)), target.id))
            .also { ownedPhotos += it.photoRefs }
        val unattached = history.save(record(listOf(targetPhoto)))
        plants.delete(target.id)
        assertNull(plants.find(target.id))
        assertTrue(history.observe(target.id).first().isEmpty())
        assertEquals(listOf(unattached), history.observe(null).first())
        assertTrue(File(targetPhoto).isFile)
        assertTrue(File(otherPhoto).isFile)
        assertFalse(File(attached.photoRefs[2]).exists())
        history.delete(unattached.id)
        assertFalse(File(targetPhoto).exists())
        assertTrue(File(otherPhoto).isFile)
        plants.delete(other.id)
        assertFalse(File(otherPhoto).exists())
    }

    @Test
    fun failedInsertPreservesEveryDraftAndRollsBackPromotions() = runBlocking {
        val history = HealthRecordRepository(database, photos)
        val draft = record(listOf(newDraft(Color.GREEN), newDraft(Color.RED), newDraft(Color.BLUE)))
        val filesBefore = savedFiles()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_health_test BEFORE INSERT ON health_records BEGIN SELECT RAISE(ABORT, 'test write failure'); END",
        )
        try {
            val unexpected = history.save(draft)
            ownedPhotos += unexpected.photoRefs
            fail("Injected insert failure must reject the assessment")
        } catch (_: SQLiteException) {
            assertTrue(draft.photoRefs.all { File(it).isFile })
            assertEquals(filesBefore, savedFiles())
            assertTrue(history.observe(null).first().isEmpty())
        }
    }

    @Test
    fun laterPromotionFailurePreservesDraftAndRemovesEarlierCopy() = runBlocking {
        val history = HealthRecordRepository(database, photos)
        val draft = newDraft(Color.GREEN)
        val missing = File(File(draft).parentFile, "missing-${UUID.randomUUID()}.jpg").absolutePath
        val filesBefore = savedFiles()
        try {
            val unexpected = history.save(record(listOf(draft, missing)))
            ownedPhotos += unexpected.photoRefs
            fail("A missing second draft must reject the assessment")
        } catch (_: IllegalArgumentException) {
            assertTrue(File(draft).isFile)
            assertEquals(filesBefore, savedFiles())
            assertTrue(history.observe(null).first().isEmpty())
        }
    }

    @Test
    fun absentPlantAndForeignPhotoAreRejectedWithoutConsumingDrafts() = runBlocking {
        val history = HealthRecordRepository(database, photos)
        val draft = newDraft(Color.GREEN)
        val filesBefore = savedFiles()
        for (invalid in listOf(record(listOf(draft), "missing-plant"), record(listOf(draft, "https://example.com/photo.jpg")))) {
            try {
                val unexpected = history.save(invalid)
                ownedPhotos += unexpected.photoRefs
                fail("Invalid local assessment must be rejected")
            } catch (_: IllegalArgumentException) {
                assertTrue(File(draft).isFile)
                assertEquals(filesBefore, savedFiles())
                assertTrue(history.observe(null).first().isEmpty())
            }
        }
    }

    private fun newDraft(color: Int): String {
        val capture = photos.newCameraFile()
        ownedDrafts += capture.absolutePath
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        capture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return photos.importPhoto(photos.cameraUri(capture)).also { ownedDrafts += it }
    }

    private fun record(refs: List<String>, plantId: String? = null) = HealthRecord(
        id = UUID.randomUUID().toString(),
        plantId = plantId,
        createdAt = 12345,
        candidate = HealthCandidate("leaf_spot", "Dugaan bercak daun", 0.42),
        uncertain = true,
        photoRefs = refs,
        notes = "Pengamatan pengguna, bukan diagnosis",
    )

    private fun plant(photo: String) = Plant(
        id = UUID.randomUUID().toString(), displayName = "Uji riwayat", photoRef = photo,
        origin = Origin.MANUAL, createdAt = 1000,
    )

    private fun savedFiles(): Set<String> =
        File(context.filesDir, "photos").listFiles()?.map { it.absolutePath }?.toSet().orEmpty()

    private fun openDatabase() = Room.databaseBuilder(context, JagaDaunDatabase::class.java, databaseName)
        .addMigrations(JagaDaunDatabase.MIGRATION_1_2)
        .build()
}
