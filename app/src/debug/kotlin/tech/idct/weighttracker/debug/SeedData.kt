package tech.idct.weighttracker.debug

import android.content.Context
import android.util.Log
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
 * The fixtures behind DebugSeedReceiver, callable directly so the E2E tests can
 * seed synchronously in-process. Debug builds only.
 */
object SeedData {

    /** The worked example from section 5: 82.4 kg on 2026-07-01 → 75.0 by 2026-11-30. */
    suspend fun seed(app: Context, behind: Boolean = false, unlock: Boolean = false) {
        val repo = WeightRepository.get(app)
        repo.deleteAllData()

        val startDate = LocalDate.of(2026, 7, 1)
        val startKg = 82.4f
        repo.savePlan(
            Plan(
                startDate = startDate,
                startKg = startKg,
                targetKg = 75.0f,
                mode = PlanMode.BY_DATE,
                targetDate = LocalDate.of(2026, 11, 30),
                ratePerWeek = null,
            )
        )

        // The series always ends on the device's actual today — a fixed index
        // rotted the moment midnight passed, leaving "today" with no entry and
        // every test asserting against yesterday.
        val todayIndex = java.time.temporal.ChronoUnit.DAYS
            .between(startDate, LocalDate.now()).toInt().coerceAtLeast(7)
        // The prototype's own sample series: a steady fall with daily noise,
        // a few missing days, and every fourth reading arriving from a scale.
        val slope = if (behind) 0.0400f else 0.05614f
        val entries = (0..todayIndex).mapNotNull { d ->
            if ((d % 7 == 3 || d % 11 == 5) && d != todayIndex) return@mapNotNull null
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
            if (entry.source == EntrySource.MANUAL) repo.saveManualEntry(entry.date, entry.kg)
        }
        repo.mergeHealthConnectEntries(entries.filter { it.source == EntrySource.HEALTH_CONNECT })

        if (unlock) repo.setUnlocked(true)
        repo.updateSettings { it.copy(onboardingComplete = true) }
        WidgetUpdater.updateAll(app)
        Log.i("DebugSeed", "seeded ${entries.size} entries, behind=$behind unlock=$unlock")
    }

    suspend fun clear(app: Context) {
        WeightRepository.get(app).deleteAllData()
        WidgetUpdater.updateAll(app)
        Log.i("DebugSeed", "cleared")
    }

    /**
     * Write records straight into Health Connect, then clear those days locally,
     * so the next sync genuinely has to fill them in. Two records land on the
     * same day to prove earliest-of-day wins.
     */
    suspend fun hcWrite(app: Context) {
        val health = HealthConnectManager(app)
        val today = LocalDate.now()
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
        val db = AppDatabase.get(app)
        listOf(today.minusDays(1), today.minusDays(2)).forEach { date ->
            db.entries().deleteByDate(date.toEpochDay())
            db.tombstones().clear(date.toEpochDay())
        }
        Log.i("DebugSeed", "wrote $written records to Health Connect")
    }
}
