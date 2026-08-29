package tech.idct.weighttracker.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.idct.weighttracker.widget.WidgetUpdater
import java.time.LocalDate
import java.time.ZoneId

/**
 * A new calendar day changes every derived number without a single row changing:
 * the plan asks for a lower weight today than yesterday, so the same last entry
 * can be ahead one evening and behind the next morning. Section 8 lists the data
 * triggers that refresh the widgets; none of them fire at midnight, so a widget
 * placed by someone who has not opened the app for a few days went on showing
 * "0.4 kg ahead" in green against a plan line that had long since moved past them.
 *
 * The alarm is inexact and does not wake the device: a widget nobody is looking at
 * can wait until the phone is next awake. Each firing arms the next midnight; the
 * chain is (re)started on every process start and after a boot or a clock change.
 */
object DayChange {

    private const val TAG = "DayChange"
    private const val ACTION = "tech.idct.weighttracker.DAY_CHANGED"

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DayChangeReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Arm, or re-arm, the refresh for a minute past the next local midnight. */
    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now().plusDays(1).atStartOfDay(zone).plusMinutes(1).toInstant().toEpochMilli()
        runCatching { alarms.set(AlarmManager.RTC, next, pending(context)) }
            .onFailure { Log.w(TAG, "Could not arm the day-change refresh", it) }
    }

    private val handler = CoroutineExceptionHandler { _, error ->
        Log.w(TAG, "Day-change refresh failed", error)
    }
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    /** Everything that shows "today" outside the app: the widgets and a posted reminder. */
    suspend fun refresh(context: Context) {
        WidgetUpdater.updateAll(context)
        Reminder.refreshIfShowing(context)
        Log.i(TAG, "Widgets and reminder refreshed for ${LocalDate.now()}")
    }
}

class DayChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending: PendingResult? = goAsync()
        DayChange.scope.launch {
            try {
                DayChange.refresh(app)
            } finally {
                DayChange.arm(app)
                pending?.finish()
            }
        }
    }
}
