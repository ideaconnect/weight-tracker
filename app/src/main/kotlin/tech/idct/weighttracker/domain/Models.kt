package tech.idct.weighttracker.domain

import java.time.LocalDate
import java.time.LocalTime

/** Display unit only — stored data is always kilograms (§3). */
enum class WeightUnit { KG, LB;
    val label: String get() = if (this == KG) "kg" else "lb"
}

enum class ThemeChoice { DARK, LIGHT, SYSTEM }

/** Where an entry came from. Manual always wins over sync (§4). */
enum class EntrySource { MANUAL, HEALTH_CONNECT }

/** §5: the target weight is always required; what varies is which of date or pace is fixed. */
enum class PlanMode { BY_DATE, AT_PACE, NO_DEADLINE }

data class WeightEntry(
    val date: LocalDate,
    val kg: Float,
    val source: EntrySource,
    val hcRecordId: String? = null,
    val recordedAt: Long? = null,
)

data class Plan(
    val startDate: LocalDate,
    val startKg: Float,
    val targetKg: Float,
    val mode: PlanMode,
    val targetDate: LocalDate?,
    val ratePerWeek: Float?,
)

data class AppSettings(
    val unit: WeightUnit = WeightUnit.KG,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val healthConnectEnabled: Boolean = false,
    val backgroundSyncEnabled: Boolean = false,
    val lastSyncAt: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(8, 0),
    val quickLogFromNotification: Boolean = true,
    val onboardingComplete: Boolean = false,
    val signedInEmail: String? = null,
)
