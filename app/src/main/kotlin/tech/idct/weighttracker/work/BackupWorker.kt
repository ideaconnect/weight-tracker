package tech.idct.weighttracker.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tech.idct.weighttracker.data.account.BackupService
import tech.idct.weighttracker.data.account.SupabaseAuth
import tech.idct.weighttracker.data.account.SupabaseClient
import tech.idct.weighttracker.data.repo.WeightRepository

/**
 * Section 11: uploads happen automatically while backup is on. The view model's
 * watcher does that whenever the app is alive; an entry logged from the
 * notification with the app dead has nobody to upload it, so this does — once
 * the network is there, off the receiver's clock.
 *
 * A conflict is left alone: it needs the user's decision, which the Account
 * screen asks for on the next open.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val repo = WeightRepository.get(app)
        if (!repo.settings().backupEnabled) return Result.success()
        val client = SupabaseClient(app)
        val auth = SupabaseAuth(app, client)
        if (auth.session.value == null) return Result.success()
        return when (BackupService(app, client, auth, repo).backupNow()) {
            is BackupService.Result.Error -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "backup-after-inline-log"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
