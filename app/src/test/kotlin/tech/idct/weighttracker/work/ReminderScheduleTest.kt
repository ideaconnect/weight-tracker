package tech.idct.weighttracker.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Section 9: one reminder a day — and the day's reminder is not dropped by a process start. */
class ReminderScheduleTest {

    private val eight = LocalTime.of(8, 0)
    private val today = LocalDate.of(2026, 8, 29)
    private val todayAtEight = LocalDateTime.of(today, eight)

    @Test
    fun `before the time, today`() {
        val now = LocalDateTime.of(today, LocalTime.of(7, 30))
        assertEquals(todayAtEight, ReminderSchedule.nextTrigger(now, eight))
    }

    @Test
    fun `at the time exactly, tomorrow`() {
        assertEquals(todayAtEight.plusDays(1), ReminderSchedule.nextTrigger(todayAtEight, eight))
    }

    @Test
    fun `after the time with nothing armed, tomorrow`() {
        val now = LocalDateTime.of(today, LocalTime.of(8, 20))
        assertEquals(todayAtEight.plusDays(1), ReminderSchedule.nextTrigger(now, eight))
    }

    @Test
    fun `armed for today and never delivered, still today`() {
        // The inexact alarm was pending at 08:20 when the app was opened: keep it.
        val now = LocalDateTime.of(today, LocalTime.of(8, 20))
        assertEquals(
            todayAtEight,
            ReminderSchedule.nextTrigger(now, eight, armedAt = todayAtEight, deliveredDay = today.minusDays(1)),
        )
    }

    @Test
    fun `armed for today and already delivered, tomorrow`() {
        // The 08:00 alarm reached the receiver; the reschedule inside it must not re-fire.
        val now = LocalDateTime.of(today, LocalTime.of(8, 0, 1))
        assertEquals(
            todayAtEight.plusDays(1),
            ReminderSchedule.nextTrigger(now, eight, armedAt = todayAtEight, deliveredDay = today),
        )
    }

    @Test
    fun `missed by more than the grace, tomorrow`() {
        // The phone was off until 10:30: that day's reminder is gone, not late.
        val now = LocalDateTime.of(today, LocalTime.of(10, 30))
        assertEquals(
            todayAtEight.plusDays(1),
            ReminderSchedule.nextTrigger(now, eight, armedAt = todayAtEight, deliveredDay = null),
        )
    }

    @Test
    fun `armed for a different time today, the new time tomorrow`() {
        // The user moved the reminder from 09:00 to 08:00 at 08:20: nothing was
        // missed, so it must not fire right away.
        val now = LocalDateTime.of(today, LocalTime.of(8, 20))
        val armedForNine = LocalDateTime.of(today, LocalTime.of(9, 0))
        assertEquals(
            todayAtEight.plusDays(1),
            ReminderSchedule.nextTrigger(now, eight, armedAt = armedForNine, deliveredDay = null),
        )
    }

    @Test
    fun `a late-evening reminder missed by an hour is still delivered after midnight`() {
        // 23:30 reminder, phone off from 23:00, back at 00:30: yesterday's is due.
        val halfPastEleven = LocalTime.of(23, 30)
        val yesterdayAt = LocalDateTime.of(today.minusDays(1), halfPastEleven)
        val now = LocalDateTime.of(today, LocalTime.of(0, 30))
        assertEquals(
            yesterdayAt,
            ReminderSchedule.nextTrigger(now, halfPastEleven, armedAt = yesterdayAt, deliveredDay = today.minusDays(2)),
        )
        // ...but not once it was delivered, and not after the grace has run out.
        assertEquals(
            LocalDateTime.of(today, halfPastEleven),
            ReminderSchedule.nextTrigger(now, halfPastEleven, armedAt = yesterdayAt, deliveredDay = today.minusDays(1)),
        )
        assertEquals(
            LocalDateTime.of(today, halfPastEleven),
            ReminderSchedule.nextTrigger(
                LocalDateTime.of(today, LocalTime.of(1, 45)), halfPastEleven, armedAt = yesterdayAt, deliveredDay = null,
            ),
        )
    }

    @Test
    fun `armed for yesterday, tomorrow`() {
        val now = LocalDateTime.of(today, LocalTime.of(8, 20))
        assertEquals(
            todayAtEight.plusDays(1),
            ReminderSchedule.nextTrigger(now, eight, armedAt = todayAtEight.minusDays(1), deliveredDay = null),
        )
    }
}
