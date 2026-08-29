package tech.idct.weighttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The axes land on round weights and real calendar boundaries. */
class ChartScaleTest {

    @Test
    fun `the worked example's range gets whole even kilograms`() {
        // 73.2 to 84.2 kg, the domain the sample plan draws: six ticks at most.
        val ticks = ChartScale.niceTicks(73.2f, 84.2f, 6)
        assertEquals(listOf(74f, 76f, 78f, 80f, 82f, 84f), ticks)
    }

    @Test
    fun `a week's range uses half kilograms`() {
        val ticks = ChartScale.niceTicks(78.6f, 80.9f, 6)
        assertEquals(listOf(79f, 79.5f, 80f, 80.5f), ticks)
    }

    @Test
    fun `a widget with room for three ticks gets a coarser step`() {
        val ticks = ChartScale.niceTicks(73.2f, 84.2f, 3)
        assertEquals(listOf(75f, 80f), ticks)
    }

    @Test
    fun `a bound that is itself round keeps its tick`() {
        assertEquals(listOf(74f, 76f, 78f, 80f), ChartScale.niceTicks(74f, 80f, 6))
    }

    @Test
    fun `pounds get round pounds`() {
        // 161.4 to 185.6 lb: five-pound steps.
        val ticks = ChartScale.niceTicks(161.4f, 185.6f, 6)
        assertEquals(listOf(165f, 170f, 175f, 180f, 185f), ticks)
        assertEquals("165.0", ChartScale.label(165f))
    }

    @Test
    fun `an empty range has no ticks`() {
        assertTrue(ChartScale.niceTicks(80f, 80f, 6).isEmpty())
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
