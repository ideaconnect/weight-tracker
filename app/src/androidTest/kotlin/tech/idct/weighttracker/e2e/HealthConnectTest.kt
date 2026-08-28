package tech.idct.weighttracker.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.idct.weighttracker.data.health.HealthConnectManager
import tech.idct.weighttracker.debug.SeedData
import java.time.LocalDate

/**
 * §4 both ways: records written by other apps come in on app open (earliest of
 * the day wins), and manual entries go back out when the write side is on.
 */
class HealthConnectTest : E2eTestBase() {

    @Before
    fun grantPermissions() {
        grantHealthPermissions()
    }

    @Test
    fun syncFromHealthConnect() {
        resetApp(seed = true)
        runBlocking {
            // Records this app wrote in earlier runs would otherwise take part in
            // the earliest-of-day pick and skew the expected numbers.
            HealthConnectManager(app).client?.deleteRecords(
                androidx.health.connect.client.records.WeightRecord::class,
                androidx.health.connect.client.time.TimeRangeFilter.between(
                    LocalDate.now().minusDays(14)
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
                    java.time.Instant.now(),
                ),
            )
            SeedData.hcWrite(app) // three records, two days, one duplicate day
            repo.updateSettings { it.copy(healthConnectEnabled = true) }
        }
        launchApp() // §4 rule 1: autosync on open

        val yesterday = LocalDate.now().minusDays(1)
        pollUntil(timeoutMs = 40_000) {
            runBlocking { repo.entry(yesterday) } != null
        }
        runBlocking {
            // 06:00 (81.1) beats 20:00 (88.8): earliest of the day wins.
            assertEquals(81.1f, repo.entry(yesterday)!!.kg, 0.001f)
            assertEquals(81.9f, repo.entry(yesterday.minusDays(1))!!.kg, 0.001f)
        }
        // §4 rule 1: the on-open sync is silent — the imported numbers are the proof.
        waitFor("79.2", substring = true)
        screenshot("home-after-sync")

        tapTab("History")
        waitFor("81.1", substring = true)
        screenshot("imported-entries")
    }

    @Test
    fun syncToHealthConnect() {
        resetApp(seed = true)
        runBlocking { repo.updateSettings { it.copy(healthConnectEnabled = true) } }
        launchApp()
        waitFor("79.2", substring = true)

        tapByDescription("Log weight")
        logWeightViaKeypad("78.9")
        waitFor("78.9", substring = true)
        screenshot("logged-locally")

        val today = LocalDate.now()
        val health = HealthConnectManager(app)
        pollUntil(timeoutMs = 40_000) {
            runBlocking {
                health.readWeights(today, today).any { kotlin.math.abs(it.kg - 78.9f) < 0.01f }
            }
        }
        assertTrue(runBlocking { health.readWeights(today, today) }.isNotEmpty())
        screenshot("written-to-health-connect")
    }
}
