package tech.idct.weighttracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.ads.Ads
import tech.idct.weighttracker.data.repo.ThemePrefs
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.widget.WidgetUpdater
import tech.idct.weighttracker.work.DailySyncWorker
import tech.idct.weighttracker.work.Reminder

class WeightTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Reminder.ensureChannel(this)
        Ads.initialise(this)

        // Bring the scheduled work back in line with whatever the settings say, and
        // refresh the widgets so a cold start never leaves them stale.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = WeightRepository.get(this@WeightTrackerApp).settings()
            ThemePrefs.write(this@WeightTrackerApp, settings.theme)
            if (settings.backgroundSyncEnabled) {
                DailySyncWorker.enable(this@WeightTrackerApp)
            } else {
                DailySyncWorker.cancel(this@WeightTrackerApp)
            }
            Reminder.reschedule(this@WeightTrackerApp)
            WidgetUpdater.updateAll(this@WeightTrackerApp)
        }
    }
}
