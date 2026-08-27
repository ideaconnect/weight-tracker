package tech.idct.weighttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entry ORDER BY date ASC")
    fun observeAll(): Flow<List<EntryRow>>

    @Query("SELECT * FROM entry ORDER BY date ASC")
    suspend fun all(): List<EntryRow>

    @Query("SELECT * FROM entry WHERE date = :date")
    suspend fun byDate(date: Long): EntryRow?

    /** §13: logging twice on the same day replaces the value rather than appending. */
    @Upsert
    suspend fun upsert(row: EntryRow)

    @Upsert
    suspend fun upsertAll(rows: List<EntryRow>)

    @Query("DELETE FROM entry WHERE date = :date")
    suspend fun deleteByDate(date: Long)

    @Query("DELETE FROM entry")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM entry")
    fun observeCount(): Flow<Int>
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan WHERE id = 1")
    fun observe(): Flow<PlanRow?>

    @Query("SELECT * FROM plan WHERE id = 1")
    suspend fun get(): PlanRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: PlanRow)

    @Query("DELETE FROM plan")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsRow?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SettingsRow)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}

@Dao
interface EntitlementDao {
    @Query("SELECT * FROM entitlement WHERE id = 1")
    fun observe(): Flow<EntitlementRow?>

    @Query("SELECT * FROM entitlement WHERE id = 1")
    suspend fun get(): EntitlementRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: EntitlementRow)
}

@Dao
interface TombstoneDao {
    @Query("SELECT date FROM tombstone")
    suspend fun allDates(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: TombstoneRow)

    @Query("DELETE FROM tombstone WHERE date = :date")
    suspend fun clear(date: Long)

    @Query("DELETE FROM tombstone")
    suspend fun deleteAll()
}
