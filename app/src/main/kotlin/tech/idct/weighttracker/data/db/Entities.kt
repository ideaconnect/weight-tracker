package tech.idct.weighttracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.ThemeChoice
import tech.idct.weighttracker.domain.WeightUnit

/** §3 entry — one row per calendar day, weights in kilograms with one decimal. */
@Entity(tableName = "entry")
data class EntryRow(
    @PrimaryKey val date: Long,
    val kg: Float,
    val source: EntrySource,
    val hcRecordId: String?,
    val recordedAt: Long?,
)

/** §3 plan — one plan at a time, so a fixed single row. */
@Entity(tableName = "plan")
data class PlanRow(
    @PrimaryKey val id: Int = 1,
    val startDate: Long,
    val startKg: Float,
    val targetKg: Float,
    val mode: PlanMode,
    val targetDate: Long?,
    val ratePerWeek: Float?,
)

/** §3 settings — a single row, kept in the same database as everything else. */
@Entity(tableName = "settings")
data class SettingsRow(
    @PrimaryKey val id: Int = 1,
    val unit: WeightUnit = WeightUnit.KG,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val healthConnectEnabled: Boolean = false,
    val backgroundSyncEnabled: Boolean = false,
    val lastSyncAt: Long? = null,
    val reminderEnabled: Boolean = false,
    /** Minutes past local midnight. */
    val reminderMinuteOfDay: Int = 8 * 60,
    val quickLogFromNotification: Boolean = true,
    val onboardingComplete: Boolean = false,
    val signedInEmail: String? = null,
    /** Uploads happen automatically while this is on and someone is signed in. */
    val backupEnabled: Boolean = false,
    /** The plan identity whose finish has been celebrated, so it happens once. */
    val celebratedPlanKey: String? = null,
)

/**
 * §3 entitlement — a single boolean, cached locally and re-verified against Play
 * Billing on launch. Its own table because §13 keeps it when all other data is
 * deleted.
 */
@Entity(tableName = "entitlement")
data class EntitlementRow(
    @PrimaryKey val id: Int = 1,
    val widgetsUnlocked: Boolean = false,
    val verifiedAt: Long? = null,
)

/**
 * §4 rule 5: a deleted entry must not be re-imported, so its date is remembered
 * even though the entry itself is gone.
 */
@Entity(tableName = "tombstone")
data class TombstoneRow(
    @PrimaryKey val date: Long,
)
