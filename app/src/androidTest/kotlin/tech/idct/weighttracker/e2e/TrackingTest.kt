package tech.idct.weighttracker.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Logging, the plan verdicts, and the finish line. */
class TrackingTest : E2eTestBase() {

    @Test
    fun manualEntry() {
        resetApp(seed = false)
        launchApp()
        waitFor("Log first weight")
        screenshot("day-one")

        tap("Log first weight")
        logWeightViaKeypad("82.4")
        waitFor("82.4", substring = true)
        screenshot("first-entry")

        // Same day, new number: one entry per day, replaced not appended.
        tapByDescription("Log weight")
        logWeightViaKeypad("82.1")
        waitFor("82.1", substring = true)
        runBlocking {
            val entries = repo.entries()
            assertEquals(1, entries.size)
            assertEquals(82.1f, entries.single().kg, 0.001f)
            assertEquals(LocalDate.now(), entries.single().date)
        }
        screenshot("same-day-replaced")
    }

    @Test
    fun onTrack() {
        resetApp(seed = true)
        launchApp()
        waitFor("ahead", substring = true)
        screenshot("on-track-home")
        tapTab("Plan")
        waitFor("Lost so far")
        screenshot("on-track-plan")
    }

    @Test
    fun behind() {
        resetApp(seed = true, behind = true)
        launchApp()
        waitFor("behind", substring = true)
        screenshot("behind-home")
        tapTab("Plan")
        waitFor("Left to go")
        screenshot("behind-plan")
    }

    @Test
    fun finishPlan() {
        resetApp(seed = true)
        launchApp()
        waitFor("79.2", substring = true)
        screenshot("almost-there")

        tapByDescription("Log weight")
        logWeightViaKeypad("75.0")
        waitFor("You did it")
        screenshot("trophy")

        tap("Keep tracking")
        waitFor("75.0", substring = true)
        screenshot("after-celebration")
    }
}
