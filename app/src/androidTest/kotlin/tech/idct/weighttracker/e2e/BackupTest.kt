package tech.idct.weighttracker.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The backup round trip: automatic upload, manual restore, manual clear. */
class BackupTest : E2eTestBase() {

    private fun openAccount() {
        tapTab("Settings")
        tap("Account & backup")
    }

    @Test
    fun backup() {
        val email = "e2e.backup@example.com"
        createConfirmedUser(email, "backup-pass-1")
        resetApp(seed = true, signedIn = email to "backup-pass-1")
        val seeded = runBlocking { repo.entries().size }
        launchApp()
        openAccount()
        waitFor("No backup yet")
        screenshot("before-backup")

        compose.onNodeWithTag("backupSwitch").performClick()
        waitFor("Backed up", timeoutMs = 40_000)
        screenshot("after-backup")
        pollUntil { serverBackup(email) != null }

        val payload = serverBackup(email)!!.getJSONObject("payload")
        assertEquals(seeded, payload.getJSONArray("entries").length())
        assertEquals(75.0, payload.getJSONObject("plan").getDouble("targetKg"), 0.001)

        // A change while the switch is on uploads by itself. Logging replaces
        // today's entry, so the proof is the value, not the count.
        device.pressBack()
        tapByDescription("Log weight")
        logWeightViaKeypad("78.8")
        pollUntil(timeoutMs = 40_000) {
            val entries = serverBackup(email)?.getJSONObject("payload")?.getJSONArray("entries")
            entries != null &&
                entries.getJSONObject(entries.length() - 1).getDouble("kg") == 78.8
        }
        screenshot("after-auto-sync")
        deleteUser(email)
    }

    @Test
    fun restore() {
        val email = "e2e.restore@example.com"
        createConfirmedUser(email, "restore-pass-1")

        // Day one: a phone full of data backs itself up.
        resetApp(seed = true, signedIn = email to "restore-pass-1")
        val seeded = runBlocking { repo.entries().size }
        launchApp()
        openAccount()
        waitFor("No backup yet")
        compose.onNodeWithTag("backupSwitch").performClick()
        pollUntil(timeoutMs = 40_000) { serverBackup(email) != null }

        // Day two: a fresh phone, the same account.
        resetApp(seed = false, signedIn = email to "restore-pass-1")
        launchApp()
        waitFor("Log first weight")
        screenshot("fresh-phone")
        openAccount()
        waitFor("Last backup date:")
        screenshot("backup-offered")

        tap("Restore from the backup")
        waitFor("Restore from the backup?")
        screenshot("restore-confirm")
        tap("Restore everything")
        pollUntil { runBlocking { repo.entries().size } == seeded }

        device.pressBack() // account → settings
        tapTab("Home")
        waitFor("79.2", substring = true)
        screenshot("restored-home")

        // Clearing the cloud copy leaves the phone alone.
        openAccount()
        tap("Clear backed-up data")
        waitFor("Delete the backed-up data?")
        screenshot("clear-confirm")
        tap("Delete the backup")
        pollUntil { serverBackup(email) == null }
        waitFor("No backup yet")
        assertEquals(seeded, runBlocking { repo.entries().size })
        screenshot("after-clear")
        deleteUser(email)
    }
}
