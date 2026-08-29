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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import tech.idct.weighttracker.MainActivity
import tech.idct.weighttracker.R
import tech.idct.weighttracker.data.health.HealthConnectManager
import tech.idct.weighttracker.data.health.SyncService
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Section 9: one daily notification at a user-set time, off by default until
 * enabled. The body carries real numbers.
 *
 * The alarm is a chain: each firing arms the next day's, so the order inside the
 * receiver matters — tomorrow is armed first, and only then is today's posted.
 * Everything that arms an alarm is awaited before the broadcast is finished,
 * because a process that has finished its broadcast can be killed at any moment.
 */
object Reminder {

    private const val TAG = "Reminder"

    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 4201
    const val KEY_WEIGHT_INPUT = "weight_input"

    const val ACTION_SHOW = "tech.idct.weighttracker.SHOW_REMINDER"
    const val ACTION_SHOW_SNOOZED = "tech.idct.weighttracker.SHOW_SNOOZED_REMINDER"
    const val ACTION_LOG = "tech.idct.weighttracker.LOG_FROM_NOTIFICATION"
    const val ACTION_SNOOZE = "tech.idct.weighttracker.SNOOZE_REMINDER"

    /**
     * The activity intent's own action. PendingIntent identity ignores extras, so
     * without it the "Open app" intent was the same PendingIntent as the 4×2 bar
     * widget's pin callback (both request code 1), and whichever was created last
     * decided where the other one went.
     */
    const val ACTION_OPEN = "tech.idct.weighttracker.OPEN_FROM_REMINDER"

    // Request codes. The daily alarm and the snooze MUST differ: AlarmManager
    // replaces an alarm whose PendingIntent matches, and they used to share one, so
    // any process start — a widget refresh, the sync job, opening the app — quietly
    // replaced a pending snooze with tomorrow's alarm.
    private const val RC_DAILY = 0
    private const val RC_OPEN_LOG = 1
    private const val RC_SNOOZE = 2
    private const val RC_LOG = 3
    private const val RC_SNOOZED_SHOW = 4
    private const val RC_OPEN_HOME = 5

    /** The Health Connect sync before posting is best effort; the receiver has ~10 s. */
    private const val SYNC_BUDGET_MS = 6_000L

    // One supervised scope for everything the reminder does off the main thread. An
    // exception in a bare CoroutineScope(Dispatchers.IO).launch used to reach the
    // thread's uncaught handler and kill the process — from the background, silently,
    // with tomorrow's alarm not yet armed.
    private val handler = CoroutineExceptionHandler { _, error ->
        Log.w(TAG, "Reminder work failed", error)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    internal fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

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

    // ---- what Android will let us do -----------------------------------------

    /**
     * Whether a posted reminder would actually reach the shade: the runtime
     * permission (Android 13+), the app-level switch, and the channel not muted.
     * Settings shows "On" only while this is true; before, the switch stayed on
     * for months after the user had blocked notifications, and every morning the
     * alarm woke the process to post nothing.
     */
    fun canDeliver(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return false
        val channel = compat.getNotificationChannel(CHANNEL_ID) ?: return true
        return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /**
     * Android 12+ gates exact alarms behind "Alarms & reminders", which Android 14
     * denies by default. Without it the reminder still arrives, within the hour
     * after the chosen time rather than on the minute; the Reminder screen says so
     * and offers the settings page.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    /** Whether the reminder is in the shade right now. */
    fun isShowing(context: Context): Boolean = runCatching {
        NotificationManagerCompat.from(context).activeNotifications.any { it.id == NOTIFICATION_ID }
    }.getOrDefault(false)

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Rebuild a reminder that is already showing, so a settings change — the unit,
     * the quick-log switch — or a background sync is reflected in the shade rather
     * than tomorrow.
     */
    suspend fun refreshIfShowing(context: Context) {
        if (isShowing(context)) post(context)
    }

    // ---- intents ---------------------------------------------------------------

    private fun broadcast(context: Context, requestCode: Int, action: String, mutable: Boolean = false) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE),
        )

    private fun dailyIntent(context: Context) = broadcast(context, RC_DAILY, ACTION_SHOW)

    private fun snoozedIntent(context: Context) = broadcast(context, RC_SNOOZED_SHOW, ACTION_SHOW_SNOOZED)

    /**
     * Opens the app at [route]. SINGLE_TOP together with the activity's singleTop
     * launch mode delivers the route to the running instance through onNewIntent;
     * without both, a tap while the app was in the background stacked a second
     * MainActivity — and a second view model, billing client and session — on top
     * of the first.
     */
    private fun openIntent(context: Context, requestCode: Int, route: String) = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            .putExtra(MainActivity.EXTRA_ROUTE, route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ---- scheduling ------------------------------------------------------------

    /**
     * Arm, or cancel, the daily alarm to match the settings — awaited. One caller at
     * a time: Application.onCreate and a receiver can both ask on the same cold
     * start, and the alarm and its mirror must be written as one step.
     */
    suspend fun rescheduleNow(context: Context) = rescheduleLock.withLock { rescheduleLocked(context) }

    private val rescheduleLock = Mutex()

    private suspend fun rescheduleLocked(context: Context) {
        val settings = runCatching { WeightRepository.get(context).settings() }
            .getOrElse { error ->
                Log.w(TAG, "Could not read settings; leaving the alarm as it is", error)
                return
            }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val daily = dailyIntent(context)
        if (!settings.reminderEnabled) {
            alarms.cancel(daily)
            alarms.cancel(snoozedIntent(context))
            // Cancelling the alarm left an already-posted notification in the shade,
            // still showing the numbers from before the switch was turned off — or
            // from before Delete-all-data wiped them.
            dismiss(context)
            ReminderPrefs.clear(context)
            return
        }
        val zone = ZoneId.systemDefault()
        val trigger = ReminderSchedule.nextTrigger(
            now = LocalDateTime.now(),
            time = settings.reminderTime,
            armedAt = ReminderPrefs.armedAt(context, zone),
            deliveredDay = ReminderPrefs.deliveredDay(context),
        )
        val triggerAt = trigger.atZone(zone).toInstant().toEpochMilli()
        arm(context, alarms, triggerAt, daily)
        ReminderPrefs.setArmedAt(context, triggerAt)
    }

    /** The fire-and-forget form, for call sites that are not already suspended. */
    fun reschedule(context: Context) {
        scope.launch { rescheduleNow(context) }
    }

    private fun arm(context: Context, alarms: AlarmManager, triggerAt: Long, pending: PendingIntent) {
        val exact = runCatching {
            if (!canScheduleExact(context)) return@runCatching false
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            true
        }.getOrDefault(false)
        if (!exact) {
            // Without the exact-alarm grant the alarm is inexact and allow-while-idle:
            // Android delivers it within the hour after the chosen time, and still
            // in Doze. (setWindow would be tighter but is not allow-while-idle, and a
            // phone that sat idle all night would hold it until the next maintenance
            // window — later, not earlier.)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Snooze has its own alarm, so the daily one is left exactly as it was. */
    fun snooze(context: Context, hours: Long = 1) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + hours * 60 * 60 * 1000
        arm(context, alarms, triggerAt, snoozedIntent(context))
    }

    /** Called the moment a show alarm reaches the receiver, before anything can fail. */
    internal fun markDelivered(context: Context) {
        ReminderPrefs.setDeliveredDay(context, LocalDate.now())
    }

    // ---- content ---------------------------------------------------------------

    /** A 21:00 reminder is not a morning weigh-in. */
    fun title(at: LocalTime): String = when (at.hour) {
        in 0..11 -> "Morning weigh-in"
        in 12..17 -> "Afternoon weigh-in"
        else -> "Evening weigh-in"
    }

    /**
     * Section 4 rule 6: with background sync granted, the reminder shows this
     * morning's weight — so it asks Health Connect first, briefly and best effort.
     * Nothing here may delay or prevent the notification.
     */
    suspend fun syncBeforePosting(context: Context) {
        val repo = WeightRepository.get(context)
        val settings = repo.settings()
        if (!settings.healthConnectEnabled || !settings.backgroundSyncEnabled) return
        withTimeoutOrNull(SYNC_BUDGET_MS) {
            runCatching { SyncService(context, repo, HealthConnectManager(context)).syncNow() }
                .onFailure { Log.w(TAG, "Sync before the reminder failed", it) }
        }
    }

    /**
     * Post the reminder with today's numbers. [note] is prepended when an inline
     * reply could not be saved, with the rejected text kept visible as the reply
     * history so the user sees what was refused rather than an unchanged card.
     */
    suspend fun post(context: Context, note: String? = null, rejectedInput: String? = null) {
        ensureChannel(context)
        if (!canDeliver(context)) {
            Log.w(TAG, "Notifications are blocked; reminder not posted")
            return
        }

        val repo = WeightRepository.get(context)
        val settings = repo.settings()
        if (!settings.reminderEnabled) {
            Log.w(TAG, "Reminder is off; nothing posted")
            return
        }
        val entries = repo.entries()
        val plan = repo.plan()
        val today = LocalDate.now()
        val body = Format.reminderBody(entries, plan, settings.unit, today)
        val text = listOfNotNull(note, body).joinToString(" ")
        val lastKnown = entries.lastOrNull()?.kg
        val loggedToday = entries.any { it.date == today }

        val openLog = openIntent(context, RC_OPEN_LOG, MainActivity.ROUTE_LOG)
        val snoozePending = broadcast(context, RC_SNOOZE, ACTION_SNOOZE)

        // Titled by the scheduled time, as the preview is: a card rebuilt at 14:30 by
        // a unit change or the sync must not turn into an "Afternoon weigh-in".
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(settings.reminderTime))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openLog)
        if (rejectedInput != null) builder.setRemoteInputHistory(arrayOf(rejectedInput))

        // With quick logging on, an inline input — its hint the last known weight —
        // writes the entry without opening the app. Not once today is logged: the
        // field would only replace the day's entry by accident.
        if (settings.quickLogFromNotification && !loggedToday) {
            val remoteInput = RemoteInput.Builder(KEY_WEIGHT_INPUT)
                .setLabel(
                    lastKnown?.let { Units.formatWithUnit(it, settings.unit) } ?: settings.unit.label
                )
                .build()
            val logPending = broadcast(context, RC_LOG, ACTION_LOG, mutable = true)
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notification, "Log", logPending)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .build()
            )
        }
        builder.addAction(R.drawable.ic_notification, "Open app", openLog)
        builder.addAction(R.drawable.ic_notification, "Snooze 1h", snoozePending)

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { Log.w(TAG, "Could not post the daily reminder", it) }
    }

    /**
     * What an inline reply gets back. Posting under the same id is what stops the
     * shade's sending spinner; simply cancelling left it spinning on some builds,
     * and gave no sign the number had been saved. It clears on tap, and the next
     * reminder replaces it. (No platform timeout: before Android 15 a timeout set
     * on this id outlived the next post under it and took that one down too.)
     */
    fun postLogged(context: Context, kg: Float, unit: WeightUnit, date: LocalDate) {
        if (!canDeliver(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Logged ${Units.formatWithUnit(kg, unit)}")
            .setContentText("Saved to ${date.format(Format.isoDate)}.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, RC_OPEN_HOME, MainActivity.ROUTE_HOME))
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { Log.w(TAG, "Could not post the confirmation", it) }
    }

    // ---- the inline reply ------------------------------------------------------

    internal suspend fun handleInlineLog(context: Context, intent: Intent) {
        val raw = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_WEIGHT_INPUT)
            ?.toString()
        val repo = WeightRepository.get(context)
        val settings = repo.settings()
        // A notification that outlived the setting (or the database it was built
        // from) must not write into it.
        if (!settings.reminderEnabled) {
            dismiss(context)
            return
        }
        // The field outlived the quick-log switch: rebuild the card without it.
        if (!settings.quickLogFromNotification) {
            post(context)
            return
        }
        val typed = raw?.let(Units::parseDisplayWeight)
        if (typed == null || !Units.isPlausible(typed, settings.unit)) {
            post(context, note = Format.rejectedWeight(settings.unit), rejectedInput = raw)
            return
        }
        val today = LocalDate.now()
        val kg = Units.fromDisplay(typed, settings.unit)
        repo.saveManualEntry(today, kg)
        postLogged(context, Units.roundKg(kg), settings.unit, today)
        // Section 4: the same optional write-back the in-app log sheet does.
        SyncService(context, repo, HealthConnectManager(context)).writeBack(today, kg)
        WidgetUpdater.updateAll(context)
        // The view model's auto-backup only runs while the app is alive.
        BackupWorker.enqueue(context)
    }
}

/** The alarm-side mirror: two facts [ReminderSchedule] needs besides the clock. */
private object ReminderPrefs {

    private const val FILE = "reminder"
    private const val KEY_ARMED_AT = "armed_at"
    private const val KEY_DELIVERED_DAY = "delivered_day"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun armedAt(context: Context, zone: ZoneId): LocalDateTime? =
        prefs(context).getLong(KEY_ARMED_AT, -1L).takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }

    fun setArmedAt(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_ARMED_AT, millis).apply()
    }

    fun deliveredDay(context: Context): LocalDate? =
        prefs(context).getLong(KEY_DELIVERED_DAY, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let(LocalDate::ofEpochDay)

    fun setDeliveredDay(context: Context, day: LocalDate) {
        // Synchronous on purpose: the receiver records this before any coroutine
        // — including Application.onCreate's reschedule — can read it.
        prefs(context).edit().putLong(KEY_DELIVERED_DAY, day.toEpochDay()).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        // Only the daily alarm counts as delivered: the mirror answers "did the alarm
        // armed for today reach us?", and a snooze that fires after midnight is last
        // night's card, not today's alarm — marking it would let the next process
        // start move today's still-pending alarm to tomorrow.
        if (action == null || action == Reminder.ACTION_SHOW) Reminder.markDelivered(app)
        // goAsync() is only non-null when the system dispatched us; a direct call
        // from a test has nothing to finish.
        val pending: PendingResult? = goAsync()
        Reminder.launch {
            try {
                when (action) {
                    Reminder.ACTION_LOG -> Reminder.handleInlineLog(app, intent)

                    Reminder.ACTION_SNOOZE -> {
                        Reminder.dismiss(app)
                        Reminder.snooze(app)
                    }

                    else -> {
                        // Tomorrow first: if posting fails, the chain must not.
                        Reminder.rescheduleNow(app)
                        Reminder.syncBeforePosting(app)
                        Reminder.post(app)
                    }
                }
            } catch (error: Throwable) {
                Log.w("Reminder", "Reminder broadcast failed", error)
            } finally {
                pending?.finish()
            }
        }
    }
}

/**
 * Boot, package replace, time-zone change, and the exact-alarm grant arriving:
 * bring the alarm back in line with the settings. The daily sync job is not
 * touched here — WorkManager keeps it across reboots on its own, and
 * Application.onCreate reconciles it with the setting on the same start.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending: PendingResult? = goAsync()
        Reminder.launch {
            try {
                Reminder.rescheduleNow(app)
            } finally {
                pending?.finish()
            }
        }
    }
}
