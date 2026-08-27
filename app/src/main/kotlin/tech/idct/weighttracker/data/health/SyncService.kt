package tech.idct.weighttracker.data.health

import android.content.Context
import android.util.Log
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.widget.WidgetUpdater
import java.time.LocalDate

/**
 * Section 4: autosync runs on every app open, silently. Background sync, when
 * granted, runs once a day and updates widgets and the reminder body.
 */
class SyncService(
    private val context: Context,
    private val repo: WeightRepository,
    private val health: HealthConnectManager,
) {

    data class Result(val ran: Boolean, val imported: Int, val reason: String? = null)

    suspend fun syncNow(): Result {
        val settings = repo.settings()
        if (!settings.healthConnectEnabled) return Result(false, 0, "Health Connect is off")
        if (!health.isAvailable) return Result(false, 0, "Health Connect is unavailable")
        if (!health.hasReadPermission()) return Result(false, 0, "Read permission not granted")

        // Re-read a fortnight either side of the last sync so late-arriving records
        // and edits made in other apps still land. Reading is cheap; missing a day
        // is not.
        val plan = repo.plan()
        val from = when {
            settings.lastSyncAt != null -> LocalDate.now().minusDays(14)
            plan != null -> plan.startDate.minusDays(1)
            else -> LocalDate.now().minusYears(1)
        }

        return runCatching {
            val incoming = health.readWeights(from, LocalDate.now())
            val written = repo.mergeHealthConnectEntries(incoming)
            repo.updateSettings { it.copy(lastSyncAt = System.currentTimeMillis()) }
            WidgetUpdater.updateAll(context)
            Result(true, written)
        }.getOrElse { error ->
            Log.w("SyncService", "Health Connect sync failed", error)
            Result(false, 0, error.message ?: "Sync failed")
        }
    }
}
