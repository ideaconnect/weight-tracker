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

/** Inset applied by [BaseWeightWidget] to every widget's content. */
private val WIDGET_PADDING = 14.dp

/**
 * The space a widget was actually given, and how much bigger that is than the box the
 * prototype drew it in.
 *
 * The prototype sizes the 4x2s at 110 dp tall and the 2x2 at 168 dp square. A real
 * launcher cell is routinely half as tall again, or twice — on the test device a 4x2
 * lands at roughly 386x213 dp. A layout that keeps its drawn size then floats in the
 * middle of the cell with a third to a half of the widget empty, which is what this
 * exists to stop.
 */
private class Cell(widthDp: Float, heightDp: Float, designContentHeightDp: Float) {
    val width = (widthDp - WIDGET_PADDING.value * 2f).coerceAtLeast(1f)
    val height = (heightDp - WIDGET_PADDING.value * 2f).coerceAtLeast(1f)

    val scale = (height / designContentHeightDp).coerceIn(1f, 2.2f)

    /** Type grows more slowly than the box, so the numbers keep their proportion. */
    fun text(base: Float) = base * (1f + (scale - 1f) * 0.6f)

    /** Gaps grow with the box. */
    fun space(base: Float) = base * scale

    /** Bars and strokes sit between the two. */
    fun stroke(base: Float) = base * (1f + (scale - 1f) * 0.5f)
}

@Composable
private fun cell(designContentHeightDp: Float): Cell {
    val size = LocalSize.current
    return Cell(size.width.value, size.height.value, designContentHeightDp)
}

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
                        .padding(WIDGET_PADDING),
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
        // Glance has no fractional width, so the fill is an explicitly sized child.
        // It must be measured against the TRACK, which sits inside the root Box's
        // padding and is therefore two paddings narrower than the widget itself —
        // measuring against the full width overstates progress and saturates the
        // bar at around 90%.
        val trackDp = (LocalSize.current.width.value - WIDGET_PADDING.value * 2f).coerceAtLeast(0f)
        val fillDp = trackDp * progress.coerceIn(0f, 1f)
        if (fillDp > 0.5f) {
            Box(
                modifier = GlanceModifier
                    .width(fillDp.dp)
                    .height(height.dp)
                    .cornerRadius((height / 2).dp)
                    .background(Color(palette.accent))
            ) {}
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    palette: WidgetPalette,
    labelSp: Float = 10f,
    valueSp: Float = 13f,
    padV: Float = 10f,
    valueColor: Int = palette.onSurface,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(Color(palette.surfaceAlt))
            .padding(horizontal = 11.dp, vertical = padV.dp)
    ) {
        Text(label, style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = labelSp.sp))
        Spacer(GlanceModifier.height(2.dp))
        Text(
            value,
            style = TextStyle(
                color = ColorProvider(Color(valueColor)),
                fontSize = valueSp.sp,
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
        val density = LocalContext.current.resources.displayMetrics.density
        val c = cell(designContentHeightDp = 140f)

        val labelSp = c.text(12f)
        val gap = c.space(8f)
        // The ring takes everything the cell leaves after the line beneath it.
        val diameter = minOf(c.width, c.height - labelSp * 1.5f - gap).coerceIn(56f, 260f)
        val stroke = diameter * 0.079f

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(
                        WidgetPainter.ring(
                            (diameter * density).roundToInt(),
                            stroke * density,
                            stats.progress,
                            palette,
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(diameter.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        pctLabel(stats),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.onSurface)),
                            fontSize = (diameter * 0.215f).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        "of plan",
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = (diameter * 0.102f).sp,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(gap.dp))
            Text(
                remainingLabel(stats, data),
                style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = labelSp.sp),
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
        val c = cell(designContentHeightDp = 78f)
        val gap = c.space(11f)

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = c.text(24f).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    data.unit.label,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = c.text(12f).sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    pctLabel(stats),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = c.text(12.5f).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.height(gap.dp))
            ProgressTrack(stats.progress, palette, height = c.stroke(8f).roundToInt().coerceAtLeast(6))
            Spacer(GlanceModifier.height(gap.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    Units.formatWithUnit(stats.startKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = c.text(11f).sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = c.text(11f).sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    Units.formatWithUnit(stats.targetKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = c.text(11f).sp,
                    ),
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
        val density = LocalContext.current.resources.displayMetrics.density
        val c = cell(designContentHeightDp = 86f)

        val bigSp = c.text(20f)
        val smallSp = c.text(11f)
        val gap = c.space(8f)

        // The prototype puts the figures beside a wide, shallow sparkline, which suits
        // its 250x110 box. A launcher 4x2 is far taller in proportion, so on a tall cell
        // the figures move above the chart and the chart takes the whole width — that
        // fills the cell AND keeps the shallow band. A short, wide cell keeps the
        // original side-by-side arrangement.
        val stacked = c.height > c.width * 0.4f

        if (stacked) {
            // Both figures sit in the header, so the whole area below belongs to the
            // chart. A separate footer row plus the axis date strip left the plot
            // barely 50 dp tall and flattened the line.
            val headerH = bigSp * 1.35f
            val chartH = (c.height - headerH - gap).coerceAtLeast(72f)
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        Units.format(stats.currentKg, data.unit),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.onSurface)),
                            fontSize = bigSp.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        data.unit.label,
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = smallSp.sp,
                        ),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            weekChangeLabel(stats, data) + " / 7d",
                            style = TextStyle(
                                color = ColorProvider(Color(palette.accent)),
                                fontSize = smallSp.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Text(
                            Units.format(stats.targetKg, data.unit) + " goal",
                            style = TextStyle(
                                color = ColorProvider(Color(palette.muted)),
                                fontSize = smallSp.sp,
                            ),
                        )
                    }
                }
                Spacer(GlanceModifier.height(gap.dp))
                Image(
                    provider = ImageProvider(
                        WidgetPainter.sparkline(
                            widthPx = (c.width * density).roundToInt(),
                            heightPx = (chartH * density).roundToInt(),
                            entries = data.entries,
                            stats = stats,
                            palette = palette,
                            density = density,
                            // Fewer ticks than the 4x4, but a line with no scale beside
                            // it is just a squiggle.
                            axes = WidgetPainter.Axes(
                                data.unit,
                                textSp = c.text(8.5f),
                                ticks = 3,
                                dates = 3,
                            ),
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().height(chartH.dp),
                )
            }
        } else {
            val figuresW = (c.width * 0.3f).coerceIn(96f, 160f)
            val chartW = (c.width - figuresW - gap).coerceAtLeast(72f)
            val chartH = c.height.coerceAtMost(chartW / 1.9f)
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(
                        WidgetPainter.sparkline(
                            widthPx = (chartW * density).roundToInt(),
                            heightPx = (chartH * density).roundToInt(),
                            entries = data.entries,
                            stats = stats,
                            palette = palette,
                            density = density,
                            axes = if (chartW >= 150f && chartH >= 60f) {
                                WidgetPainter.Axes(data.unit, textSp = c.text(8.5f), ticks = 3, dates = 2)
                            } else null,
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.width(chartW.dp).height(chartH.dp),
                )
                Spacer(GlanceModifier.width(gap.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Units.format(stats.currentKg, data.unit),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.onSurface)),
                            fontSize = bigSp.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        weekChangeLabel(stats, data) + " / 7d",
                        style = TextStyle(
                            color = ColorProvider(Color(palette.accent)),
                            fontSize = smallSp.sp,
                        ),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        Units.format(stats.targetKg, data.unit) + " goal",
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = c.text(10.5f).sp,
                        ),
                    )
                }
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
        val density = LocalContext.current.resources.displayMetrics.density
        val c = cell(designContentHeightDp = 238f)

        val headerSp = c.text(26f)
        val chipSp = c.text(12f)
        val tileLabelSp = c.text(10f)
        val tileValueSp = c.text(13.5f)
        val gap = c.space(12f)
        val trackH = c.stroke(6f)
        val tilePadV = c.space(10f)
        val tileH = tileLabelSp * 1.4f + 2f + tileValueSp * 1.4f + tilePadV * 2f

        // Every other row has a known height, so the chart takes the remainder — the
        // whole design grows with the cell rather than sitting in the top of it.
        val fixed = headerSp * 1.4f + gap * 3f + trackH + tileH
        val chartH = (c.height - fixed).coerceIn(72f, c.width / 1.2f)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = headerSp.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    data.unit.label,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = chipSp.sp,
                    ),
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
                                fontSize = chipSp.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(gap.dp))
            Image(
                provider = ImageProvider(
                    WidgetPainter.sparkline(
                        widthPx = (c.width * density).roundToInt(),
                        heightPx = (chartH * density).roundToInt(),
                        entries = data.entries,
                        stats = stats,
                        palette = palette,
                        density = density,
                        // Big enough to carry a scale, so the line can be read.
                        axes = WidgetPainter.Axes(data.unit, textSp = c.text(9.5f)),
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxWidth().height(chartH.dp),
            )
            Spacer(GlanceModifier.height(gap.dp))
            ProgressTrack(stats.progress, palette, height = trackH.roundToInt().coerceAtLeast(4))
            Spacer(GlanceModifier.height(gap.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    StatTile(
                        "Left", Units.formatWithUnit(stats.leftKg, data.unit), palette,
                        tileLabelSp, tileValueSp, tilePadV,
                    )
                }
                Spacer(GlanceModifier.width(1.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    StatTile(
                        "Per day",
                        if (stats.hasRate) Units.format(stats.neededPerDay, data.unit, 2) else "\u2014",
                        palette, tileLabelSp, tileValueSp, tilePadV,
                    )
                }
                Spacer(GlanceModifier.width(1.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    StatTile(
                        "Finish",
                        stats.projectedFinish?.format(isoShort) ?: "\u2014",
                        palette, tileLabelSp, tileValueSp, tilePadV,
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
        val density = LocalContext.current.resources.displayMetrics.density
        val c = cell(designContentHeightDp = 36f)

        val diameter = c.height.coerceIn(28f, 46f)
        // The figures are pushed to the two ends rather than bunched against the ring,
        // so the strip reads as deliberate at whatever width the launcher gives it
        // instead of trailing off into empty space on the right.
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(
                    WidgetPainter.ring(
                        (diameter * density).roundToInt(),
                        diameter * 0.118f * density,
                        stats.progress,
                        palette,
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(diameter.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Text(
                    Units.formatWithUnit(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = c.text(15f).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = c.text(11.5f).sp,
                    ),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                pctLabel(stats),
                style = TextStyle(
                    color = ColorProvider(Color(palette.accent)),
                    fontSize = c.text(15f).sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
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
