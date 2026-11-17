package com.jagadaun.app.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagadaun.app.data.local.JagaDaunDatabase
import com.jagadaun.app.data.local.ReminderEntity
import com.jagadaun.app.data.repository.PlantRepository
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
class PhotoPersistenceTest {
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
        databaseName = "photo-test-${UUID.randomUUID()}.db"
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
    fun importedPhotoHonorsOrientationAndRemovesLocation() {
        val capture = photos.newCameraFile()
        ownedDrafts += capture.absolutePath
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        capture.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        bitmap.recycle()
        ExifInterface(capture.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "6/1,12/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            saveAttributes()
        }
        val draft = photos.importPhoto(photos.cameraUri(capture))
        ownedDrafts += draft
        val decoded = requireNotNull(photos.readBitmap(draft, 2048))
        assertEquals(800, decoded.width)
        assertEquals(1200, decoded.height)
        decoded.recycle()
        val exif = ExifInterface(draft)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        val thumbnail = requireNotNull(photos.readBitmap(draft, 128))
        assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 128)
        thumbnail.recycle()
    }

    @Test
    fun photoSurvivesDatabaseReopenAndReplacementCleansOldFile() = runBlocking {
        var repository = PlantRepository(database, photos)
        val initial = repository.save(plant(newDraft(Color.GREEN)))
        ownedPhotos += requireNotNull(initial.photoRef)
        assertTrue(File(initial.photoRef!!).isFile)
        database.close()
        database = openDatabase()
        repository = PlantRepository(database, photos)
        assertEquals(initial.photoRef, repository.find(initial.id)?.photoRef)
        val replacement = repository.save(initial.copy(photoRef = newDraft(Color.BLUE)))
        ownedPhotos += requireNotNull(replacement.photoRef)
        assertFalse(File(initial.photoRef!!).exists())
        assertTrue(File(replacement.photoRef!!).isFile)
        database.reminderDao().upsert(ReminderEntity("photo-test-reminder", initial.id, "Siram", 7, "07:00", "Asia/Jakarta", true, 1000))
        repository.delete(initial.id)
        assertNull(repository.find(initial.id))
        assertTrue(database.reminderDao().observeAll().first().isEmpty())
        assertFalse(File(replacement.photoRef!!).exists())
    }

    @Test
    fun failedDatabaseSaveKeepsDraftAndRemovesPromotedCopy() = runBlocking {
        val repository = PlantRepository(database, photos)
        val draft = newDraft(Color.RED)
        val filesBefore = File(context.filesDir, "photos").listFiles()?.map { it.name }?.toSet().orEmpty()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_photo_test BEFORE INSERT ON plants BEGIN SELECT RAISE(ABORT, 'test write failure'); END",
        )
        try {
            val unexpected = repository.save(plant(draft))
            ownedPhotos += requireNotNull(unexpected.photoRef)
            fail("Injected database failure must reject save")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertTrue(File(draft).isFile)
            val filesAfter = File(context.filesDir, "photos").listFiles()?.map { it.name }?.toSet().orEmpty()
            assertEquals(filesBefore, filesAfter)
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

    private fun plant(draft: String) = Plant(
        id = UUID.randomUUID().toString(), displayName = "Uji foto", photoRef = draft,
        origin = Origin.MANUAL, createdAt = 1000,
    )

    private fun openDatabase() = Room.databaseBuilder(context, JagaDaunDatabase::class.java, databaseName).build()
}
