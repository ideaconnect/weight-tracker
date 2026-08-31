package tech.idct.weighttracker.e2e

import android.os.SystemClock
import androidx.test.uiautomator.UiDevice
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * The emulator's wall clock, moved by hand.
 *
 * Nearly everything this app says is a function of the calendar rather than of
 * the data: the plan asks for a lower weight today than yesterday, so the same
 * last entry reads "0.4 kg ahead" one morning and "0.3 kg behind" a fortnight
 * later without a single row changing. A test that only ever sees one date
 * cannot see any of that, so these scenarios move the device's own clock and
 * then watch the app, the notification and the widgets follow it.
 *
 * `cmd alarm set-time` is the whole trick. It reaches AlarmManagerService as the
 * shell user, which holds SET_TIME, so it needs no root — `adb root` is refused
 * on the Play-image emulators this suite runs on — and, unlike writing to
 * /dev/alarm, it goes through the same path a user setting the clock in Settings
 * would: the service rebatches its alarms and the system broadcasts TIME_SET.
 * That is exactly what the app is listening for (BootReceiver), so nothing here
 * has to simulate the app's reaction; it only has to move the clock.
 *
 * Two rules matter:
 *
 *  - auto_time is turned off first, or NTP pulls the clock back to real time in
 *    the middle of a scenario.
 *  - the real time is remembered as an offset from [SystemClock.elapsedRealtime],
 *    which no clock change touches, so [release] can put the emulator back
 *    however far the scenario travelled. Every scenario restores the clock in an
 *    `@After`, and `e2e/run.py` restores it again between scenarios in case the
 *    instrumentation process died holding it.
 */
class DeviceClock(private val device: UiDevice) {

    private var baseWall = 0L
    private var baseElapsed = 0L
    private var held = false

    /** Whether this scenario has taken the clock over. */
    val travelling: Boolean get() = held

    /** Stop the network setting the clock, and remember where real time was. */
    fun hold() {
        if (held) return
        device.executeShellCommand("settings put global auto_time 0")
        baseWall = System.currentTimeMillis()
        baseElapsed = SystemClock.elapsedRealtime()
        held = true
    }

    /** Give the emulator its real time back. Safe to call when it was never taken. */
    fun release() {
        if (!held) return
        held = false
        // elapsedRealtime is monotonic and a clock jump does not touch it, so this
        // is what the time really is now, however many weeks the scenario travelled.
        setMillis(baseWall + (SystemClock.elapsedRealtime() - baseElapsed))
        device.executeShellCommand("settings put global auto_time 1")
    }

    /** Move to [time] on [date], local time. Returns the date, to read as a statement. */
    fun travelTo(date: LocalDate, time: LocalTime = MORNING): LocalDate {
        hold()
        setMillis(LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli())
        check(LocalDate.now() == date) { "asked for $date, the device says ${LocalDate.now()}" }
        return date
    }

    /** The same day, later on: for stepping over a reminder's own time. */
    fun travelTo(time: LocalTime): LocalDate = travelTo(LocalDate.now(), time)

    fun advance(days: Long, time: LocalTime = MORNING): LocalDate =
        travelTo(LocalDate.now().plusDays(days), time)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun setMillis(millis: Long) {
        val out = device.executeShellCommand("cmd alarm set-time $millis")
        check(out.isBlank()) { "cmd alarm set-time refused the clock: ${out.trim()}" }
        // The service applies it on its own thread; the tolerance is wide because
        // real seconds keep passing while we wait for the new time to land.
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (abs(System.currentTimeMillis() - millis) < 120_000) return
            SystemClock.sleep(200)
        }
        error("the device clock never moved to $millis (it says ${System.currentTimeMillis()})")
    }

    private companion object {
        /** Late enough that "today" is unambiguous in every time zone the CI might use. */
        val MORNING: LocalTime = LocalTime.of(9, 0)
    }
}
