package tech.idct.weighttracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The hourly beat behind everything the app derives rather than stores: ahead or
 * behind, the projected finish, the percentage, the status colour. None of it is a
 * row in the database — it is [tech.idct.weighttracker.domain.PlanMath] read against
 * the current date — so none of the section 8 data triggers fire when it changes.
 *
 * [DayChange] arms a single alarm for a minute past midnight to carry that over, and
 * that alarm is the whole of it: miss one — the phone was in doze, the process was
 * force-stopped, a vendor battery policy cleared the pending intent — and a widget
 * goes on showing yesterday's verdict against yesterday's plan line until somebody
 * opens the app. An hour is a cheap heartbeat for a Room read and a redraw, it puts
 * a wrong widget right within the hour instead of within the day, and each run
 * re-arms the midnight alarm, so the chain repairs itself rather than staying broken.
 *
 * It needs no permission and no grant, so unlike [HealthSyncWorker] it runs for
 * everyone: what it refreshes is the app's own arithmetic, not Health Connect's data.
 */
class PlanRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DayChange.refresh(applicationContext)
        DayChange.arm(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "plan-refresh"

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlanRefreshWorker>(1, TimeUnit.HOURS)
                // Short enough that a phone which is picked up often cannot keep
                // pushing the first run out ahead of itself.
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
