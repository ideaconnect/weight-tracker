package tech.idct.weighttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The worked example from §5 of the build specification:
 * start 82.4 kg on 2026-07-01, target 75.0 kg by 2026-11-30 — a 152-day span,
 * 0.049 kg/day. At day 57 (2026-08-27) the plan wants 79.6 kg; the user is at
 * 79.2 kg, so 0.4 kg ahead, 43% complete, 0.044 kg/day needed for the remaining
 * 95 days, projected finish 2026-11-10.
 */
class PlanMathTest {

    private val start = LocalDate.of(2026, 7, 1)
    private val today = LocalDate.of(2026, 8, 27)

    private val samplePlan = Plan(
        startDate = start,
        startKg = 82.4f,
        targetKg = 75.0f,
        mode = PlanMode.BY_DATE,
        targetDate = LocalDate.of(2026, 11, 30),
        ratePerWeek = null,
    )

    /** The prototype's sample history: a straight 82.4 → 79.2 over 57 days. */
    private val sampleEntries = listOf(
        WeightEntry(start, 82.4f, EntrySource.MANUAL),
        WeightEntry(today, 79.2f, EntrySource.MANUAL),
    )

    @Test
    fun `span is 152 days`() {
        assertEquals(152, PlanMath.spanDays(samplePlan))
    }

    @Test
    fun `plan rate is 0_049 kg per day`() {
        val stats = PlanMath.stats(samplePlan, sampleEntries, today)
        assertEquals(0.049f, stats.planRatePerDay, 0.0005f)
    }

    @Test
    fun `plan wants 79_6 kg on day 57`() {
        assertEquals(79.6f, PlanMath.planKgAt(samplePlan, 57), 0.05f)
    }

    @Test
    fun `user is 0_4 kg ahead and not behind`() {
        val stats = PlanMath.stats(samplePlan, sampleEntries, today)
        assertEquals(0.4f, stats.aheadKg, 0.05f)
        assertFalse(stats.behind)
    }

    @Test
    fun `progress is 43 percent`() {
        val stats = PlanMath.stats(samplePlan, sampleEntries, today)
        assertEquals(43, (stats.progress * 100).roundToInt())
    }

    @Test
    fun `0_044 kg per day needed over the remaining 95 days`() {
        val stats = PlanMath.stats(samplePlan, sampleEntries, today)
        assertEquals(95, stats.spanDays - stats.daysSinceStart)
        assertEquals(0.044f, stats.neededPerDay, 0.0005f)
    }

    @Test
    fun `projected finish at the current pace is 2026-11-10`() {
        val stats = PlanMath.stats(samplePlan, sampleEntries, today)
        assertEquals(LocalDate.of(2026, 11, 10), stats.projectedFinish)
    }

    @Test
    fun `behind the plan line by more than 0_05 kg turns the status amber`() {
        val entries = listOf(
            WeightEntry(start, 82.4f, EntrySource.MANUAL),
            WeightEntry(today, 79.7f, EntrySource.MANUAL),
        )
        val stats = PlanMath.stats(samplePlan, entries, today)
        assertTrue(stats.behind)
        assertTrue(stats.aheadKg < -PlanMath.BEHIND_THRESHOLD_KG)
    }

    // §13 edge cases -------------------------------------------------------

    @Test
    fun `a gain plan keeps ahead meaning the good side of the plan line`() {
        val gain = samplePlan.copy(startKg = 60f, targetKg = 68f)
        // Gaining faster than the plan asks is ahead, not behind.
        val entries = listOf(
            WeightEntry(start, 60f, EntrySource.MANUAL),
            WeightEntry(today, 64f, EntrySource.MANUAL),
        )
        val stats = PlanMath.stats(gain, entries, today)
        assertEquals(-1f, stats.direction, 0f)
        assertTrue("gaining ahead of plan should not read as behind", stats.aheadKg > 0)
        assertFalse(stats.behind)
        assertTrue(stats.progress > 0f && stats.progress <= 1f)
    }

    @Test
    fun `no deadline derives nothing`() {
        val open = samplePlan.copy(mode = PlanMode.NO_DEADLINE, targetDate = null)
        val stats = PlanMath.stats(open, sampleEntries, today)
        assertFalse(stats.dated)
        assertEquals(0f, stats.neededPerDay, 0f)
        assertEquals(0f, stats.aheadKg, 0f)
        assertFalse(stats.behind)
        assertNull(PlanMath.targetDate(open))
    }

    @Test
    fun `at pace derives the target date`() {
        val paced = samplePlan.copy(
            mode = PlanMode.AT_PACE,
            targetDate = null,
            ratePerWeek = 0.34f,
        )
        // 7.4 kg at 0.34 kg a week is 152.4 days.
        assertEquals(152, PlanMath.spanDays(paced))
        assertEquals(LocalDate.of(2026, 11, 30), PlanMath.targetDate(paced))
    }

    @Test
    fun `a target date in the past suppresses the daily rate`() {
        val stats = PlanMath.stats(samplePlan, sampleEntries, LocalDate.of(2027, 1, 1))
        assertTrue(stats.targetDatePassed)
        assertFalse(stats.hasRate)
        assertEquals(0f, stats.neededPerDay, 0f)
    }

    @Test
    fun `the plan line stops at the target after the target date`() {
        assertEquals(75.0f, PlanMath.planKgAt(samplePlan, 300), 0.001f)
    }

    @Test
    fun `a projection needs two entries and a trend in the right direction`() {
        val oneEntry = listOf(WeightEntry(today, 79.2f, EntrySource.MANUAL))
        assertNull(PlanMath.stats(samplePlan, oneEntry, today).projectedFinish)

        val wrongWay = listOf(
            WeightEntry(start, 79.0f, EntrySource.MANUAL),
            WeightEntry(today, 82.0f, EntrySource.MANUAL),
        )
        assertNull(PlanMath.stats(samplePlan, wrongWay, today).projectedFinish)
    }

    // §3: "pinned when the plan is created" ---------------------------------

    @Test
    fun `a plan created today starts today, not at the last entry`() {
        // The user last weighed in five days ago and sets a goal now.
        val lastWeighIn = today.minusDays(5)
        val entries = listOf(
            WeightEntry(today.minusDays(30), 84.0f, EntrySource.MANUAL),
            WeightEntry(lastWeighIn, 82.4f, EntrySource.MANUAL),
        )
        val plan = PlanMath.newPlan(
            today = today,
            startKg = 82.4f,
            targetKg = 75.0f,
            mode = PlanMode.BY_DATE,
            targetDate = today.plusDays(90),
            ratePerWeek = null,
        )

        assertEquals(today, plan.startDate)

        // Day one must be neither ahead nor behind: nothing has happened yet.
        val stats = PlanMath.stats(plan, entries, today)
        assertEquals(0, stats.daysSinceStart)
        assertEquals(82.4f, stats.planKgToday, 0.001f)
        assertEquals(0f, stats.aheadKg, 0.001f)
        assertFalse("a brand new plan cannot already be behind", stats.behind)
        assertEquals(0f, stats.progress, 0.001f)
    }

    @Test
    fun `pinning the start to an old entry would report the user behind on day one`() {
        // Guards the regression: the same plan anchored five days back.
        val lastWeighIn = today.minusDays(5)
        val entries = listOf(WeightEntry(lastWeighIn, 82.4f, EntrySource.MANUAL))
        val anchoredToEntry = Plan(
            startDate = lastWeighIn,
            startKg = 82.4f,
            targetKg = 75.0f,
            mode = PlanMode.BY_DATE,
            targetDate = today.plusDays(90),
            ratePerWeek = null,
        )
        val wrong = PlanMath.stats(anchoredToEntry, entries, today)
        assertTrue("this is the behaviour newPlan exists to avoid", wrong.behind)

        val right = PlanMath.stats(
            PlanMath.newPlan(today, 82.4f, 75.0f, PlanMode.BY_DATE, today.plusDays(90), null),
            entries,
            today,
        )
        assertFalse(right.behind)
    }

    @Test
    fun `an at-pace plan created today also starts today`() {
        val plan = PlanMath.newPlan(
            today = today,
            startKg = 82.4f,
            targetKg = 75.0f,
            mode = PlanMode.AT_PACE,
            targetDate = null,
            ratePerWeek = 0.5f,
        )
        assertEquals(today, plan.startDate)
        assertNull("AT_PACE derives its date rather than storing one", plan.targetDate)
        assertEquals(today.plusDays(104), PlanMath.targetDate(plan))
    }

    @Test
    fun `unit conversion rounds to one decimal in both units`() {
        assertEquals("79.2", Units.format(79.2f, WeightUnit.KG))
        assertEquals("174.6", Units.format(79.2f, WeightUnit.LB))
        assertEquals(79.2f, Units.fromDisplay(Units.toDisplay(79.2f, WeightUnit.LB), WeightUnit.LB), 0.01f)
    }

    @Test
    fun `plausibility follows the 20 to 400 kg equivalent range`() {
        assertTrue(Units.isPlausible(79.2f, WeightUnit.KG))
        assertFalse(Units.isPlausible(19f, WeightUnit.KG))
        assertFalse(Units.isPlausible(401f, WeightUnit.KG))
        assertTrue(Units.isPlausible(174.6f, WeightUnit.LB))
        assertFalse(Units.isPlausible(40f, WeightUnit.LB))
    }
}
