package tech.idct.weighttracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import java.time.LocalDate

/**
 * Section 9 body, state by state. The plan is §5's worked example: 82.4 kg on
 * 2026-07-01 → 75.0 by 2026-11-30; on day 57 the plan line is at 79.6 kg.
 */
class FormatReminderTest {

    private val start = LocalDate.of(2026, 7, 1)
    private val today = LocalDate.of(2026, 8, 27)
    private val kg = WeightUnit.KG

    private val plan = Plan(
        startDate = start,
        startKg = 82.4f,
        targetKg = 75.0f,
        mode = PlanMode.BY_DATE,
        targetDate = LocalDate.of(2026, 11, 30),
        ratePerWeek = null,
    )

    private fun entry(date: LocalDate, weight: Float) = WeightEntry(date, weight, EntrySource.MANUAL)

    private fun body(entries: List<WeightEntry>, plan: Plan? = this.plan) =
        Format.reminderBody(entries, plan, kg, today)

    @Test
    fun `no entries at all`() {
        assertEquals("Log today's weight to start the chart.", body(emptyList()))
        assertEquals("Log today's weight to start the chart.", body(emptyList(), plan = null))
    }

    @Test
    fun `the worked example, logged yesterday`() {
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 79.2f))
        assertEquals("You're 0.4 kg ahead of plan. Yesterday you were 79.2 kg.", body(entries))
    }

    @Test
    fun `behind`() {
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 80.3f))
        assertEquals("You're 0.7 kg behind plan. Yesterday you were 80.3 kg.", body(entries))
    }

    @Test
    fun `a week-old entry is not yesterday`() {
        val entries = listOf(entry(start, 82.4f), entry(LocalDate.of(2026, 8, 20), 81.0f))
        assertEquals("You're 1.4 kg behind plan. Last logged 81.0 kg on 2026-08-20.", body(entries))
    }

    @Test
    fun `already logged today`() {
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 79.4f), entry(today, 79.2f))
        assertEquals("Already logged today: 79.2 kg. You're 0.4 kg ahead of plan.", body(entries))
    }

    @Test
    fun `day zero of a plan is not zero ahead`() {
        val fresh = plan.copy(startDate = today, targetDate = today.plusDays(152))
        assertEquals("Your plan starts from today. Yesterday you were 82.4 kg.", body(listOf(entry(today.minusDays(1), 82.4f)), fresh))
    }

    @Test
    fun `no deadline`() {
        val open = plan.copy(mode = PlanMode.NO_DEADLINE, targetDate = null)
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 79.2f))
        assertEquals("4.2 kg to your goal. Yesterday you were 79.2 kg.", body(entries, open))
    }

    @Test
    fun `target date passed`() {
        val lapsed = plan.copy(targetDate = LocalDate.of(2026, 8, 1))
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 79.2f))
        assertEquals(
            "The target date has passed — 4.2 kg to your goal. Yesterday you were 79.2 kg.",
            body(entries, lapsed),
        )
    }

    @Test
    fun `goal reached`() {
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 74.9f))
        assertEquals("You've reached your goal of 75.0 kg. Yesterday you were 74.9 kg.", body(entries))
    }

    @Test
    fun `no plan, only the last weigh-in`() {
        assertEquals("Yesterday you were 79.2 kg.", body(listOf(entry(today.minusDays(1), 79.2f)), plan = null))
    }

    @Test
    fun `pounds`() {
        val entries = listOf(entry(start, 82.4f), entry(today.minusDays(1), 79.2f))
        assertEquals(
            "You're 0.9 lb ahead of plan. Yesterday you were 174.6 lb.",
            Format.reminderBody(entries, plan, WeightUnit.LB, today),
        )
    }

    @Test
    fun `last weigh-in helper`() {
        assertNull(Format.lastWeighIn(emptyList(), today, kg))
        assertNull(Format.lastWeighIn(listOf(entry(today, 79.2f)), today, kg))
        assertEquals("Yesterday you were 79.2 kg", Format.lastWeighIn(listOf(entry(today.minusDays(1), 79.2f)), today, kg))
        assertEquals(
            "Last logged 79.2 kg on 2026-08-01",
            Format.lastWeighIn(listOf(entry(LocalDate.of(2026, 8, 1), 79.2f)), today, kg),
        )
    }

    @Test
    fun `rejected reply`() {
        assertEquals("Not saved — enter a weight between 20 and 400 kg.", Format.rejectedWeight(kg))
    }
}
