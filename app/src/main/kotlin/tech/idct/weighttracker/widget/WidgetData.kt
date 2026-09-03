package tech.idct.weighttracker.widget

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.AppSettings
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.ThemeChoice
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.work.DayChange
import java.time.LocalDate

/**
 * One read of the local database, shared by every widget size. Each reads the
 * same local data and needs no network (section 8).
 */
data class WidgetData(
    val unlocked: Boolean,
    val unit: WeightUnit,
    val dark: Boolean,
    val plan: Plan?,
    val stats: PlanStats?,
    val entries: List<WeightEntry>,
    val today: LocalDate,
) {
    val hasPlan: Boolean get() = plan != null && stats != null
    val behind: Boolean get() = stats?.behind == true

    companion object {
        /** A one-shot read, for the first frame of a new Glance session. */
        suspend fun load(context: Context): WidgetData {
            val repo = WeightRepository.get(context)
            return build(
                context = context,
                entries = repo.entries(),
                plan = repo.plan(),
                settings = repo.settings(),
                unlocked = repo.isUnlocked(),
            )
        }

        /**
         * The same snapshot as a flow.
         *
         * This has to be collected inside `provideContent`, not read before it. Glance
         * keeps a session alive for a while after an update, and a later update
         * recomposes that session's existing content rather than calling
         * `provideGlance` again — so anything loaded outside the composition is frozen
         * at the value it had when the session started, and a theme or unit change
         * would not reach a placed widget until the session expired.
         */
        fun flow(context: Context): Flow<WidgetData> {
            val repo = WeightRepository.get(context)
            return combine(
                repo.observeEntries(),
                repo.observePlan(),
                repo.observeSettings(),
                repo.observeUnlocked(),
                // The fifth source is the calendar. Every number below hangs off
                // "today", and none of the four above emits merely because the day
                // turned — so without this a session that outlived midnight
                // recomposed yesterday's snapshot however often it was asked to
                // redraw. [DayChange.today] says when the day really moved; the
                // date itself is read from the clock in [build], which is correct
                // whichever source woke the combine up.
                DayChange.today,
            ) { entries, plan, settings, unlocked, _ ->
                build(context, entries, plan, settings, unlocked)
            }
        }

        private fun build(
            context: Context,
            entries: List<WeightEntry>,
            plan: Plan?,
            settings: AppSettings,
            unlocked: Boolean,
        ): WidgetData {
            val today = LocalDate.now()
            val stats = plan?.let { PlanMath.stats(it, entries, today) }

            // Section 8: widgets follow the app's theme setting, not only the system one.
            val systemDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (settings.theme) {
                ThemeChoice.DARK -> true
                ThemeChoice.LIGHT -> false
                ThemeChoice.SYSTEM -> systemDark
            }

            return WidgetData(
                unlocked = unlocked,
                unit = settings.unit,
                dark = dark,
                plan = plan,
                stats = stats,
                entries = entries,
                today = today,
            )
        }
    }
}

/** The section 12 palette, as plain integers for the widget's own drawing. */
class WidgetPalette(dark: Boolean, behind: Boolean) {
    val background = if (dark) 0xFF0D0D0D.toInt() else 0xFFFAFAFA.toInt()
    val surfaceAlt = if (dark) 0xFF161616.toInt() else 0xFFF2F2F2.toInt()
    val outline = if (dark) 0xFF242424.toInt() else 0xFFE4E4E4.toInt()
    /** WtColors.grid: fainter than [outline], because a gridline is not a border. */
    val grid = if (dark) 0xFF1D1D1D.toInt() else 0xFFECECEC.toInt()
    val onSurface = if (dark) 0xFFF3F3F3.toInt() else 0xFF121212.toInt()
    val muted = if (dark) 0xFF8B8B8B.toInt() else 0xFF6B6B6B.toInt()
    val onTrack = if (dark) 0xFF4FC97F.toInt() else 0xFF2E9A5E.toInt()
    val behindColor = if (dark) 0xFFE0A44A.toInt() else 0xFFA9720F.toInt()
    val onAccent = if (dark) 0xFF00160B.toInt() else 0xFFFFFFFF.toInt()

    /** Every widget sparkline turns amber when the user is behind the plan (section 6). */
    val accent = if (behind) behindColor else onTrack

    /**
     * The projection's own colour, matching WtColors.trend. It is deliberately not a
     * status: §6 reserves amber for being behind, so the line that says where the
     * weights are heading is told apart from the grey plan line by hue instead.
     */
    val trend = if (dark) 0xFF5AA9FF.toInt() else 0xFF1668D6.toInt()
}
