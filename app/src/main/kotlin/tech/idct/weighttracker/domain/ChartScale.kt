package tech.idct.weighttracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Axis ticks for the chart and the widget sparklines, in one place so they read
 * the same scale. Section 6 gives the chart weight labels in a left gutter and
 * dates underneath; a scale is only readable when its labels land on round
 * numbers and real calendar boundaries, not on whatever fraction of the range a
 * gridline happens to fall at.
 */
object ChartScale {

    /** Steps a weight axis may use, in the display unit. */
    private val WEIGHT_STEPS = floatArrayOf(0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f)

    /**
     * Round-number ticks inside [lo, hi] — both in the display unit — using the
     * finest step that yields at most [maxTicks] of them. Empty when the range is
     * degenerate.
     */
    fun niceTicks(lo: Float, hi: Float, maxTicks: Int): List<Float> {
        if (hi <= lo || maxTicks < 1) return emptyList()
        for (step in WEIGHT_STEPS) {
            // A hair of slack so a bound that is itself a round number keeps its tick.
            val first = ceil(lo / step - 1e-3f).toInt()
            val last = floor(hi / step + 1e-3f).toInt()
            val count = last - first + 1
            if (count <= maxTicks) {
                return (first..last).map { i -> roundTo(i * step, step) }
            }
        }
        return emptyList()
    }

    /** "84.0", the one decimal every weight in the app carries. */
    fun label(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun roundTo(value: Float, step: Float): Float =
        if (step >= 1f) Math.round(value).toFloat() else Math.round(value * 10f) / 10f

    /**
     * Calendar-aligned date ticks for the window of day indices [x0, x1] relative
     * to [startDate]: every day, every other day, Mondays, alternate Mondays,
     * month starts, then coarser — the finest that fits [maxTicks] labels.
     */
    fun dateTicks(startDate: LocalDate, x0: Float, x1: Float, maxTicks: Int): List<LocalDate> {
        val from = startDate.plusDays(ceil(x0).toLong())
        val to = startDate.plusDays(floor(x1).toLong())
        if (to.isBefore(from) || maxTicks < 1) return emptyList()
        val limit = max(1, maxTicks)
        val days = daysBetween(from, to)
        for (rule in RULES) {
            val ticks = days.filter(rule)
            if (ticks.size <= limit) return ticks
        }
        // Beyond a decade in one window: every Nth January.
        val januaries = days.filter { it.dayOfMonth == 1 && it.monthValue == 1 }
        val every = ceil(januaries.size / limit.toFloat()).toInt().coerceAtLeast(1)
        return januaries.filterIndexed { index, _ -> index % every == 0 }
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    /** 1970-01-05 was a Monday, so weeks are counted from epoch day 4. */
    private fun weekIndex(date: LocalDate): Long = Math.floorDiv(date.toEpochDay() - 4, 7L)

    private val RULES: List<(LocalDate) -> Boolean> = listOf(
        { true },
        { it.toEpochDay() % 2 == 0L },
        { it.dayOfWeek == DayOfWeek.MONDAY },
        { it.dayOfWeek == DayOfWeek.MONDAY && weekIndex(it) % 2 == 0L },
        { it.dayOfMonth == 1 },
        { it.dayOfMonth == 1 && it.monthValue % 2 == 1 },
        { it.dayOfMonth == 1 && it.monthValue in setOf(1, 4, 7, 10) },
        { it.dayOfMonth == 1 && it.monthValue in setOf(1, 7) },
        { it.dayOfMonth == 1 && it.monthValue == 1 },
    )
}
