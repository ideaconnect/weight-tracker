package tech.idct.weighttracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Everything derived from a plan and a history, in one place, so the home screen,
 * the plan screen, the widgets and the reminder body all agree.
 *
 * Signs follow §13: a gain plan (target above start) uses the same maths with the
 * sign reversed, and "ahead" still means the actual line is on the good side of
 * the plan line.
 */
data class PlanStats(
    val plan: Plan,
    /** +1 for a loss plan, -1 for a gain plan. */
    val direction: Float,
    /** false only in NO_DEADLINE — no daily rate, no sloped plan line, no ahead/behind. */
    val dated: Boolean,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    /** Days from plan start to target date; 0 when there is no date. */
    val spanDays: Int,
    val daysSinceStart: Int,
    val currentKg: Float,
    val lastEntryDate: LocalDate?,
    val planKgToday: Float,
    /** Positive means on the good side of the plan line. */
    val aheadKg: Float,
    val behind: Boolean,
    val lostKg: Float,
    val leftKg: Float,
    val planRatePerDay: Float,
    val neededPerDay: Float,
    val neededPerWeek: Float,
    val trendPerDay: Float,
    val projectedFinish: LocalDate?,
    val progress: Float,
    /** Change over the last seven days, in the plan's own sign convention. */
    val weekChangeKg: Float?,
    /** §13: target date in the past suppresses the daily rate and invites an edit. */
    val targetDatePassed: Boolean,
    /** The logged weight has crossed the target, in the plan's own direction. */
    val reached: Boolean,
    /**
     * Whether the schedule has actually run. False on the day the plan is created:
     * the start weight is that day's weight, so there is nothing yet to be ahead or
     * behind of.
     */
    val scheduleStarted: Boolean,
) {
    val targetKg: Float get() = plan.targetKg
    val startKg: Float get() = plan.startKg
    /** Whether a daily rate is meaningful at all. */
    val hasRate: Boolean get() = dated && !targetDatePassed
}

object PlanMath {

    /** §5: the tolerance band is the plan line ±0.6 kg. */
    const val TOLERANCE_KG = 0.6f

    /** §5: behind the plan line by more than 0.05 kg turns everything amber. */
    const val BEHIND_THRESHOLD_KG = 0.05f

    fun dayIndex(startDate: LocalDate, date: LocalDate): Int =
        ChronoUnit.DAYS.between(startDate, date).toInt()

    /** Days from plan start to target date, or 0 when the plan has no deadline. */
    fun spanDays(plan: Plan): Int = when (plan.mode) {
        PlanMode.NO_DEADLINE -> 0
        PlanMode.BY_DATE -> plan.targetDate
            ?.let { max(1, dayIndex(plan.startDate, it)) } ?: 0
        PlanMode.AT_PACE -> {
            val perDay = (plan.ratePerWeek ?: 0f) / 7f
            if (perDay <= 0f) 0 else max(1, (abs(plan.startKg - plan.targetKg) / perDay).roundToInt())
        }
    }

    /** The date the plan aims at: given in BY_DATE, derived in AT_PACE, absent otherwise. */
    fun targetDate(plan: Plan): LocalDate? = when (plan.mode) {
        PlanMode.NO_DEADLINE -> null
        PlanMode.BY_DATE -> plan.targetDate
        PlanMode.AT_PACE -> spanDays(plan).takeIf { it > 0 }?.let { plan.startDate.plusDays(it.toLong()) }
    }

    /**
     * planKg(d) = startKg + (targetKg − startKg) × clamp(d / span, 0, 1)
     *
     * With no deadline the plan line is flat at the target, so the chart can still
     * draw a goal without implying a schedule.
     */
    fun planKgAt(plan: Plan, day: Int): Float {
        if (plan.mode == PlanMode.NO_DEADLINE) return plan.targetKg
        val span = spanDays(plan)
        if (span <= 0) return plan.targetKg
        val t = min(1f, max(0f, day.toFloat() / span))
        return plan.startKg + (plan.targetKg - plan.startKg) * t
    }

    /**
     * §13: the trend needs at least two entries and a trend in the right direction,
     * otherwise the projection and its line are hidden rather than showing an
     * absurd date. Returned in the plan's sign convention — positive is progress.
     */
    fun trendPerDay(entries: List<WeightEntry>, direction: Float): Float {
        if (entries.size < 2) return 0f
        val first = entries.first()
        val last = entries.last()
        val days = ChronoUnit.DAYS.between(first.date, last.date).toInt()
        if (days <= 0) return 0f
        return (first.kg - last.kg) * direction / days
    }

    /**
     * Compute everything from a plan and the full, date-ascending history.
     * [today] is passed in rather than read from the clock so this stays testable.
     */
    fun stats(plan: Plan, entries: List<WeightEntry>, today: LocalDate): PlanStats {
        val direction = if (plan.targetKg <= plan.startKg) 1f else -1f
        val dated = plan.mode != PlanMode.NO_DEADLINE
        val span = spanDays(plan)
        val daysSince = dayIndex(plan.startDate, today)
        val targetDate = targetDate(plan)

        val last = entries.lastOrNull()
        val currentKg = last?.kg ?: plan.startKg
        val planToday = planKgAt(plan, daysSince)

        // Day zero is the baseline, not a day of the schedule. The plan takes whatever
        // the user weighs today as its starting point and only begins asking for
        // progress tomorrow, so a plan set this morning cannot already be behind.
        // ...and nothing to measure until something has been logged. Without an
        // entry currentKg falls back to the start weight, which would read as
        // behind by one day's worth for every day the plan has existed.
        val scheduleStarted = dated && daysSince > 0 && entries.isNotEmpty()
        val aheadKg = if (scheduleStarted) (planToday - currentKg) * direction else 0f
        val behind = scheduleStarted && aheadKg < -BEHIND_THRESHOLD_KG

        val lostKg = (plan.startKg - currentKg) * direction
        val leftKg = max(0f, (currentKg - plan.targetKg) * direction)

        // The target date itself still counts: the plan has not lapsed until the day
        // after it. `>=` retired the rate a day early and made the Plan screen invite
        // an edit on the deadline morning.
        val targetDatePassed = dated && span > 0 && daysSince > span
        val daysLeft = max(1, span - daysSince)
        val planRatePerDay = if (dated && span > 0) (plan.startKg - plan.targetKg) * direction / span else 0f
        val neededPerDay = if (dated && !targetDatePassed) leftKg / daysLeft else 0f

        val trend = trendPerDay(entries, direction)
        val projected = if (trend > 0.001f && leftKg > 0f) {
            val daysToGo = (leftKg / trend).roundToLong()
            // A projection more than ten years out is not information, it is noise.
            if (daysToGo in 0..3650) today.plusDays(daysToGo) else null
        } else if (trend > 0.001f && leftKg <= 0f) {
            today
        } else {
            null
        }

        val denom = (plan.startKg - plan.targetKg) * direction
        val progress = if (denom <= 0.05f) 0f else min(1f, max(0f, lostKg / denom))

        // "Last 7 days" has to span roughly seven days and end near today. The
        // reference was previously bounded only from above, so the newest entry older
        // than a week won whatever its age: a month's gap in logging silently became
        // the measurement interval and a 26-day loss was published as a weekly one.
        // With no entry in the window there is no honest figure, and null renders "—".
        val weekChange = last?.takeIf { it.date >= today.minusDays(3) }?.let { l ->
            val ref = entries.lastOrNull {
                it.date <= l.date.minusDays(7) && it.date >= l.date.minusDays(11)
            }
            if (ref == null) null else (ref.kg - l.kg) * direction
        }

        return PlanStats(
            plan = plan,
            direction = direction,
            dated = dated,
            startDate = plan.startDate,
            targetDate = targetDate,
            spanDays = span,
            daysSinceStart = daysSince,
            currentKg = currentKg,
            lastEntryDate = last?.date,
            planKgToday = planToday,
            aheadKg = aheadKg,
            behind = behind,
            lostKg = lostKg,
            leftKg = leftKg,
            reached = entries.isNotEmpty() && (currentKg - plan.targetKg) * direction <= 1e-4f,
            planRatePerDay = planRatePerDay,
            neededPerDay = neededPerDay,
            neededPerWeek = neededPerDay * 7f,
            trendPerDay = trend,
            projectedFinish = projected,
            progress = progress,
            weekChangeKg = weekChange,
            targetDatePassed = targetDatePassed,
            scheduleStarted = scheduleStarted,
        )
    }

    /**
     * §3: startDate and startKg are "pinned when the plan is created", so a new plan
     * begins TODAY, at the weight the user is at today.
     *
     * Anchoring the start to the last entry's date instead puts the plan line's origin
     * in the past, and the plan then expects progress for days that elapsed before it
     * existed — so the very first day already reads as behind schedule. On a plan asking
     * for more than 0.05 kg a day, one missed weigh-in is enough to turn the whole app
     * amber before the user has done anything.
     */
    fun newPlan(
        today: LocalDate,
        startKg: Float,
        targetKg: Float,
        mode: PlanMode,
        targetDate: LocalDate?,
        ratePerWeek: Float?,
    ): Plan = Plan(
        startDate = today,
        startKg = startKg,
        targetKg = targetKg,
        mode = mode,
        targetDate = if (mode == PlanMode.BY_DATE) targetDate else null,
        ratePerWeek = if (mode == PlanMode.AT_PACE) ratePerWeek else null,
    )

    /**
     * §13: a target equal to, or past, the start weight in the wrong direction is
     * rejected at edit time. Returns null when the target is acceptable.
     */
    fun validateTarget(startKg: Float, targetKg: Float): String? = when {
        abs(startKg - targetKg) < 0.05f -> "Target is the same as your starting weight."
        else -> null
    }
}
