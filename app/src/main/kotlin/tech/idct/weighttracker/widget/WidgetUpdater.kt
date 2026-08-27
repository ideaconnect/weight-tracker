package tech.idct.weighttracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import tech.idct.weighttracker.MainActivity

/**
 * Section 8 update triggers: manual save, completed sync (foreground or
 * background), plan change, unit change, theme change, and the daily background
 * job. Everything funnels through here so no trigger is forgotten.
 */
object WidgetUpdater {

    private val widgets = listOf(
        RingWidget(),
        BarWidget(),
        ChartWidget(),
        BigWidget(),
        GlanceWidget(),
    )

    suspend fun updateAll(context: Context) {
        widgets.forEach { widget ->
            runCatching { widget.updateAll(context) }
                .onFailure { Log.w("WidgetUpdater", "Widget refresh failed", it) }
        }
    }

    /** Whether any of the five sizes is currently placed on a home screen. */
    suspend fun placedCount(context: Context): Int {
        val manager = GlanceAppWidgetManager(context)
        return widgets.sumOf { runCatching { manager.getGlanceIds(it::class.java).size }.getOrDefault(0) }
    }

    /**
     * Section 8: the second of the two documented routes to adding a widget — tapping
     * one inside the app and adding it from there via requestPinAppWidget.
     */
    fun requestPin(context: Context, kind: WidgetKind): Boolean {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT < 26 || !manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, kind.receiver)
        val callback = PendingIntent.getActivity(
            context,
            kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_PLACEMENT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return runCatching { manager.requestPinAppWidget(provider, null, callback) }.getOrDefault(false)
    }
}

/** The five sizes, in the order the widget gallery lists them. */
enum class WidgetKind(
    val title: String,
    val sizeLabel: String,
    val receiver: Class<*>,
) {
    RING("2×2 progress ring", "2×2 · Progress ring", RingWidgetReceiver::class.java),
    BAR("4×2 progress bar", "4×2 · Progress bar", BarWidgetReceiver::class.java),
    CHART("4×2 chart", "4×2 · Chart", ChartWidgetReceiver::class.java),
    BIG("4×4 chart + stats", "4×4 · Chart + stats", BigWidgetReceiver::class.java),
    GLANCE("lock screen glance", "Lock screen · glance", GlanceWidgetReceiver::class.java),
}
