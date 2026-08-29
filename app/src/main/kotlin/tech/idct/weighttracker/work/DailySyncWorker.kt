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
 * Section 4 rule 6: background sync, when granted, runs once a day and updates
 * widgets and the reminder body — the latter by rebuilding a reminder that is
 * already in the shade, and by being run again just before one is posted (see
 * [Reminder.syncBeforePosting]). Without it, widgets refresh on app open, on
 * manual save and on plan change.
 */
class DailySyncWorker(
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
        private const val NAME = "daily-health-sync"

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
