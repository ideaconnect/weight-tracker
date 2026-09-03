package tech.idct.weighttracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
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
     * A weight axis: where its bottom sits, and the weights to label going up from
     * there. [lo] is always the first tick, so the lowest label lands ON the axis
     * instead of floating above it.
     */
    class Axis(val lo: Float, val ticks: List<Float>)

    /** Gridlines closer together than this are noise rather than a scale. */
    private const val GRID_LIMIT = 12

    /**
     * The scale for a plot whose bottom edge is its axis.
     *
     * [niceTicks] finds round weights strictly inside the domain, which leaves the
     * lowest of them hanging above the bottom of the plot by however much padding the
     * domain happened to carry — a gridline a few pixels clear of the axis, which
     * reads as a mistake.
     *
     * So the bottom is [lo] itself: the lowest weight the chart draws, which on a loss
     * plan is the goal. Rounding the bottom down to a whole kilogram instead is what
     * left the plan line landing above the X axis while the projection's dot, drawn on
     * the axis by construction, sat correctly on it — the two marks are the same
     * finish, and a chart that puts them on different lines is wrong about its own
     * subject. A bottom that is already a round weight keeps the plain ladder it
     * always had; one that is not is a label in its own right, with the round weights
     * carrying the grid above it, and the first of those dropped when it would sit so
     * close to the bottom label that the two read as a smudge.
     *
     * The step is chosen for tightness — 74.4 stays 74.4 rather than becoming 70 —
     * and [maxTicks] only thins out which of those weights get printed.
     */
    fun axis(lo: Float, hi: Float, maxTicks: Int): Axis {
        if (hi <= lo || maxTicks < 1) return Axis(lo, emptyList())
        val step = WEIGHT_STEPS.firstOrNull { s ->
            countFrom(floorTo(lo, s), hi, s) in 2..GRID_LIMIT
        } ?: return Axis(lo, emptyList())
        // The bottom is its own lowest label, so it carries the one decimal every
        // weight in the app is printed to; never upwards, or the goal would fall off.
        val bottom = roundTo(floorTo(lo, 0.1f), 0.1f)
        val count = countFrom(floorTo(bottom, step), hi, step)
        for (stride in 1..count) {
            val ticks = ladder(bottom, hi, step, step * stride)
            if (ticks.size <= maxTicks) return Axis(bottom, ticks)
        }
        return Axis(bottom, listOf(bottom))
    }

    /**
     * The labels for a bottom of [bottom] and a gridline every [grid]: the bottom
     * first, then the round weights above it. Anchored on the bottom when the bottom
     * is itself one of them, so an axis of whole kilograms stays whole kilograms.
     */
    private fun ladder(bottom: Float, hi: Float, step: Float, grid: Float): List<Float> {
        val onGrid = abs(bottom / step - Math.round(bottom / step)) < 1e-3f
        val ticks = ArrayList<Float>()
        val first: Float
        if (onGrid) {
            first = bottom
        } else {
            ticks.add(bottom)
            val above = ceilTo(bottom, grid)
            first = if (above - bottom < grid * 0.5f) above + grid else above
        }
        var i = 0
        while (first + i * grid <= hi + grid * 1e-3f) {
            ticks.add(roundTo(first + i * grid, step))
            i++
        }
        return ticks
    }

    private fun floorTo(value: Float, step: Float): Float =
        floor(value / step + 1e-3f) * step

    private fun ceilTo(value: Float, step: Float): Float =
        ceil(value / step - 1e-3f) * step

    private fun countFrom(bottom: Float, hi: Float, step: Float): Int =
        floor((hi - bottom) / step + 1e-3f).toInt() + 1

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
