package tech.idct.weighttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The axes land on round weights and real calendar boundaries. */
class ChartScaleTest {

    /**
     * The one that matters: the plot's bottom edge is its own lowest label, so the
     * bottom gridline lies along the X axis instead of floating above it. Everything
     * else here is a way of checking that stays true without the scale going silly.
     */
    @Test
    fun `the lowest label is the bottom of the plot`() {
        val ranges = listOf(
            74.4f to 82.7f,     // the sample loss plan, band included
            69.4f to 78.3f,     // a gain plan
            78.6f to 80.3f,     // a week of a plan, under a kilogram of range
            79.4f to 110.3f,    // thirty kilograms
            164.0f to 182.3f,   // the sample plan in pounds
        )
        for ((lo, hi) in ranges) {
            for (maxTicks in 2..8) {
                val axis = ChartScale.axis(lo, hi, maxTicks)
                assertTrue("$lo..$hi at $maxTicks has no ticks", axis.ticks.isNotEmpty())
                assertEquals(
                    "$lo..$hi at $maxTicks does not start on its own bottom",
                    axis.lo, axis.ticks.first(), 0.001f,
                )
                assertTrue(
                    "$lo..$hi at $maxTicks moved the bottom up, over the content",
                    axis.lo <= lo + 0.001f,
                )
                assertTrue(
                    "$lo..$hi at $maxTicks used more labels than it was given",
                    axis.ticks.size <= maxTicks,
                )
            }
        }
    }

    @Test
    fun `the worked example stands on its own lowest weight`() {
        // 74.4 to 82.7 kg, the domain the sample plan draws: six labels at most.
        // The bottom is the lowest weight drawn, and whole kilograms above it.
        val axis = ChartScale.axis(74.4f, 82.7f, 6)
        assertEquals(74.4f, axis.lo, 0.001f)
        assertEquals(listOf(74.4f, 76f, 78f, 80f, 82f), axis.ticks)
    }

    /**
     * The one the user reported: a goal that is not a whole kilogram used to leave
     * the plan line landing above the X axis, while the projection's dot — drawn on
     * the axis by construction — sat correctly on it. Both mark the same finish, so
     * the bottom of the plot is the goal itself.
     */
    @Test
    fun `a goal off the round grid still lands on the axis`() {
        for (goal in listOf(74.5f, 74.4f, 85.5f, 92.3f, 68.7f)) {
            val axis = ChartScale.axis(goal, goal + 8.3f, 6)
            assertEquals("$goal does not sit on the axis", goal, axis.lo, 0.001f)
            assertEquals("$goal is not the bottom label", goal, axis.ticks.first(), 0.001f)
        }
    }

    /** No second label close enough to the bottom one to read as a smudge. */
    @Test
    fun `the label above the bottom keeps its distance`() {
        for (goal in listOf(74.1f, 74.5f, 74.9f, 161.2f)) {
            val axis = ChartScale.axis(goal, goal + 8.3f, 6)
            val gaps = axis.ticks.zipWithNext { a, b -> b - a }
            val grid = gaps.drop(1).minOrNull() ?: return
            assertTrue(
                "$goal crowds its bottom label: ${axis.ticks}",
                gaps.first() >= grid * 0.5f - 0.001f,
            )
        }
    }

    @Test
    fun `a week's range uses fractions of a kilogram`() {
        val axis = ChartScale.axis(78.6f, 80.9f, 6)
        assertEquals(78.6f, axis.lo, 0.001f)
        assertEquals(listOf(78.6f, 79f, 79.4f, 79.8f, 80.2f, 80.6f), axis.ticks)
    }

    /**
     * A tight budget labels fewer of the same weights rather than reaching for a
     * coarser step — a step chosen by the label budget alone would drop the bottom
     * of this range from 74.4 to 70 and spend a third of the plot on empty air.
     */
    @Test
    fun `a widget with room for three labels keeps the bottom where it is`() {
        val axis = ChartScale.axis(74.4f, 82.7f, 3)
        assertEquals(74.4f, axis.lo, 0.001f)
        assertEquals(listOf(74.4f, 78f, 81f), axis.ticks)
    }

    @Test
    fun `a bound that is itself round keeps its tick`() {
        val axis = ChartScale.axis(74f, 80f, 6)
        assertEquals(74f, axis.lo, 0.001f)
        assertEquals(74f, axis.ticks.first(), 0.001f)
    }

    @Test
    fun `pounds get round pounds above their own bottom`() {
        // Twenty-four pounds is too wide for a one-pound grid to stay a scale rather
        // than a hatch, so it steps in fives — above a bottom that is still the
        // lowest weight the chart draws rather than the round number under it.
        val axis = ChartScale.axis(161.4f, 185.6f, 6)
        assertEquals(161.4f, axis.lo, 0.001f)
        assertEquals(listOf(161.4f, 165f, 170f, 175f, 180f, 185f), axis.ticks)
        assertEquals("165.0", ChartScale.label(165f))
    }

    @Test
    fun `an empty range has no ticks`() {
        assertTrue(ChartScale.axis(80f, 80f, 6).ticks.isEmpty())
    }

    private val start = LocalDate.of(2026, 7, 1)

    @Test
    fun `a week shows every day`() {
        val ticks = ChartScale.dateTicks(start, 0f, 6f, 8)
        assertEquals((0L..6L).map { start.plusDays(it) }, ticks)
    }

    @Test
    fun `a month shows Mondays`() {
        val ticks = ChartScale.dateTicks(start, 0f, 30f, 6)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.of(2026, 7, 6), ticks.first())
        assertEquals(LocalDate.of(2026, 7, 27), ticks.last())
    }

    @Test
    fun `the whole plan shows month starts`() {
        // Day -2 to day 158 of the 152-day sample plan.
        val ticks = ChartScale.dateTicks(start, -2f, 158f, 6)
        assertEquals(
            listOf(7, 8, 9, 10, 11, 12).map { LocalDate.of(2026, it, 1) },
            ticks,
        )
    }

    @Test
    fun `a small widget gets alternate months`() {
        // The plan starts on the first of July, itself a tick.
        val ticks = ChartScale.dateTicks(start, 0f, 152f, 3)
        assertEquals(listOf(7, 9, 11).map { LocalDate.of(2026, it, 1) }, ticks)
        // Started mid-month, the same span has room for only two.
        val midMonth = ChartScale.dateTicks(LocalDate.of(2026, 7, 3), 0f, 152f, 3)
        assertEquals(listOf(9, 11).map { LocalDate.of(2026, it, 1) }, midMonth)
    }

    @Test
    fun `days before the plan start are real dates, not the start again`() {
        val ticks = ChartScale.dateTicks(start, -3f, 2f, 8)
        assertEquals(LocalDate.of(2026, 6, 28), ticks.first())
        assertEquals(LocalDate.of(2026, 7, 3), ticks.last())
    }

    @Test
    fun `a decade in view still fits the label budget`() {
        val ticks = ChartScale.dateTicks(start, 0f, 365f * 12, 4)
        assertTrue(ticks.size in 1..4)
        assertTrue(ticks.all { it.dayOfMonth == 1 && it.monthValue == 1 })
    }
}
