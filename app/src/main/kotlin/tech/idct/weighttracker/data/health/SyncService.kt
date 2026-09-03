package tech.idct.weighttracker.data.health

import android.content.Context
import android.util.Log
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.widget.WidgetUpdater
import java.time.LocalDate

/**
 * Section 4: autosync runs on every app open, silently. Background sync, when
 * granted, runs every half hour and updates widgets and the reminder body.
 */
class SyncService(
    private val context: Context,
    private val repo: WeightRepository,
    private val health: HealthConnectManager,
) {

    data class Result(val ran: Boolean, val imported: Int, val reason: String? = null)

    /**
     * Section 4: the optional write-back of a manual entry, on whenever the write
     * grant exists. Both ways of logging — the sheet and the notification's inline
     * field — come through here, so they cannot diverge again. Fails quietly: the
     * local database is the source of truth and already holds the entry.
     */
    suspend fun writeBack(date: LocalDate, kg: Float): Boolean = runCatching {
        if (!repo.settings().healthConnectEnabled) return@runCatching false
        health.writeWeight(date, Units.roundKg(kg))
    }.getOrElse { error ->
        Log.w("SyncService", "Health Connect write-back failed", error)
        false
    }

    suspend fun syncNow(): Result {
        val settings = repo.settings()
        if (!settings.healthConnectEnabled) return Result(false, 0, "Health Connect is off")
        if (!health.isAvailable) return Result(false, 0, "Health Connect is unavailable")
        if (!health.hasReadPermission()) return Result(false, 0, "Read permission not granted")

        // Every sync re-reads a whole year, and further back still if the plan is
        // older than that: a diet runs for months, records arrive late, and edits
        // made in other apps can touch any day of it. A fortnight used to be the
        // window after the first sync, which silently dropped anything older — and
        // the first sync with a plan never looked before the plan at all, so the
        // history a scale already held was left behind. Reading is cheap: a year
        // of weigh-ins is a few hundred rows, paged.
        val plan = repo.plan()
        val from = listOfNotNull(
            LocalDate.now().minusYears(1),
            plan?.startDate?.minusDays(1),
        ).min()

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
