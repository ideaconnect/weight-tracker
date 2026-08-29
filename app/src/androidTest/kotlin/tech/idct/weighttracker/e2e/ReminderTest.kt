package tech.idct.weighttracker.e2e

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.RemoteInput
import androidx.core.os.bundleOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.idct.weighttracker.MainActivity
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.work.Reminder
import tech.idct.weighttracker.work.ReminderReceiver
import java.time.LocalDate
import java.time.LocalTime

/**
 * Section 9, end to end: the screen that switches the reminder on, the
 * notification itself with its real numbers, the inline reply both ways,
 * Snooze, and the tap that opens the log sheet.
 */
class ReminderTest : E2eTestBase() {

    private val notifications: NotificationManager
        get() = app.getSystemService(NotificationManager::class.java)

    private fun reminder(): StatusBarNotification? =
        notifications.activeNotifications.firstOrNull { it.id == Reminder.NOTIFICATION_ID }

    private fun StatusBarNotification.title() =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

    private fun StatusBarNotification.text() =
        notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

    private fun StatusBarNotification.actionTitles() =
        notification.actions.orEmpty().map { it.title.toString() }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            device.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        }
    }

    /**
     * Whether an alarm under [action] is pending. Only a pending entry has its
     * `tag=` line followed by the `type=… origWhen=` line; the removal history
     * repeats the tag, so a plain contains() would never see a cancel.
     */
    private fun pendingAlarm(action: String): Boolean {
        val dump = device.executeShellCommand("dumpsys alarm")
        return Regex("""tag=\*walarm\*:${Regex.escape(action)}\s*\n\s*type=RTC_WAKEUP origWhen=""")
            .containsMatchIn(dump)
    }

    private fun waitForReminder(timeoutMs: Long = 15_000, condition: (StatusBarNotification) -> Boolean): StatusBarNotification {
        var found: StatusBarNotification? = null
        pollUntil(timeoutMs, 500) {
            found = reminder()?.takeIf(condition)
            found != null
        }
        return found!!
    }

    /** The receiver, exactly as SystemUI would send it an inline reply. */
    private fun replyInline(text: String) {
        val intent = Intent(app, ReminderReceiver::class.java).setAction(Reminder.ACTION_LOG)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(Reminder.KEY_WEIGHT_INPUT).build()),
            intent,
            bundleOf(Reminder.KEY_WEIGHT_INPUT to text),
        )
        app.sendBroadcast(intent)
    }

    private fun expectedBody(): String = runBlocking {
        Format.reminderBody(repo.entries(), repo.plan(), repo.settings().unit, LocalDate.now())
    }

    /** Settings → Daily reminder: switch it on, pick 21:00, preview it. */
    @Test
    fun reminderScreen() {
        grantNotifications()
        resetApp(seed = true)
        launchApp()
        tapTab("Settings")
        tap("Daily reminder")
        waitFor("Remind me to weigh in")
        screenshot("reminder-off")

        compose.onNodeWithTag("reminderSwitch").performClick()
        pollUntil { runBlocking { repo.settings().reminderEnabled } }
        tap("21:00")
        pollUntil { runBlocking { repo.settings().reminderTime } == LocalTime.of(21, 0) }
        screenshot("reminder-on-at-2100")

        tap("Preview the notification")
        waitFor("Evening weigh-in")
        waitFor("ahead of plan", substring = true)
        screenshot("preview")
        device.pressBack()

        // The alarm is really armed, under the daily action.
        pollUntil { pendingAlarm(Reminder.ACTION_SHOW) }

        device.pressBack()
        waitFor("21:00")
        screenshot("settings-row")
    }

    /** The notification carries the real numbers; a bad reply is refused in words, a good one saves. */
    @Test
    fun postInlineLogAndSnooze() {
        grantNotifications()
        resetApp(seed = true)
        runBlocking {
            // Today's fixture entry would (rightly) hide the inline field.
            repo.deleteEntry(LocalDate.now())
            repo.updateSettings { it.copy(reminderEnabled = true) }
            Reminder.rescheduleNow(app)
            Reminder.post(app)
        }
        pollUntil { pendingAlarm(Reminder.ACTION_SHOW) }
        val posted = waitForReminder { it.actionTitles().isNotEmpty() }
        assertTrue(posted.title(), posted.title().endsWith("weigh-in"))
        assertEquals(expectedBody(), posted.text())
        assertTrue(posted.text(), posted.text().contains("of plan"))
        assertEquals(listOf("Log", "Open app", "Snooze 1h"), posted.actionTitles())
        device.openNotification()
        SystemClock.sleep(1_200)
        screenshot("reminder-in-shade")
        device.pressBack()

        // Not a weight: the card says so, keeps the field, and writes nothing.
        replyInline("abc")
        val refused = waitForReminder { it.text().startsWith("Not saved") }
        assertTrue(refused.actionTitles().contains("Log"))
        assertEquals(null, runBlocking { repo.entry(LocalDate.now()) })

        // A weight, with a comma: saved, confirmed, and the field is gone.
        replyInline("80,1")
        pollUntil { runBlocking { repo.entry(LocalDate.now())?.kg } == 80.1f }
        val confirmed = waitForReminder { it.title() == "Logged 80.1 kg" }
        assertEquals("Saved to ${LocalDate.now()}.", confirmed.text())
        assertTrue(confirmed.actionTitles().isEmpty())
        device.openNotification()
        SystemClock.sleep(1_200)
        screenshot("logged-confirmation")
        device.pressBack()

        // Posted again now that today is logged: informational, no inline field.
        runBlocking { Reminder.post(app) }
        val informational = waitForReminder { it.text().startsWith("Already logged today: 80.1 kg") }
        assertEquals(listOf("Open app", "Snooze 1h"), informational.actionTitles())

        // Snooze: the card goes, its own alarm is armed, the daily one is untouched.
        app.sendBroadcast(Intent(app, ReminderReceiver::class.java).setAction(Reminder.ACTION_SNOOZE))
        pollUntil { reminder() == null }
        pollUntil { pendingAlarm(Reminder.ACTION_SHOW_SNOOZED) && pendingAlarm(Reminder.ACTION_SHOW) }

        // Off: nothing is left behind, not even the snooze.
        runBlocking { repo.updateSettings { it.copy(reminderEnabled = false) }; Reminder.rescheduleNow(app) }
        pollUntil { !pendingAlarm(Reminder.ACTION_SHOW_SNOOZED) && !pendingAlarm(Reminder.ACTION_SHOW) }
        assertFalse(Reminder.isShowing(app))
    }

    /**
     * This app's MainActivity records in the task list, e.g.
     * "ActivityRecord{1a2b3c u0 tech.idct.weighttracker.debug/tech.idct.weighttracker.MainActivity t42}"
     * — anchored on the component, since other apps' MainActivity tasks may be in Recents too.
     */
    private fun activityRecords(): List<String> {
        val component = Regex.escape("${app.packageName}/${MainActivity::class.java.name}")
        return Regex("""\* Hist\s+#\d+: (ActivityRecord\{[0-9a-f]+ u0 $component[^}]*\})""")
            .findAll(device.executeShellCommand("dumpsys activity activities"))
            .map { it.groupValues[1] }
            .toList()
    }

    /**
     * The tap while the app is in the background: the log sheet opens on the one
     * activity that was already there — the same record, delivered through
     * onNewIntent — not a second instance on top of it.
     *
     * Launched by intent rather than through ActivityScenario: the scenario cannot
     * follow an activity that a later intent re-delivers into, and its teardown
     * used to fail after the test had passed.
     */
    @Test
    fun notificationTapOpensTheLogSheetOnce() {
        grantNotifications()
        resetApp(seed = true)
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        waitFor("79.2", substring = true)
        val before = activityRecords()
        assertEquals("one MainActivity after launch", 1, before.size)
        device.pressHome()
        SystemClock.sleep(800)

        // Exactly what the notification's content intent sends.
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .setAction(Reminder.ACTION_OPEN)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                .putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_LOG)
        )
        waitFor("Log weight")
        screenshot("deep-link-log-sheet")

        val after = activityRecords()
        assertEquals("one MainActivity, not a stack of them", 1, after.size)
        assertEquals("the same instance, re-delivered — not recreated", before.single(), after.single())
    }
}
