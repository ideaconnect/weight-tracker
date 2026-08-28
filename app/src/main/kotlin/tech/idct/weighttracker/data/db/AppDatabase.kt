package tech.idct.weighttracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntryRow::class, PlanRow::class, SettingsRow::class, EntitlementRow::class, TombstoneRow::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun plans(): PlanDao
    abstract fun settings(): SettingsDao
    abstract fun entitlement(): EntitlementDao
    abstract fun tombstones(): TombstoneDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2: cloud backup switch and the once-per-plan celebration marker. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN backupEnabled INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE settings ADD COLUMN celebratedPlanKey TEXT")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "weight-tracker.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
