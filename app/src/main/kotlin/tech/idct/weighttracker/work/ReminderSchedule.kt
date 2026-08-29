package tech.idct.weighttracker.work

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When the daily reminder should next fire — pure, so it can be pinned in a JVM
 * test. Section 9: one notification a day at the user's time.
 *
 * The decision needs two facts besides the clock, because the clock alone
 * dropped reminders: an inexact alarm that Android was still holding at 08:20
 * was moved to tomorrow by whatever started the process next, and a phone that
 * was off at 08:00 never got that day's reminder at all.
 *
 * - [armedAt] is the trigger the alarm was last set for.
 * - [deliveredDay] is the last day the alarm actually reached the receiver.
 *
 * If the most recently due occurrence was armed and never delivered, and the time
 * is still within [grace] of it, the answer is that occurrence again — in the
 * past, which makes AlarmManager fire it at once. Otherwise the next one. "Most
 * recently due" is yesterday's when the time has not come round yet today, so a
 * 23:30 reminder missed by an hour is still delivered at 00:30.
 */
object ReminderSchedule {

    val defaultGrace: Duration = Duration.ofHours(2)

    fun nextTrigger(
        now: LocalDateTime,
        time: LocalTime,
        armedAt: LocalDateTime? = null,
        deliveredDay: LocalDate? = null,
        grace: Duration = defaultGrace,
    ): LocalDateTime {
        val todayAt = LocalDateTime.of(now.toLocalDate(), time)
        val lastDue = if (now.isBefore(todayAt)) todayAt.minusDays(1) else todayAt
        val missed = armedAt == lastDue &&
            deliveredDay != lastDue.toLocalDate() &&
            now.isBefore(lastDue.plus(grace))
        if (missed) return lastDue
        return if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
    }
}
