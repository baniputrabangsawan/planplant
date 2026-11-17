package com.jagadaun.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlantEntity::class, ReminderEntity::class, CareEventEntity::class, HealthRecordEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class JagaDaunDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun reminderDao(): ReminderDao
    abstract fun careEventDao(): CareEventDao
    abstract fun healthRecordDao(): HealthRecordDao

    companion object {
        // Additive only: the existing garden, reminders, and care events are untouched.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_records` (
                        `id` TEXT NOT NULL,
                        `plantId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `candidateCode` TEXT NOT NULL,
                        `candidateLabel` TEXT NOT NULL,
                        `candidateScore` REAL NOT NULL,
                        `uncertain` INTEGER NOT NULL,
                        `photoRef1` TEXT NOT NULL,
                        `photoRef2` TEXT,
                        `photoRef3` TEXT,
                        `notes` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_records_plantId` ON `health_records` (`plantId`)")
            }
        }

        @Volatile
        private var instance: JagaDaunDatabase? = null

        fun getInstance(context: Context): JagaDaunDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JagaDaunDatabase::class.java,
                    "jagadaun.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
