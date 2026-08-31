package tech.idct.weighttracker.e2e

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.idct.weighttracker.widget.WidgetData
import tech.idct.weighttracker.widget.WidgetPalette
import tech.idct.weighttracker.work.DayChange
import tech.idct.weighttracker.work.Reminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/**
 * What the calendar does to this app, proved by moving the emulator's own clock.
 *
 * Every other scenario sees a single day, so it can only ever check that the
 * arithmetic is right once. But almost nothing here is a function of the data
 * alone: the plan line falls a little every day, so the same last weigh-in is
 * "0.4 kg ahead" one morning and "0.3 kg behind" a fortnight later, the daily
 * rate the plan asks for climbs as the days run out, and the projected finish
 * slides one day further off for every day nobody logs. The reminder and the
 * widgets have to follow all of it while the app is not even running.
 *
 * So these three scenarios seed once and then only move the clock — no extra
 * seeding, no DAY_CHANGED broadcast, no manual reminder post. Whatever changes
 * afterwards, the app did by itself. See [DeviceClock] for how the clock moves
 * and how it is always given back.
 *
 * The fixture is pinned to the specification's worked example rather than to
 * "today": with the clock under the test's control the plan really can start on
 * 2026-07-01 at 82.4 kg and aim at 75.0 kg by 2026-11-30, and every number below
 * is the one section 5 works through, to the decimal.
 */
class TemporalTest : E2eTestBase() {

    private companion object {
        /** §5's worked example: the plan starts here. */
        val PLAN_START: LocalDate = LocalDate.of(2026, 7, 1)
        val TARGET_DATE: LocalDate = LocalDate.of(2026, 11, 30)

        /** The reminder's time in these scenarios, and the minutes past midnight for it. */
        val REMINDER_AT: LocalTime = LocalTime.of(8, 0)
        const val REMINDER_MINUTE = 8 * 60
        val BEFORE_REMINDER: LocalTime = LocalTime.of(7, 55)
        val AFTER_REMINDER: LocalTime = LocalTime.of(8, 1)

        /**
         * Antialiasing means no two runs paint quite the same number of pixels, so
         * the status colours are matched with a little room and the counts are only
         * ever compared against a floor. A ring and a bar drawn in the status colour
         * cover a few thousand pixels; a launcher with none of it covers a few dozen.
         */
        const val COLOUR_TOLERANCE = 26
        const val STATUS_PIXELS = 600
    }

    /** Day [index] of the plan, as a date. Day 57 is the worked example's "today". */
    private fun day(index: Long): LocalDate = PLAN_START.plusDays(index)

    // ---------------------------------------------------------------- the plan

    /**
     * One fixture, five dates, and not a single row changed between the first four:
     * 0.4 kg ahead, 0.1 ahead, 0.3 behind, 1.3 behind — because the plan line kept
     * falling while the user's weight did not. Then one weigh-in on the last date
     * puts them back on the good side, to show the verdict is not a ratchet.
     *
     * The app is sent to the background and brought back rather than relaunched:
     * the view model re-reads the day when the screen comes back, and that is the
     * path a real user takes when they open the app the next morning.
     */
    @Test
    fun planFollowsTheCalendar() {
        clock.travelTo(day(57))
        resetApp(seed = true)

        // The clock was moved before seeding, so the fixture is the worked example
        // itself and not merely its shape.
        runBlocking {
            val plan = repo.plan() ?: error("the fixture saved no plan")
            assertEquals("plan start", PLAN_START, plan.startDate)
            assertEquals("target date", TARGET_DATE, plan.targetDate)
            assertEquals("start weight", 82.4f, plan.startKg, 0.001f)
            assertEquals("target weight", 75.0f, plan.targetKg, 0.001f)
            assertEquals("today's entry", 79.2f, repo.entry(day(57))?.kg ?: 0f, 0.001f)
        }

        startAppByIntent()
        assertHome(target = "79.6", verdict = "0.4 kg ahead", chip = "−0.4 vs plan")
        assertNotVisible("behind")
        screenshot("day-57-2026-08-27-home-0.4-kg-ahead")

        tapTab("Plan")
        assertPlan(lost = "3.2 kg", left = "4.2 kg", perDay = "0.04 kg", pace = "2026-11-10")
        screenshot("day-57-2026-08-27-plan-0.04-kg-a-day")
        tapTab("Home")

        // A week later, nothing logged: the margin is nearly gone.
        travelAndResume(day(64))
        assertHome(target = "79.3", verdict = "0.1 kg ahead", chip = "−0.1 vs plan")
        assertNotVisible("behind")
        screenshot("day-64-2026-09-03-home-0.1-kg-ahead")

        // A week after that the plan line has crossed them, and §6's status colour
        // turns the whole screen amber without anything being logged or deleted.
        travelAndResume(day(71))
        assertHome(target = "78.9", verdict = "0.3 kg behind", chip = "+0.3 vs plan")
        waitFor("Actual · behind")
        // The last weigh-in is a fortnight old now, so there is no honest weekly
        // figure left to print.
        waitFor("—")
        screenshot("day-71-2026-09-10-home-0.3-kg-behind")

        // Five weeks on. The weight has not moved, so "lost so far" and "left to go"
        // are frozen — but the plan now asks for 0.07 kg a day instead of 0.04, and
        // the projected finish has slid from before the deadline to after it.
        travelAndResume(day(92))
        assertHome(target = "77.9", verdict = "1.3 kg behind", chip = "+1.3 vs plan")
        screenshot("day-92-2026-10-01-home-1.3-kg-behind")

        tapTab("Plan")
        assertPlan(lost = "3.2 kg", left = "4.2 kg", perDay = "0.07 kg", pace = "2026-12-15")
        screenshot("day-92-2026-10-01-plan-0.07-kg-a-day")
        tapTab("Home")

        // And it is not a ratchet: one weigh-in on the day the clock says it is,
        // and the same plan reads green again.
        tapByDescription("Log weight")
        logWeightViaKeypad("77.0")
        assertHome(target = "77.9", verdict = "0.9 kg ahead", chip = "−0.9 vs plan")
        assertNotVisible("behind")
        screenshot("day-92-2026-10-01-home-logged-77.0-back-ahead")

        runBlocking {
            assertEquals(
                "the entry is written on the date the device believes in",
                77.0f,
                repo.entry(day(92))?.kg ?: 0f,
                0.001f,
            )
        }
    }

    // ------------------------------------------------------------ the reminder

    /**
     * Section 9's alarm chain, run for real across four days. Nothing in this
     * scenario posts a notification or sends a broadcast: it arms the reminder
     * once, then only moves the clock past 08:00 and waits.
     *
     * Each day proves a different thing. Day 58 is the plain case. Day 59 shows
     * the body is rebuilt from that day's arithmetic, not repeated — a different
     * margin, and "yesterday" giving way to a date once the last weigh-in is older
     * than that. Day 71 is the phone that was away for a week and a half: the
     * chain catches up, and the reminder that arrives says the user is behind, off
     * exactly the same fixture that said "ahead" on day 58.
     */
    @Test
    fun theReminderArrivesEveryDayAndKnowsWhatDayItIs() {
        grantNotifications()
        clock.travelTo(day(57))
        resetApp(seed = true, reminder = true, reminderMinute = REMINDER_MINUTE)

        // 09:00 on day 57: this morning's 08:00 is past, so the chain starts at
        // tomorrow's.
        assertEquals(LocalDateTime.of(day(58), REMINDER_AT), armedDailyAlarm())
        assertNull("nothing has been posted yet", reminder())

        val first = awaitReminderOn(day(58))
        assertEquals("Morning weigh-in", first.title())
        assertEquals(
            "You're 0.4 kg ahead of plan. Yesterday you were 79.2 kg.",
            first.text(),
        )
        assertEquals(listOf("Log", "Open app", "Snooze 1h"), first.actionTitles())
        shootTheShade("day-58-2026-08-28-reminder-0.4-kg-ahead")

        // The chain armed tomorrow's before it posted today's.
        assertEquals(LocalDateTime.of(day(59), REMINDER_AT), armedDailyAlarm())

        // Day 59: a smaller margin, and the last weigh-in is no longer "yesterday".
        val second = awaitReminderOn(day(59))
        assertEquals(
            "You're 0.3 kg ahead of plan. Last logged 79.2 kg on 2026-08-27.",
            second.text(),
        )

        // Eleven days later, the same fixture, the opposite verdict.
        val late = awaitReminderOn(day(71))
        assertEquals(
            "You're 0.3 kg behind plan. Last logged 79.2 kg on 2026-08-27.",
            late.text(),
        )
        shootTheShade("day-71-2026-09-10-reminder-0.3-kg-behind")

        assertEquals(LocalDateTime.of(day(72), REMINDER_AT), armedDailyAlarm())
        assertEquals(
            "the alarm-side mirror records the day it was really delivered",
            day(71),
            deliveredDay(),
        )
    }

    // -------------------------------------------------------------- the widgets

    /**
     * Section 8's widgets have no screen to be resumed on and no user to open
     * them: the only thing that tells them a day has passed is the day-change
     * refresh, and §6 says every ring, bar and percentage on the launcher turns
     * amber the moment the plan line crosses the user.
     *
     * So this places the ring and the bar, photographs the launcher, moves the
     * clock a fortnight and photographs it again — and reads the launcher's own
     * pixels to prove the widgets really redrew rather than sitting on the colour
     * they were pinned with.
     */
    @Test
    fun widgetsFollowTheCalendar() {
        clock.travelTo(day(57))
        resetApp(seed = true, unlock = true)

        // An empty launcher, so both widgets land on the first page and the shot
        // shows them rather than a page of somebody else's icons.
        device.executeShellCommand("pm clear com.google.android.apps.nexuslauncher")
        device.pressHome()
        SystemClock.sleep(1_500)

        launchApp()
        openWidgetGallery()
        placeWidget("RING")
        launchApp()
        openWidgetGallery()
        placeWidget("BAR")

        // The one press of Home: pinning leaves the launcher on the page it put the
        // widgets on, and pressing Home again would scroll back to the default page,
        // where there are none. Everything from here on photographs this page.
        device.pressHome()
        SystemClock.sleep(2_500)
        awaitLauncher("the ring and the bar in the on-track green") {
            it.onTrack > STATUS_PIXELS && it.behind < STATUS_PIXELS
        }
        screenshot("day-57-2026-08-27-launcher-widgets-on-track")

        // Not only the colour: the bar prints what the plan asks for, and on day 57
        // with 95 days left that is 4.2 kg / 95 ≈ 0.044 kg a day, or 370 kcal.
        assertTrue("the bar should be asking for 370 kcal a day", onLauncher("−370 kcal / day"))
        assertEquals("the day the widgets are drawing", day(57), DayChange.today.value)
        assertTrue("the fixture is on track on day 57", !runBlocking { WidgetData.load(app) }.behind)

        // The clock, and nothing else. No app launch, no logging, no DAY_CHANGED
        // broadcast: the widgets have to notice on their own.
        clock.travelTo(day(71))

        awaitLauncher("every ring and bar turned amber, and none of it still green") {
            it.behind > STATUS_PIXELS && it.onTrack < STATUS_PIXELS
        }
        screenshot("day-71-2026-09-10-launcher-widgets-behind")

        // Fourteen fewer days to do the same 4.2 kg in, so the widget now asks for
        // 440 kcal a day rather than 370 — the arithmetic moved, not just the colour.
        assertTrue("the bar should have raised its figure to 440 kcal", onLauncher("−440 kcal / day"))
        assertEquals(
            "the day the widgets are drawing, which is what used to stay stuck",
            day(71),
            DayChange.today.value,
        )
        assertTrue("the same fixture is behind on day 71", runBlocking { WidgetData.load(app) }.behind)

        // Still bound, so what changed on the launcher was the drawing and not the
        // widgets falling off it.
        val bound = device.executeShellCommand("dumpsys appwidget")
        assertTrue("ring widget must still be bound", bound.contains("RingWidgetReceiver"))
        assertTrue("bar widget must still be bound", bound.contains("BarWidgetReceiver"))
    }

    // ------------------------------------------------------------------ helpers

    /** Home's three verdicts: the plan's weight for today, the margin, and the chip. */
    private fun assertHome(target: String, verdict: String, chip: String) {
        waitFor(verdict)
        waitFor(chip)
        waitFor(target)
    }

    private fun assertPlan(lost: String, left: String, perDay: String, pace: String) {
        waitFor("Lost so far")
        waitFor(lost)
        waitFor(left)
        waitFor(perDay)
        waitFor(pace)
    }

    /**
     * The day changing under an app that is sitting in the background — which is
     * what actually happens overnight — and the user opening it again in the
     * morning.
     */
    private fun travelAndResume(date: LocalDate) {
        backgroundApp()
        clock.travelTo(date)
        foregroundApp()
        // The app comes back on the tab it left on, and every verdict below is
        // Home's.
        tapTab("Home")
    }

    // ---- notifications ----

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
     * Wake up on [date] and wait for that morning's reminder to arrive by itself.
     *
     * The clock stops at 07:55 first and the shade is emptied there, so that the
     * notification which turns up afterwards can only be the one the alarm posted
     * on crossing 08:00 — the test never sends anything itself.
     *
     * Emptying it is not tidiness. Skipping a stretch of days leaves an alarm
     * overdue by however long the jump was, and AlarmManager delivers it on
     * arrival; that catch-up is section 9 working as designed, but it is not the
     * firing under test. Waiting for the chain to settle on this morning's alarm
     * proves the catch-up has already run, and the quiet moment after the shade is
     * cleared proves nothing is still in flight.
     */
    private fun awaitReminderOn(date: LocalDate): StatusBarNotification {
        clock.travelTo(date, BEFORE_REMINDER)
        pollUntil { armedDailyAlarm() == LocalDateTime.of(date, REMINDER_AT) }
        Reminder.dismiss(app)
        pollUntil { reminder() == null }
        SystemClock.sleep(2_000)
        assertNull("nothing may be posted before ${date}T$REMINDER_AT", reminder())

        clock.travelTo(date, AFTER_REMINDER)
        var posted: StatusBarNotification? = null
        pollUntil(timeoutMs = 30_000, intervalMs = 500) {
            posted = reminder()?.takeIf { it.actionTitles().isNotEmpty() }
            posted != null
        }
        return posted!!
    }

    /** The shade itself, since a report reader cannot see a StatusBarNotification. */
    private fun shootTheShade(name: String) {
        device.openNotification()
        SystemClock.sleep(1_200)
        screenshot(name)
        device.pressBack()
        SystemClock.sleep(400)
    }

    /**
     * When the daily alarm is next due, read out of AlarmManager rather than
     * inferred. Only a pending entry has its `tag=` line followed by the
     * `type=… origWhen=` line; the removal history repeats the tag the other way
     * round, so this cannot read a cancelled alarm as a live one.
     */
    private fun armedDailyAlarm(): LocalDateTime? {
        val dump = device.executeShellCommand("dumpsys alarm")
        val match = Regex(
            """tag=\*walarm\*:${Regex.escape(Reminder.ACTION_SHOW)}\s*\n\s*""" +
                """type=RTC_WAKEUP origWhen=(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})"""
        ).find(dump) ?: return null
        return LocalDateTime.of(
            LocalDate.parse(match.groupValues[1]),
            LocalTime.parse(match.groupValues[2]),
        )
    }

    /** The alarm-side mirror's record of the last day a reminder really landed. */
    private fun deliveredDay(): LocalDate? =
        app.getSharedPreferences("reminder", android.content.Context.MODE_PRIVATE)
            .getLong("delivered_day", Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let(LocalDate::ofEpochDay)

    // ---- the launcher's own pixels ----

    /**
     * How much of the launcher is painted in each of §6's two status colours.
     *
     * A screenshot in the report shows a reader that the widgets turned amber; this
     * is how the test sees it. Both counts come from the top of the screen, above
     * the dock and the search bar, so the wallpaper and the app icons — which carry
     * plenty of yellows of their own — cannot be mistaken for a status colour.
     */
    private data class StatusPixels(val onTrack: Int, val behind: Int) {
        override fun toString() = "on-track $onTrack px, behind $behind px"
    }

    /** Whether the launcher is showing [text] — the widget's own rendered words. */
    private fun onLauncher(text: String): Boolean =
        device.wait(Until.hasObject(By.text(text)), 10_000) == true

    private fun statusPixels(): StatusPixels {
        val palette = WidgetPalette(dark = runBlocking { WidgetData.load(app).dark }, behind = false)
        val raw = instrumentation.uiAutomation.takeScreenshot()
            ?: error("could not photograph the launcher")
        val shot = if (raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            raw
        }
        val width = shot.width
        val height = (shot.height * 0.55f).toInt()
        val pixels = IntArray(width * height)
        shot.getPixels(pixels, 0, width, 0, 0, width, height)
        var onTrack = 0
        var behind = 0
        for (pixel in pixels) {
            if (near(pixel, palette.onTrack)) onTrack++
            if (near(pixel, palette.behindColor)) behind++
        }
        return StatusPixels(onTrack, behind)
    }

    /**
     * Photographs the launcher until it shows [what], so nothing has to sleep
     * blind. It never touches the launcher, only looks at it: a press of Home
     * scrolls back to the default page, and the widgets are not on it.
     *
     * The condition has to describe the whole launcher, both colours at once.
     * Asking only "is any of it amber yet" passed on the frame where the ring had
     * repainted and the bar had not, and the green half of that frame then failed
     * the assertion that followed.
     */
    private fun awaitLauncher(what: String, settled: (StatusPixels) -> Boolean): StatusPixels {
        var last = StatusPixels(0, 0)
        try {
            pollUntil(timeoutMs = 40_000, intervalMs = 1_500) {
                last = statusPixels()
                settled(last)
            }
        } catch (timeout: IllegalStateException) {
            throw IllegalStateException("the launcher never showed $what; last read $last", timeout)
        }
        return last
    }

    private fun near(pixel: Int, colour: Int): Boolean =
        abs((pixel shr 16 and 0xFF) - (colour shr 16 and 0xFF)) <= COLOUR_TOLERANCE &&
            abs((pixel shr 8 and 0xFF) - (colour shr 8 and 0xFF)) <= COLOUR_TOLERANCE &&
            abs((pixel and 0xFF) - (colour and 0xFF)) <= COLOUR_TOLERANCE
}
