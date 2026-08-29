package tech.idct.weighttracker.ui

import tech.idct.weighttracker.domain.AppSettings
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Section 12 Copy: warm but factual. Numbers do the encouraging; the app never
 * congratulates or scolds. Dates are ISO, the clock is 24-hour.
 */
object Format {

    val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.US)
    val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun greeting(now: LocalTime = LocalTime.now(), today: LocalDate = LocalDate.now()): String {
        val part = when (now.hour) {
            in 0..11 -> "morning"
            in 12..17 -> "afternoon"
            else -> "evening"
        }
        val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$day $part"
    }

    /** "−0.4 vs plan" when ahead, "+0.4 vs plan" when behind. */
    fun aheadChip(stats: PlanStats, unit: WeightUnit): String {
        val prefix = if (stats.aheadKg >= 0) "−" else "+"
        return prefix + Units.format(abs(stats.aheadKg), unit) + " vs plan"
    }

    fun aheadLine(stats: PlanStats, unit: WeightUnit): String = when {
        !stats.dated -> "No deadline set"
        // Day zero: the plan is taking today's weight as its baseline.
        !stats.scheduleStarted -> "Starts from today"
        stats.aheadKg >= 0 -> "${Units.formatWithUnit(stats.aheadKg, unit)} ahead"
        else -> "${Units.formatWithUnit(-stats.aheadKg, unit)} behind"
    }

    /**
     * Change over the last seven days as a plain weight delta, so a gain plan reads
     * "+0.4" when the number went up regardless of whether that is progress.
     */
    fun weekChange(stats: PlanStats, unit: WeightUnit): String {
        val change = stats.weekChangeKg ?: return "—"
        val delta = -change * stats.direction
        return Units.formatSigned(delta, unit)
    }

    fun weekPace(stats: PlanStats, unit: WeightUnit): String =
        if (stats.dated) "Plan asks ${Units.format(stats.planRatePerDay * 7f, unit, 2)} / week"
        else "Open-ended goal"

    fun planHeadline(stats: PlanStats, unit: WeightUnit): String =
        if (stats.dated) "${Units.formatWithUnit(stats.leftKg, unit)} to go"
        else "Target ${Units.formatWithUnit(stats.targetKg, unit)}"

    fun percent(stats: PlanStats): String = "${(stats.progress * 100).roundToInt()}%"

    fun rateNeeded(stats: PlanStats, unit: WeightUnit): String = when {
        !stats.dated -> "No daily rate — no date set"
        stats.targetDatePassed -> "Target date has passed"
        else -> "${Units.format(stats.neededPerDay, unit, 2)} ${unit.label} / day needed"
    }

    fun projection(stats: PlanStats): String =
        stats.projectedFinish?.let { "On pace: ${it.format(isoDate)}" } ?: ""

    /**
     * The energy version of [rateNeeded]: neededPerDay × 8400 kcal/kg, rounded to
     * the nearest 10 kcal in the domain. Null when there is no rate or nothing
     * left to lose, so callers hide the line rather than print "0 kcal".
     */
    fun kcalNeeded(stats: PlanStats): String? {
        if (!stats.hasKcal) return null
        val word = if (stats.direction > 0) "deficit" else "surplus"
        return "≈${stats.neededKcalRounded} kcal / day $word needed"
    }

    /** The widget-sized version: "−370 kcal / day" for a deficit, "+" for a surplus. */
    fun kcalCompact(stats: PlanStats): String? {
        if (!stats.hasKcal) return null
        val sign = if (stats.direction > 0) "−" else "+"
        return "$sign${stats.neededKcalRounded} kcal / day"
    }

    /**
     * Section 4: home shows the sync state as one line, with a manual "Sync now"
     * beside it.
     */
    fun syncLine(settings: AppSettings): String {
        if (!settings.healthConnectEnabled) return "Manual entries only"
        val at = settings.lastSyncAt ?: return "Health Connect · not synced yet"
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime().format(clock)
        return if (settings.backgroundSyncEnabled) {
            "Health Connect · background sync $time"
        } else {
            "Health Connect · synced on open, $time"
        }
    }

    /** The closing sentence on the Plan screen, comparing the user's pace to the plan's. */
    fun planNote(stats: PlanStats, unit: WeightUnit): String {
        if (!stats.dated) {
            return "Without a date there is no daily rate and no plan line — just your weight and the goal."
        }
        if (stats.targetDatePassed) {
            return "The target date has passed. Edit the plan to set a new one, or switch to no deadline."
        }
        if (stats.trendPerDay <= 0.001f) {
            return "There is not enough movement yet to project a finish date. The dashed line is what the plan asks for."
        }
        val ownPace = Units.format(stats.trendPerDay * 7f, unit, 2)
        val planPace = Units.format(stats.planRatePerDay * 7f, unit, 2)
        val comparison = when {
            stats.trendPerDay > stats.planRatePerDay * 1.05f -> "a little faster than the plan needs"
            stats.trendPerDay < stats.planRatePerDay * 0.95f -> "a little slower than the plan needs"
            else -> "almost exactly what the plan needs"
        }
        return "Your own pace is $ownPace ${unit.label} a week, $comparison. " +
            "The plan asks for $planPace ${unit.label}. The dotted line projects where your pace lands you."
    }
}
