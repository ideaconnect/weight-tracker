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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import tech.idct.weighttracker.MainActivity
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val isoShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

/**
 * The inset [BaseWeightWidget] gives every widget's content.
 *
 * The prototype draws with a 14 dp inset, which is right for a cell of the prototype's
 * size and wrong for a smaller one: a nine-row grid hands a 4x1 about 70 dp of height,
 * and a constant 28 dp of that goes before anything is drawn. Both saturate at the
 * prototype's figure, so a cell of the size the repo's screenshots were taken at keeps
 * exactly the inset it had and only a smaller one gets its space back.
 */
internal fun padH(widthDp: Float) = (widthDp * 0.09f).coerceIn(6f, 14f)

internal fun padV(heightDp: Float) = (heightDp * 0.14f).coerceIn(4f, 14f)

/** §12's widget surface, held at the prototype's 24 dp until the cell is too small for it. */
private fun corner(widthDp: Float, heightDp: Float) =
    (minOf(widthDp, heightDp) * 0.30f).coerceIn(10f, 24f)

/** Type below this is noticed rather than read; §12 drops a line before printing one smaller. */
private const val MIN_TEXT_SP = 9f

/**
 * The space a widget was actually given, and how that compares with the box the
 * prototype drew it in.
 *
 * The prototype sizes the 4x2s at 110 dp tall and the 2x2 at 168 dp square. A real
 * launcher cell is routinely half as tall again, or twice — on the test device a 4x2
 * lands at roughly 386x213 dp — so the design has to grow into it. It also has to
 * shrink. A five-column, nine-row grid hands the same span a little over half the
 * height and four fifths of the width, and the first model could not: its scale was
 * floored at the design size and read height alone, so a cell smaller than the
 * prototype got the prototype drawn at full size and the launcher clipped whatever
 * overflowed. That is how "the widget is the right size but everything on it is
 * small" happened — the 4x2 chart lost its date axis, the 2x2's ring lost its top
 * and bottom, and at the very sizes the two lock-screen widgets declare as their
 * minimum the type was cut through the middle.
 *
 * So the fit is symmetric and two-dimensional, and nothing is floored at the design
 * size. The damping that stops type ballooning on a large cell applies only above 1:
 * a cell smaller than the design has no slack to give away, so below 1 type tracks
 * the cell one for one. And because type grows by 1 + (scale - 1) * 0.6, the width
 * limit inverts exactly that law rather than guessing at headroom — [designWidthDp]
 * is the width the layout needs at scale 1.
 */
internal class Cell(
    widthDp: Float,
    heightDp: Float,
    designWidthDp: Float,
    designHeightDp: Float,
    /** [text] returns sp and every budget here is dp, so the host's setting has to be known. */
    private val fontScale: Float,
) {
    val width = (widthDp - padH(widthDp) * 2f).coerceAtLeast(1f)
    val height = (heightDp - padV(heightDp) * 2f).coerceAtLeast(1f)

    val scale = run {
        val r = width / designWidthDp
        val widthLimit = if (r >= 1f) 1f + (r - 1f) / 0.6f else r
        minOf(height / designHeightDp, widthLimit).coerceIn(0.55f, 2.2f)
    }

    /** Type grows more slowly than the box, and shrinks with it down to [MIN_TEXT_SP]. */
    fun text(base: Float): Float {
        val factor = if (scale >= 1f) 1f + (scale - 1f) * 0.6f else scale
        return (base * factor).coerceAtLeast(minOf(base, MIN_TEXT_SP))
    }

    /** Gaps track the box in both directions. */
    fun space(base: Float) = (base * scale).coerceAtLeast(1f)

    /** Bars and strokes sit between the two, and stay visible. */
    fun stroke(base: Float): Float {
        val factor = if (scale >= 1f) 1f + (scale - 1f) * 0.5f else scale
        return (base * factor).coerceAtLeast(minOf(base, 2f))
    }

    /**
     * The dp a line of [sp] type occupies, [factor] being the line box the caller
     * already used. sp are multiplied by the reader's text-size setting before they
     * are laid out while every budget here is dp, so a column measured in bare sp
     * overruns by exactly that factor — the same clipping the dense grid produced,
     * arriving by a different door. At the default setting this returns what the old
     * arithmetic did.
     */
    fun lineH(sp: Float, factor: Float = 1.35f) = sp * factor * fontScale

    /** Type sized to fit a dp box — a ring's caption — has to be asked for in sp. */
    fun spFromDp(dp: Float) = dp / fontScale
}

@Composable
private fun cell(designWidthDp: Float, designHeightDp: Float): Cell {
    val size = LocalSize.current
    return Cell(
        size.width.value,
        size.height.value,
        designWidthDp,
        designHeightDp,
        LocalContext.current.resources.configuration.fontScale,
    )
}

/** Route the launcher tap: unlocked widgets open the app, locked ones the paywall. */
private fun launchIntent(context: Context, locked: Boolean): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_ROUTE, if (locked) MainActivity.ROUTE_PAYWALL else MainActivity.ROUTE_HOME)
    }

/**
 * The widget sizes of section 8. All sit behind the one-time purchase; a
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
            val size = LocalSize.current
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(palette.background))
                        .cornerRadius(corner(size.width.value, size.height.value).dp)
                        .clickable(actionStartActivity(launchIntent(context, !data.unlocked)))
                        .padding(
                            horizontal = padH(size.width.value).dp,
                            vertical = padV(size.height.value).dp,
                        ),
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
        val widgetW = LocalSize.current.width.value
        val trackDp = (widgetW - padH(widgetW) * 2f).coerceAtLeast(0f)
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
        val c = cell(designWidthDp = 96f, designHeightDp = 140f)

        val labelSp = c.text(12f)
        val kcalSp = c.text(10.5f)
        val gap = c.space(8f)
        // Both lines under the ring are optional, and they go in that order: §12 would
        // rather drop a label than shrink the ring it sits under to nothing. The ring
        // then takes the remainder — never a floor above it, which is what used to
        // draw a 56 dp ring into a 24 dp gap and lose its top and bottom to the clip.
        val labelH = c.lineH(labelSp, 1.5f)
        val kcalH = c.lineH(kcalSp, 1.5f)
        val kcal = kcalLabel(stats)?.takeIf { c.height - labelH - kcalH - gap >= 44f }
        val label = c.height - labelH - gap >= 30f
        val labelsH = (if (label) labelH else 0f) + (if (kcal != null) kcalH else 0f)
        val diameter = minOf(c.width, c.height - labelsH - (if (labelsH > 0f) gap else 0f))
            .coerceIn(20f, 260f)
        val stroke = diameter * 0.079f
        // The caption is sized to fit inside the ring, so it is a dp quantity; below
        // this the ring is too small to hold a second line at all.
        val innerCaption = diameter >= 84f

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
                            fontSize = c.spFromDp(diameter * 0.215f).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    if (innerCaption) {
                        Text(
                            "of plan",
                            style = TextStyle(
                                color = ColorProvider(Color(palette.muted)),
                                fontSize = c.spFromDp(diameter * 0.102f).sp,
                            ),
                        )
                    }
                }
            }
            if (label) {
                Spacer(GlanceModifier.height(gap.dp))
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = labelSp.sp),
                )
            }
            if (kcal != null) {
                Text(
                    kcal,
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = kcalSp.sp),
                )
            }
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
        val c = cell(designWidthDp = 190f, designHeightDp = 78f)
        val gap = c.space(11f)

        // The header carries the energy figure only when the row can hold weight,
        // unit, kcal and percent together. 190dp covers the widest realistic
        // strings at base type, and c.text scales that budget exactly as the fonts
        // scale; a narrow 4x2 (a five-column grid hands one ~255dp) keeps the
        // original three-item header instead of wrapping it.
        val kcal = kcalLabel(stats)?.takeIf { c.width >= c.text(190f) }

        // A column of fixed rows has nothing to absorb slack, so when the rows total
        // more than the cell it simply runs past the bottom edge. One squeeze factor
        // over all of them keeps the proportions and fits; where the column already
        // fits — every cell the repo's screenshots were taken at — k is 1 and nothing
        // moves.
        val headerSp = c.text(24f)
        val footerSp = c.text(11f)
        val trackH = c.stroke(8f)
        val needed = c.lineH(headerSp) + gap * 2f + trackH + c.lineH(footerSp)
        val k = (c.height / needed).coerceAtMost(1f)

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = (headerSp * k).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    data.unit.label,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (c.text(12f) * k).sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (kcal != null) {
                    Text(
                        kcal,
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = (footerSp * k).sp,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                }
                Text(
                    pctLabel(stats),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = (c.text(12.5f) * k).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.height((gap * k).dp))
            ProgressTrack(stats.progress, palette, height = (trackH * k).roundToInt().coerceAtLeast(4))
            Spacer(GlanceModifier.height((gap * k).dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    Units.formatWithUnit(stats.startKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (footerSp * k).sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (footerSp * k).sp,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    Units.formatWithUnit(stats.targetKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (footerSp * k).sp,
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
        val c = cell(designWidthDp = 175f, designHeightDp = 86f)

        val bigSp = c.text(20f)
        val smallSp = c.text(11f)
        val gap = c.space(8f)

        // The prototype puts the figures beside a wide, shallow sparkline, which suits
        // its 250x110 box. A launcher 4x2 is far taller in proportion, so on a tall cell
        // the figures move above the chart and the chart takes the whole width — that
        // fills the cell AND keeps the shallow band. A short, wide cell keeps the
        // original side-by-side arrangement.
        val stacked = c.height > c.width * 0.4f

        val kcal = kcalLabel(stats)

        if (stacked) {
            // Both figures sit in the header, so the whole area below belongs to the
            // chart. A separate footer row plus the axis date strip left the plot
            // barely 50 dp tall and flattened the line.
            // The right column is two or three small lines tall; the header must be
            // measured as whichever side is taller, or the chart under it overflows.
            // The energy line is the one that goes first: floored at 72 dp the plot
            // pushed itself and its date axis off the bottom of a dense grid's cell,
            // where dropping one line of the header leaves room for the whole chart.
            fun headerFor(lines: Int) = maxOf(c.lineH(bigSp), c.lineH(smallSp) * lines)
            val headerKcal = kcal?.takeIf { c.height - headerFor(3) - gap >= 72f }
            val headerH = headerFor(if (headerKcal != null) 3 else 2)
            val chartH = (c.height - headerH - gap).coerceAtLeast(24f)
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
                        if (headerKcal != null) {
                            Text(
                                headerKcal,
                                style = TextStyle(
                                    color = ColorProvider(Color(palette.muted)),
                                    fontSize = smallSp.sp,
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
                            // Fewer ticks than the 4x4, but a line with no scale beside
                            // it is just a squiggle.
                            axes = WidgetPainter.Axes(
                                data.unit,
                                textSp = c.text(8.5f),
                                maxTicks = 3,
                                maxDates = 3,
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
            // This branch serves the shortest cells, so a fourth line joins the
            // figures column only when all four actually fit the height.
            val kcalLine = kcal?.takeIf {
                c.height >= c.lineH(bigSp) + c.lineH(smallSp) + c.lineH(c.text(10.5f)) * 2f + 12f
            }
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
                                WidgetPainter.Axes(data.unit, textSp = c.text(8.5f), maxTicks = 3, maxDates = 2)
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
                    if (kcalLine != null) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            kcalLine,
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
        val c = cell(designWidthDp = 215f, designHeightDp = 238f)

        val headerSp = c.text(26f)
        val chipSp = c.text(12f)
        val tileLabelSp = c.text(10f)
        val tileValueSp = c.text(13.5f)
        val kcalSp = c.text(11f)
        val gap = c.space(12f)
        val trackH = c.stroke(6f)
        val tilePadV = c.space(10f)
        val tileH = c.lineH(tileLabelSp, 1.4f) + 2f + c.lineH(tileValueSp, 1.4f) + tilePadV * 2f

        // A quarter-width tile cannot hold "370 kcal" once the cell scales the type,
        // so the energy figure is a full-width line under the chart instead — the
        // same place the app itself puts it.
        val kcal = kcalLabel(stats)

        // Every other row has a known height, so the chart takes the remainder — the
        // whole design grows with the cell rather than sitting in the top of it, and
        // shrinks with it rather than pushing the stat tiles off the bottom. The
        // energy line is dropped first, for the same reason as on the 4x2.
        fun fixedFor(withKcal: Boolean) =
            c.lineH(headerSp, 1.4f) + gap * 3f + trackH + tileH +
                if (withKcal) c.lineH(kcalSp, 1.4f) + c.space(6f) else 0f
        val shownKcal = kcal?.takeIf { c.height - fixedFor(true) >= 72f }
        val chartH = (c.height - fixedFor(shownKcal != null))
            .coerceAtMost(c.width / 1.2f)
            .coerceAtLeast(24f)

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
                if (stats.scheduleStarted) {
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
            if (shownKcal != null) {
                Spacer(GlanceModifier.height(c.space(6f).dp))
                Text(
                    shownKcal,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = kcalSp.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }
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

// ---- lock screen glance, wide ----------------------------------------------

/**
 * §8's lock-screen glance, in the width a launcher usually hands a 4x1: the ring and
 * the figures on one line, and the plan's progress bar filling the row beneath rather
 * than leaving the space empty.
 */
class GlanceWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        val density = LocalContext.current.resources.displayMetrics.density
        val c = cell(designWidthDp = 165f, designHeightDp = 44f)

        val trackH = c.stroke(6f)
        val gap = c.space(8f)
        // The ring shares the height with the bar, so it is sized against what is left
        // — and never floored above it, which at the widget's own declared 250x60
        // minimum drew a 26 dp ring beside two lines of type in a row too short for
        // either, and cut both through the middle.
        val rowH = (c.height - trackH - gap).coerceAtLeast(12f)
        val diameter = minOf(rowH, 44f)
        // The two lines beside the ring are the taller half of that row, so they are
        // what has to be squeezed into it.
        val weightSpRaw = c.text(15f)
        val subSpRaw = c.text(11.5f)
        val k = (rowH / (c.lineH(weightSpRaw, 1.3f) + c.lineH(subSpRaw, 1.3f))).coerceAtMost(1f)
        val weightSp = weightSpRaw * k
        val subSp = subSpRaw * k

        // The middle line takes the energy figure only when ring, label and percent
        // all fit the span at the height-scaled fonts (the label runs ~13em with the
        // figure appended); a narrow 4x1 keeps the plain "left" label instead.
        val kcal = kcalLabel(stats)?.takeIf {
            c.width >= diameter + subSp * 13f + weightSp * 2.8f + 22f
        }

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            fontSize = weightSp.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        if (kcal != null) "${remainingLabel(stats, data)} · $kcal"
                        else remainingLabel(stats, data),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = subSp.sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    pctLabel(stats),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = weightSp.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.height(gap.dp))
            ProgressTrack(stats.progress, palette, height = trackH.roundToInt().coerceAtLeast(4))
        }
    }
}

class GlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = GlanceWidget()
}

// ---- lock screen glance, compact -------------------------------------------

/**
 * The same glance at half the width, for anyone who would rather not give a whole row
 * to it. Ring, weight, percentage — nothing that needs the space the wide one uses.
 */
class GlanceCompactWidget : BaseWeightWidget() {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette) {
        val stats = data.stats!!
        val density = LocalContext.current.resources.displayMetrics.density
        val c = cell(designWidthDp = 122f, designHeightDp = 36f)

        // At the widget's own declared 110x40 minimum the old floor asked for a 26 dp
        // ring and two lines of type inside 12 dp of content, and the launcher cut the
        // weight in half. Ring and type both take what the cell actually has.
        val diameter = minOf(c.height, 44f).coerceAtLeast(12f)
        val weightSpRaw = c.text(15f)
        val subSpRaw = c.text(11.5f)
        val k = (c.height / (c.lineH(weightSpRaw, 1.3f) + c.lineH(subSpRaw, 1.3f))).coerceAtMost(1f)
        val weightSp = weightSpRaw * k
        val subSp = subSpRaw * k
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
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(
                    Units.formatWithUnit(stats.currentKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = weightSp.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    pctLabel(stats) + " of plan",
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = subSp.sp,
                    ),
                )
            }
        }
    }
}

class GlanceCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = GlanceCompactWidget()
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

/**
 * The daily energy figure, mirroring Format.kcalCompact: "−370 kcal / day" is the
 * deficit a loss plan asks for, "+370" the surplus of a gain plan. Null hides the
 * line when there is no rate or nothing left.
 */
private fun kcalLabel(stats: PlanStats): String? {
    if (!stats.hasKcal) return null
    val sign = if (stats.direction > 0) "−" else "+"
    return "$sign${stats.neededKcalRounded} kcal / day"
}
