package tech.idct.weighttracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [EntryRow::class, PlanRow::class, SettingsRow::class, EntitlementRow::class, TombstoneRow::class],
    version = 1,
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "weight-tracker.db",
            ).build().also { instance = it }
        }
    }
}
