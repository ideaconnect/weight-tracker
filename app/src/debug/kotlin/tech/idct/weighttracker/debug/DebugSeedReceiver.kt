package tech.idct.weighttracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.db.AppDatabase
import tech.idct.weighttracker.data.health.HealthConnectManager
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.widget.WidgetUpdater
import java.time.LocalDate
import kotlin.math.sin

/**
 * Debug-only fixture loader, so the screens can be exercised without tapping a
 * couple of months of weights in by hand. Never compiled into a release build.
 *
 *   adb shell am broadcast -a tech.idct.weighttracker.debug.SEED \
 *     -n tech.idct.weighttracker.debug/tech.idct.weighttracker.debug.DebugSeedReceiver
 *
 * Extras: --ez behind true (seed a plan the user is behind on)
 *         --ez unlock true (grant the widget entitlement)
 *         --ez clear  true (wipe everything instead of seeding)
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val behind = intent.getBooleanExtra("behind", false)
        val unlock = intent.getBooleanExtra("unlock", false)
        val clear = intent.getBooleanExtra("clear", false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = WeightRepository.get(app)

                // Write records straight into Health Connect, so the read path can be
                // exercised with data this app does not already hold locally.
                if (intent.getBooleanExtra("hcwrite", false)) {
                    val health = HealthConnectManager(app)
                    val today = LocalDate.now()
                    // Two records on the same day, to prove earliest-of-day wins.
                    val written = listOf(
                        Triple(today.minusDays(2), 81.9f, 7),
                        Triple(today.minusDays(1), 81.1f, 6),
                        Triple(today.minusDays(1), 88.8f, 20),
                    ).count { (date, kg, hour) ->
                        health.writeWeight(
                            date = date,
                            kg = kg,
                            atTime = date.atTime(hour, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant(),
                        )
                    }
                    // Remove those days locally, tombstones and all, so the next sync
                    // genuinely has to fill them in from Health Connect.
                    val db = AppDatabase.get(app)
                    listOf(today.minusDays(1), today.minusDays(2)).forEach { date ->
                        db.entries().deleteByDate(date.toEpochDay())
                        db.tombstones().clear(date.toEpochDay())
                    }
                    Log.i("DebugSeed", "wrote $written records to Health Connect")
                    return@launch
                }

                repo.deleteAllData()
                if (clear) {
                    WidgetUpdater.updateAll(app)
                    Log.i("DebugSeed", "cleared")
                    return@launch
                }

                // The worked example from section 5 of the build specification.
                val startDate = LocalDate.of(2026, 7, 1)
                val startKg = 82.4f
                val plan = Plan(
                    startDate = startDate,
                    startKg = startKg,
                    targetKg = 75.0f,
                    mode = PlanMode.BY_DATE,
                    targetDate = LocalDate.of(2026, 11, 30),
                    ratePerWeek = null,
                )
                repo.savePlan(plan)

                val todayIndex = 57
                // The prototype's own sample series: a steady fall with daily noise,
                // a few missing days, and every fourth reading arriving from a scale.
                val slope = if (behind) 0.0400f else 0.05614f
                val entries = (0..todayIndex).mapNotNull { d ->
                    if (d % 7 == 3 || d % 11 == 5) return@mapNotNull null
                    val kg = startKg - slope * d + 0.32f * sin(d * 1.7f) + 0.18f * sin(d * 0.55f)
                    WeightEntry(
                        date = startDate.plusDays(d.toLong()),
                        kg = Units.roundKg(kg),
                        source = if (d % 4 == 2) EntrySource.HEALTH_CONNECT else EntrySource.MANUAL,
                        recordedAt = System.currentTimeMillis() - (todayIndex - d) * 86_400_000L,
                    )
                }.toMutableList()

                // Pin today's reading so the screens show the documented numbers.
                entries[entries.lastIndex] = entries.last().copy(
                    kg = if (behind) 80.3f else 79.2f,
                    source = EntrySource.MANUAL,
                )

                entries.forEach { entry ->
                    if (entry.source == EntrySource.MANUAL) {
                        repo.saveManualEntry(entry.date, entry.kg)
                    }
                }
                repo.mergeHealthConnectEntries(entries.filter { it.source == EntrySource.HEALTH_CONNECT })

                if (unlock) repo.setUnlocked(true)
                repo.updateSettings { it.copy(onboardingComplete = true) }
                WidgetUpdater.updateAll(app)
                Log.i("DebugSeed", "seeded ${entries.size} entries, behind=$behind unlock=$unlock")
            } finally {
                pending.finish()
            }
        }
    }
}
