package tech.idct.weighttracker.data.repo

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.idct.weighttracker.data.db.AppDatabase
import tech.idct.weighttracker.data.db.EntitlementRow
import tech.idct.weighttracker.data.db.EntryRow
import tech.idct.weighttracker.data.db.PlanRow
import tech.idct.weighttracker.data.db.SettingsRow
import tech.idct.weighttracker.data.db.TombstoneRow
import tech.idct.weighttracker.domain.AppSettings
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import java.time.LocalDate
import java.time.LocalTime

/**
 * The only door to stored data. The source of truth is always local (§2), so
 * nothing here waits on a network.
 */
class WeightRepository(private val db: AppDatabase) {

    // ---- entries -----------------------------------------------------------

    fun observeEntries(): Flow<List<WeightEntry>> =
        db.entries().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun entries(): List<WeightEntry> = db.entries().all().map { it.toDomain() }

    suspend fun entry(date: LocalDate): WeightEntry? = db.entries().byDate(date.toEpochDay())?.toDomain()

    fun observeEntryCount(): Flow<Int> = db.entries().observeCount()

    /**
     * §13: one entry per day — logging twice on the same day replaces the value.
     * Saving by hand also lifts any tombstone, since the user has clearly changed
     * their mind about that day.
     */
    suspend fun saveManualEntry(date: LocalDate, kg: Float) {
        val rounded = Units.roundKg(kg)
        db.tombstones().clear(date.toEpochDay())
        db.entries().upsert(
            EntryRow(
                date = date.toEpochDay(),
                kg = rounded,
                source = EntrySource.MANUAL,
                hcRecordId = null,
                recordedAt = System.currentTimeMillis(),
            )
        )
        repinPlanStartIfToday(date, rounded)
    }

    /**
     * §3 pins the plan's start "when the plan is created". The pin settles at the end
     * of that day: a weight logged on the start date IS the starting weight, so the
     * plan line follows it. From the next day on the start is frozen, and the schedule
     * is measured against it.
     */
    private suspend fun repinPlanStartIfToday(date: LocalDate, kg: Float) {
        val today = LocalDate.now()
        if (date != today) return
        val plan = db.plans().get() ?: return
        if (plan.startDate != today.toEpochDay()) return
        if (plan.startKg == kg) return
        db.plans().put(plan.copy(startKg = kg))
    }

    /**
     * §4 rule 4: editing a synced entry converts it to MANUAL, so later syncs stop
     * touching it.
     */
    suspend fun updateEntry(date: LocalDate, kg: Float) = saveManualEntry(date, kg)

    /**
     * §4 rule 5: deleting removes the entry locally and it must not be re-imported,
     * so the date keeps a tombstone.
     */
    suspend fun deleteEntry(date: LocalDate) {
        db.entries().deleteByDate(date.toEpochDay())
        db.tombstones().put(TombstoneRow(date.toEpochDay()))
    }

    /**
     * §4 rule 2, applied per day. The incoming list is expected to hold at most one
     * record per day already (§4 rule 3 picks the earliest of the day).
     */
    suspend fun mergeHealthConnectEntries(incoming: List<WeightEntry>): Int {
        if (incoming.isEmpty()) return 0
        val tombstoned = db.tombstones().allDates().toSet()
        val existing = db.entries().all().associateBy { it.date }
        val toWrite = mutableListOf<EntryRow>()

        for (record in incoming) {
            val key = record.date.toEpochDay()
            if (key in tombstoned) continue
            val local = existing[key]
            when {
                local == null ->
                    toWrite += record.copy(kg = Units.roundKg(record.kg)).toRow()

                local.source == EntrySource.MANUAL -> Unit // manual entries win

                // An existing synced entry is overwritten by the newer record.
                (record.recordedAt ?: 0L) > (local.recordedAt ?: 0L) ->
                    toWrite += record.copy(kg = Units.roundKg(record.kg)).toRow()
            }
        }
        if (toWrite.isNotEmpty()) db.entries().upsertAll(toWrite)
        return toWrite.size
    }

    // ---- plan --------------------------------------------------------------

    fun observePlan(): Flow<Plan?> = db.plans().observe().map { it?.toDomain() }

    suspend fun plan(): Plan? = db.plans().get()?.toDomain()

    suspend fun savePlan(plan: Plan) = db.plans().put(plan.toRow())

    // ---- settings ----------------------------------------------------------

    fun observeSettings(): Flow<AppSettings> =
        db.settings().observe().map { (it ?: SettingsRow()).toDomain() }

    suspend fun settings(): AppSettings = (db.settings().get() ?: SettingsRow()).toDomain()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = (db.settings().get() ?: SettingsRow()).toDomain()
        db.settings().put(transform(current).toRow())
    }

    // ---- entitlement -------------------------------------------------------

    /**
     * §3: a cached true stays true offline — the flow never consults the network,
     * and Play Billing only ever writes into it.
     */
    fun observeUnlocked(): Flow<Boolean> =
        db.entitlement().observe().map { it?.widgetsUnlocked == true }

    suspend fun isUnlocked(): Boolean = db.entitlement().get()?.widgetsUnlocked == true

    suspend fun setUnlocked(unlocked: Boolean) {
        db.entitlement().put(
            EntitlementRow(widgetsUnlocked = unlocked, verifiedAt = System.currentTimeMillis())
        )
    }

    // ---- backup ------------------------------------------------------------

    suspend fun tombstoneDates(): List<Long> = db.tombstones().allDates()

    /**
     * Restore replaces entries, tombstones and the plan wholesale — never merges.
     * Settings stay: they describe this device, not the data.
     */
    suspend fun replaceAllFromBackup(
        entries: List<WeightEntry>,
        tombstoneEpochDays: List<Long>,
        plan: Plan?,
    ) {
        db.entries().deleteAll()
        db.tombstones().deleteAll()
        db.plans().deleteAll()
        if (entries.isNotEmpty()) db.entries().upsertAll(entries.map { it.toRow() })
        tombstoneEpochDays.forEach { db.tombstones().put(TombstoneRow(it)) }
        plan?.let { db.plans().put(it.toRow()) }
    }

    // ---- destructive -------------------------------------------------------

    /**
     * §13: clears entries, plan and settings — but not the purchase entitlement.
     *
     * Whether onboarding has been seen is not really a setting; wiping it dropped the
     * user back into the first-run flow on the next launch, as if the app had been
     * reinstalled.
     */
    suspend fun deleteAllData() {
        val seenOnboarding = db.settings().get()?.onboardingComplete == true
        db.entries().deleteAll()
        db.plans().deleteAll()
        db.settings().deleteAll()
        db.tombstones().deleteAll()
        if (seenOnboarding) db.settings().put(SettingsRow(onboardingComplete = true))
    }

    companion object {
        @Volatile private var instance: WeightRepository? = null

        fun get(context: Context): WeightRepository = instance ?: synchronized(this) {
            instance ?: WeightRepository(AppDatabase.get(context)).also { instance = it }
        }
    }
}

// ---- mapping ---------------------------------------------------------------

private fun EntryRow.toDomain() = WeightEntry(
    date = LocalDate.ofEpochDay(date),
    kg = kg,
    source = source,
    hcRecordId = hcRecordId,
    recordedAt = recordedAt,
)

private fun WeightEntry.toRow() = EntryRow(
    date = date.toEpochDay(),
    kg = kg,
    source = source,
    hcRecordId = hcRecordId,
    recordedAt = recordedAt,
)

private fun PlanRow.toDomain() = Plan(
    startDate = LocalDate.ofEpochDay(startDate),
    startKg = startKg,
    targetKg = targetKg,
    mode = mode,
    targetDate = targetDate?.let { LocalDate.ofEpochDay(it) },
    ratePerWeek = ratePerWeek,
)

private fun Plan.toRow() = PlanRow(
    startDate = startDate.toEpochDay(),
    startKg = startKg,
    targetKg = targetKg,
    mode = mode,
    targetDate = targetDate?.toEpochDay(),
    ratePerWeek = ratePerWeek,
)

private fun SettingsRow.toDomain() = AppSettings(
    unit = unit,
    theme = theme,
    healthConnectEnabled = healthConnectEnabled,
    backgroundSyncEnabled = backgroundSyncEnabled,
    lastSyncAt = lastSyncAt,
    reminderEnabled = reminderEnabled,
    reminderTime = LocalTime.ofSecondOfDay(reminderMinuteOfDay * 60L),
    quickLogFromNotification = quickLogFromNotification,
    onboardingComplete = onboardingComplete,
    signedInEmail = signedInEmail,
    backupEnabled = backupEnabled,
    celebratedPlanKey = celebratedPlanKey,
)

private fun AppSettings.toRow() = SettingsRow(
    unit = unit,
    theme = theme,
    healthConnectEnabled = healthConnectEnabled,
    backgroundSyncEnabled = backgroundSyncEnabled,
    lastSyncAt = lastSyncAt,
    reminderEnabled = reminderEnabled,
    reminderMinuteOfDay = reminderTime.hour * 60 + reminderTime.minute,
    quickLogFromNotification = quickLogFromNotification,
    onboardingComplete = onboardingComplete,
    signedInEmail = signedInEmail,
    backupEnabled = backupEnabled,
    celebratedPlanKey = celebratedPlanKey,
)
