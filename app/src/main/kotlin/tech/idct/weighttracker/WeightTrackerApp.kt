package tech.idct.weighttracker

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.ads.Ads
import tech.idct.weighttracker.data.repo.ThemePrefs
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.widget.WidgetUpdater
import tech.idct.weighttracker.work.DayChange
import tech.idct.weighttracker.work.HealthSyncWorker
import tech.idct.weighttracker.work.PlanRefreshWorker
import tech.idct.weighttracker.work.Reminder

class WeightTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Reminder.ensureChannel(this)
        Ads.initialise(this)

        // Bring the scheduled work back in line with whatever the settings say, and
        // refresh the widgets so a cold start never leaves them stale. Logged, not
        // fatal: an unhandled exception here would kill a process that may have
        // been started only to deliver the reminder.
        val handler = CoroutineExceptionHandler { _, error ->
            Log.w("WeightTrackerApp", "Startup reconciliation failed", error)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler).launch {
            val settings = WeightRepository.get(this@WeightTrackerApp).settings()
            ThemePrefs.write(this@WeightTrackerApp, settings.theme)
            if (settings.backgroundSyncEnabled) {
                HealthSyncWorker.enable(this@WeightTrackerApp)
            } else {
                HealthSyncWorker.cancel(this@WeightTrackerApp)
            }
            // Needs no grant of any kind, so it is not behind a setting: what it
            // refreshes is the app's own arithmetic against today's date.
            PlanRefreshWorker.enable(this@WeightTrackerApp)
            Reminder.reschedule(this@WeightTrackerApp)
            DayChange.arm(this@WeightTrackerApp)
            WidgetUpdater.updateAll(this@WeightTrackerApp)
        }
    }
}
