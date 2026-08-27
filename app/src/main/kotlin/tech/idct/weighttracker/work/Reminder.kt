package tech.idct.weighttracker.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.idct.weighttracker.MainActivity
import tech.idct.weighttracker.R
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.widget.WidgetUpdater
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Section 9: one daily notification at a user-set time, off by default until
 * enabled. The body carries real numbers.
 */
object Reminder {

    private const val TAG = "Reminder"

    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 4201
    const val KEY_WEIGHT_INPUT = "weight_input"

    const val ACTION_SHOW = "tech.idct.weighttracker.SHOW_REMINDER"
    const val ACTION_LOG = "tech.idct.weighttracker.LOG_FROM_NOTIFICATION"
    const val ACTION_SNOOZE = "tech.idct.weighttracker.SNOOZE_REMINDER"

    /** Notification channel: low importance, no sound by default. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A single daily nudge to weigh in."
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun showIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_SHOW),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Schedule, or cancel, the daily alarm to match the current settings. */
    fun reschedule(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = WeightRepository.get(context).settings()
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return@launch
            val pending = showIntent(context)
            if (!settings.reminderEnabled) {
                alarms.cancel(pending)
                return@launch
            }
            val zone = ZoneId.systemDefault()
            var next = LocalDateTime.of(LocalDate.now(), settings.reminderTime)
            if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
            val triggerAt = next.atZone(zone).toInstant().toEpochMilli()

            val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // Without the exact-alarm grant an inexact daily alarm is still honest:
                // the reminder arrives around the chosen time rather than on the second.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun snooze(context: Context, hours: Long = 1) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + hours * 60 * 60 * 1000
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, showIntent(context))
    }

    /** The body carries how far ahead or behind the plan the user is, and yesterday's weight. */
    suspend fun buildBody(context: Context): Pair<String, Float?> {
        val repo = WeightRepository.get(context)
        val entries = repo.entries()
        val plan = repo.plan()
        val settings = repo.settings()
        val yesterday = entries.lastOrNull { it.date < LocalDate.now() }
        val lastKnown = entries.lastOrNull()?.kg

        val parts = mutableListOf<String>()
        if (plan != null) {
            val stats = PlanMath.stats(plan, entries, LocalDate.now())
            if (stats.dated) {
                val amount = Units.formatWithUnit(abs(stats.aheadKg), settings.unit)
                parts += if (stats.aheadKg >= 0) {
                    "You're $amount ahead of plan."
                } else {
                    "You're $amount behind plan."
                }
            } else {
                parts += "${Units.formatWithUnit(stats.leftKg, settings.unit)} to your goal."
            }
        }
        if (yesterday != null) {
            parts += "Yesterday you were ${Units.formatWithUnit(yesterday.kg, settings.unit)}."
        }
        if (parts.isEmpty()) parts += "Log today's weight to start the chart."
        return parts.joinToString(" ") to lastKnown
    }

    suspend fun post(context: Context) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val repo = WeightRepository.get(context)
        val settings = repo.settings()
        if (!settings.reminderEnabled) {
            Log.i(TAG, "Reminder is off; nothing posted")
            return
        }

        val (body, lastKnown) = buildBody(context)

        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_LOG),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePending = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Morning weigh-in")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openApp)

        // With quick logging on, an inline input pre-filled with the last known weight
        // writes the entry without opening the app.
        if (settings.quickLogFromNotification) {
            val remoteInput = RemoteInput.Builder(KEY_WEIGHT_INPUT)
                .setLabel(
                    lastKnown?.let { Units.formatWithUnit(it, settings.unit) } ?: settings.unit.label
                )
                .build()
            val logPending = PendingIntent.getBroadcast(
                context,
                3,
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_LOG),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notification, "Log", logPending)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .build()
            )
        }
        builder.addAction(R.drawable.ic_notification, "Open app", openApp)
        builder.addAction(R.drawable.ic_notification, "Snooze 1h", snoozePending)

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { Log.w(TAG, "Could not post the daily reminder", it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Reminder.ACTION_LOG -> {
                        val input = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(Reminder.KEY_WEIGHT_INPUT)
                            ?.toString()
                            ?.trim()
                            ?.replace(',', '.')
                        val repo = WeightRepository.get(appContext)
                        val settings = repo.settings()
                        val typed = input?.filter { it.isDigit() || it == '.' }?.toFloatOrNull()
                        if (typed != null && Units.isPlausible(typed, settings.unit)) {
                            repo.saveManualEntry(
                                LocalDate.now(),
                                Units.fromDisplay(typed, settings.unit),
                            )
                            WidgetUpdater.updateAll(appContext)
                            NotificationManagerCompat.from(appContext).cancel(Reminder.NOTIFICATION_ID)
                        } else {
                            // Leave the notification up rather than silently dropping the input.
                            Reminder.post(appContext)
                        }
                        Reminder.reschedule(appContext)
                    }

                    Reminder.ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(appContext).cancel(Reminder.NOTIFICATION_ID)
                        Reminder.snooze(appContext)
                    }

                    else -> {
                        Reminder.post(appContext)
                        Reminder.reschedule(appContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.reschedule(context.applicationContext)
        DailySyncWorker.reschedule(context.applicationContext)
    }
}
