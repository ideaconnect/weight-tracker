package tech.idct.weighttracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tech.idct.weighttracker.data.health.HealthConnectManager
import tech.idct.weighttracker.data.health.SyncService
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Section 4 rule 6: background sync, when granted, and what updates the widgets and
 * the reminder body while the app is closed — the latter by rebuilding a reminder
 * that is already in the shade, and by being run again just before one is posted
 * (see [Reminder.syncBeforePosting]). Without the grant, widgets refresh on app
 * open, on manual save and on plan change.
 *
 * Every half hour, not once a day. A weight a scale wrote at seven in the morning
 * that a widget picks up tomorrow has, to the person looking at the widget, not
 * synced at all: they weigh themselves, glance at the home screen, see yesterday's
 * figure, and open the app — which is the very thing background sync exists to make
 * unnecessary. Half an hour is close to the shortest a periodic WorkManager job may
 * be (fifteen minutes is the floor, and doze stretches either), and the work itself
 * is small: a few hundred rows read over IPC and a merge that usually writes nothing.
 */
class HealthSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = WeightRepository.get(applicationContext)
        val settings = repo.settings()
        if (!settings.backgroundSyncEnabled) {
            // The grant was withdrawn since this job was queued; stop rescheduling.
            cancel(applicationContext)
            return Result.success()
        }
        val health = HealthConnectManager(applicationContext)
        SyncService(applicationContext, repo, health).syncNow()
        WidgetUpdater.updateAll(applicationContext)
        Reminder.refreshIfShowing(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "health-sync"

        /**
         * The daily job this replaced. Unique work is addressed by name, so a rename
         * alone would leave the old one running its own schedule beside the new one
         * for the life of the install; it is cancelled by name wherever this one is
         * touched.
         */
        private const val LEGACY_NAME = "daily-health-sync"

        /** Minutes between background syncs. */
        const val INTERVAL_MINUTES = 30L

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            )
                // Well under the period: the job is re-asserted on every process
                // start, so a delay of a whole period could be pushed out for ever
                // on a phone that is picked up often.
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(LEGACY_NAME)
                enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(LEGACY_NAME)
                cancelUniqueWork(NAME)
            }
        }
    }
}
