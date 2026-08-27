package tech.idct.weighttracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import tech.idct.weighttracker.MainActivity
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val isoShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

/** Route the launcher tap: unlocked widgets open the app, locked ones the paywall. */
private fun launchIntent(context: Context, locked: Boolean): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_ROUTE, if (locked) MainActivity.ROUTE_PAYWALL else MainActivity.ROUTE_HOME)
    }

/**
 * The five widget sizes of section 8. All sit behind the one-time purchase; a
 * widget placed while locked renders a small locked state with a tap-to-unlock
 * action rather than stale data.
 */
abstract class BaseWeightWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    abstract fun Content(data: WidgetData, palette: WidgetPalette)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            // Collected inside the composition on purpose: Glance recomposes a live
            // session instead of calling provideGlance again, so anything captured
            // outside would go stale until the session expired.
            val data by WidgetData.flow(context).collectAsState(initial = initial)
            val palette = WidgetPalette(data.dark, data.behind)
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(palette.background))
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(launchIntent(context, !data.unlocked)))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        !data.unlocked -> LockedState(palette)
                        !data.hasPlan -> NoPlanState(palette)
                        else -> Content(data, palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedState(palette: WidgetPalette) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Widgets locked",
            style = TextStyle(
                color = ColorProvider(Color(palette.onSurface)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            "Tap to unlock",
            style = TextStyle(color = ColorProvider(Color(palette.onTrack)), fontSize = 11.sp),
        )
    }
}

@Composable
private fun NoPlanState(palette: WidgetPalette) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "No plan yet",
            style = TextStyle(
                color = ColorProvider(Color(palette.onSurface)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            "Tap to set a goal",
            style = TextStyle(color = ColorProvider(Color(palette.onTrack)), fontSize = 11.sp),
        )
    }
}

@Composable
private fun ProgressTrack(progress: Float, palette: WidgetPalette, height: Int = 8) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height.dp)
            .cornerRadius((height / 2).dp)
            .background(Color(palette.surfaceAlt)),
    ) {
        // Glance has no fractional width, so the fill is drawn as a one-pixel bitmap
        // stretched to the right proportion of the track.
        val context = LocalContext.current
        val widthPx = (LocalSize.current.width.value * context.resources.displayMetrics.density).roundToInt()
        val fill = (widthPx * progress.coerceIn(0f, 1f)).roundToInt()
        if (fill > 0) {
            Box(
                modifier = GlanceModifier
                    .width((fill / context.resources.displayMetrics.density).dp)
                    .height(height.dp)
                    .cornerRadius((height / 2).dp)
                    .background(Color(palette.accent))
            ) {}
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, palette: WidgetPalette, valueColor: Int = palette.onSurface) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(Color(palette.surfaceAlt))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(label, style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 10.sp))
        Spacer(GlanceModifier.height(2.dp))
        Text(
            value,
            style = TextStyle(
                color = ColorProvider(Color(valueColor)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun remainingLabel(stats: PlanStats, data: WidgetData) =
    "${Units.formatWithUnit(stats.leftKg, data.unit)} left"

private fun pctLabel(stats: PlanStats) = "${(stats.progress * 100).roundToInt()}%"

// ---- 2x2 progress ring -----------------------------------------------------

class RingWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val ringPx = (88 * density).roundToInt()
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(
                        WidgetPainter.ring(ringPx, 7f * density, stats.progress, palette)
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(88.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        pctLabel(stats),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.onSurface)),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        "of plan",
                        style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 9.sp),
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                remainingLabel(stats, data),
                style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 12.sp),
            )
        }
    }
}

class RingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = RingWidget()
}

// ---- 4x2 progress bar ------------------------------------------------------

class BarWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    data.unit.label,
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    pctLabel(stats),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.height(11.dp))
            ProgressTrack(stats.progress, palette)
            Spacer(GlanceModifier.height(11.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    Units.formatWithUnit(stats.startKg, data.unit),
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 11.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 11.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    Units.formatWithUnit(stats.targetKg, data.unit),
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 11.sp),
                )
            }
        }
    }
}

class BarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = BarWidget()
}

// ---- 4x2 chart -------------------------------------------------------------

class ChartWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val size = LocalSize.current
        val chartWidthDp = (size.width.value - 130f).coerceAtLeast(80f)
        // Grow with the cell, but keep the prototype's wide, shallow band (200x70):
        // at 1:1 the daily noise swamps the trend and the widget reads as a scribble.
        val available = (size.height.value - 28f).coerceAtLeast(48f)
        val chartHeightDp = minOf(available, chartWidthDp / 2.9f).coerceIn(44f, 110f)

        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(
                    WidgetPainter.sparkline(
                        widthPx = (chartWidthDp * density).roundToInt(),
                        heightPx = (chartHeightDp * density).roundToInt(),
                        entries = data.entries,
                        stats = stats,
                        palette = palette,
                        density = density,
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.width(chartWidthDp.dp).height(chartHeightDp.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Units.format(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    weekChangeLabel(stats, data) + " / 7d",
                    style = TextStyle(color = ColorProvider(Color(palette.accent)), fontSize = 11.sp),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "${Units.format(stats.targetKg, data.unit)} goal",
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 10.5.sp),
                )
            }
        }
    }
}

class ChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = ChartWidget()
}

// ---- 4x4 chart + stats -----------------------------------------------------

class BigWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val size = LocalSize.current
        val chartWidthDp = (size.width.value - 32f).coerceAtLeast(120f)
        // Everything else in the column is a known height, so the chart takes what is
        // left of the cell — capped to the prototype's 260x96 proportion, with the
        // column centred so any remainder is split above and below rather than
        // pooling at the bottom.
        val fixedHeightDp = 34f + 12f + 12f + 6f + 12f + 52f + 28f
        val available = (size.height.value - fixedHeightDp).coerceAtLeast(64f)
        val chartHeightDp = minOf(available, chartWidthDp / 2.7f).coerceIn(64f, 160f)

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    data.unit.label,
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (stats.dated) {
                    Box(
                        modifier = GlanceModifier
                            .background(Color(palette.surfaceAlt))
                            .cornerRadius(11.dp)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            aheadChipLabel(stats, data),
                            style = TextStyle(
                                color = ColorProvider(Color(palette.accent)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            Image(
                provider = ImageProvider(
                    WidgetPainter.sparkline(
                        widthPx = (chartWidthDp * density).roundToInt(),
                        heightPx = (chartHeightDp * density).roundToInt(),
                        entries = data.entries,
                        stats = stats,
                        palette = palette,
                        density = density,
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxWidth().height(chartHeightDp.dp),
            )
            Spacer(GlanceModifier.height(12.dp))
            ProgressTrack(stats.progress, palette, height = 6)
            Spacer(GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    StatTile("Left", Units.formatWithUnit(stats.leftKg, data.unit), palette)
                }
                Spacer(GlanceModifier.width(1.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    StatTile(
                        "Per day",
                        if (stats.hasRate) Units.format(stats.neededPerDay, data.unit, 2) else "—",
                        palette,
                    )
                }
                Spacer(GlanceModifier.width(1.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    StatTile(
                        "Finish",
                        stats.projectedFinish?.format(isoShort) ?: "—",
                        palette,
                        valueColor = palette.accent,
                    )
                }
            }
        }
    }
}

class BigWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = BigWidget()
}

// ---- lock screen glance ----------------------------------------------------

class GlanceWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(
                    WidgetPainter.ring((34 * density).roundToInt(), 4f * density, stats.progress, palette)
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(34.dp),
            )
            Spacer(GlanceModifier.width(14.dp))
            Column {
                Text(
                    Units.formatWithUnit(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    "${remainingLabel(stats, data)} · ${pctLabel(stats)}",
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = 11.5.sp),
                )
            }
        }
    }
}

class GlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = GlanceWidget()
}

// ---- shared labels ---------------------------------------------------------

private fun weekChangeLabel(stats: PlanStats, data: WidgetData): String {
    val change = stats.weekChangeKg ?: return "—"
    // weekChangeKg is positive when moving the right way; show it as the weight moved.
    val asWeight = -change * stats.direction
    return Units.formatSigned(asWeight, data.unit)
}

private fun aheadChipLabel(stats: PlanStats, data: WidgetData): String {
    val prefix = if (stats.aheadKg >= 0) "−" else "+"
    return prefix + Units.format(abs(stats.aheadKg), data.unit) + " vs plan"
}
