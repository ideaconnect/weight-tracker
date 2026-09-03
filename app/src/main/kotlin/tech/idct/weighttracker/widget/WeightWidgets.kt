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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import kotlin.math.sqrt

private val isoShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

/**
 * The inset [BaseWeightWidget] gives every widget's content: the prototype's figure,
 * kept until the cell is genuinely too small to spare it. A lock-screen strip declares
 * itself 40 dp tall, and a constant inset was spending nearly a third of that before
 * anything was drawn.
 */
internal fun padH(widthDp: Float) = (widthDp * 0.06f).coerceIn(6f, 14f)

internal fun padV(heightDp: Float) = (heightDp * 0.09f).coerceIn(4f, 12f)

/**
 * What a row of Glance text loses to Glance itself.
 *
 * Every [androidx.glance.text.Text] becomes a TextView inside wrappers of Glance's
 * own, and a column measured as "the cell, less the picture, less the spacer" comes
 * out a few dp narrower than that on the device. Measured off the rendered pixels of
 * a 186x106 dp strip, where the arithmetic said 84.6 dp and the TextView was given
 * 80.9. Reserving it is cheaper than an ellipsis.
 */
internal const val GLANCE_TEXT_SLACK = 6f

/** §12's widget surface, held at the prototype's 24 dp until the cell is too small for it. */
private fun corner(widthDp: Float, heightDp: Float) =
    (minOf(widthDp, heightDp) * 0.30f).coerceIn(10f, 24f)

/**
 * The space a widget was given — as far as anyone can tell.
 *
 * The figure a launcher reports is a floor, not a measurement. HyperOS never revises
 * the size it first wrote into a widget's options, so a 4x2 that really occupies
 * 274x137 dp goes on describing itself as the 250x110 dp its provider declares as a
 * minimum, and a 2x2 says 110x110 whatever cell it is dropped into. The first design
 * read that figure both ways — it would scale the layout down as readily as up — so on
 * that launcher a 4x2 rendered at a fraction of its cell: small faint type, a chart
 * using half its tile, a margin at each end. Every one of those is the same bug.
 *
 * Two rules follow, and between them they hold the design steady at any grid.
 *
 * The first: [scale] never goes below 1. Every widget's design fits inside the cell it
 * declares as its minimum, and the reported figure can only understate the cell, so a
 * report can never be a reason to draw the design smaller than it is. It is still a
 * reason to draw it bigger, which is what a launcher that reports honestly gets.
 *
 * The second: filling the cell is the layout's job, not arithmetic's. The parts with
 * give — the chart, the ring, the progress bar, the slack around it — are Glance
 * weights and stretched pictures, which the launcher resolves against the space it
 * really laid out rather than the space it admitted to.
 */
internal class Cell(
    widthDp: Float,
    heightDp: Float,
    designWidthDp: Float,
    designHeightDp: Float,
    /** Type is asked for in sp and every budget here is dp, so the host's setting matters. */
    private val fontScale: Float,
    /** A lock-screen strip has no business growing into headlines. */
    maxScale: Float = 2.2f,
) {
    val width = (widthDp - padH(widthDp) * 2f).coerceAtLeast(1f)
    val height = (heightDp - padV(heightDp) * 2f).coerceAtLeast(1f)

    /**
     * How much bigger than the design this cell is. Because type grows by
     * 1 + (scale - 1) * 0.6, the width limit inverts exactly that law rather than
     * guessing at headroom — [designWidthDp] is the width the layout needs at 1.
     */
    val scale = run {
        val r = width / designWidthDp
        val widthLimit = 1f + (r - 1f) / 0.6f
        minOf(height / designHeightDp, widthLimit).coerceIn(1f, maxScale)
    }

    /** Type grows more slowly than the box, so a large cell is not all numerals. */
    fun text(base: Float) = base * (1f + (scale - 1f) * 0.6f)

    /**
     * Axis figures do not scale at all. The home screen sets its scales at a flat
     * 9.5 sp however big the chart is, and a widget's chart is already the smaller
     * of the two, so type that tracked the box made its scales three times the
     * weight of the same scales in the app — which is most of what made them look
     * like different products. Holding them still also leaves the plot the room to
     * carry as many figures as the app's does.
     */
    fun axis(base: Float) = base

    /** Gaps track the box. */
    fun space(base: Float) = base * scale

    /** Bars and strokes sit between the two. */
    fun stroke(base: Float) = base * (1f + (scale - 1f) * 0.5f)

    /**
     * The dp a line of [sp] type occupies, [factor] being its line box. sp are
     * multiplied by the reader's text-size setting before they are laid out while
     * every budget here is dp, so a column measured in bare sp overruns by exactly
     * that factor.
     */
    fun lineH(sp: Float, factor: Float = 1.35f) = sp * factor * fontScale

    /**
     * The one concession to a cell that cannot hold the design after all — a host
     * that reports less than the provider's own minimum, which the lock screen does.
     * Everything shrinks together rather than the bottom row falling off the edge.
     */
    fun squeeze(needed: Float) = (height / needed).coerceIn(0.6f, 1f)
}

@Composable
private fun cell(designWidthDp: Float, designHeightDp: Float, maxScale: Float = 2.2f): Cell {
    val size = LocalSize.current
    return Cell(
        size.width.value,
        size.height.value,
        designWidthDp,
        designHeightDp,
        LocalContext.current.resources.configuration.fontScale,
        maxScale,
    )
}

/**
 * A bitmap's pixel size: the box it will be drawn into, at the screen's own density,
 * so an ImageView has nothing left to scale. The callers make that exact rather than
 * approximate by fixing the height of every row around the picture, so the weight the
 * picture takes is the height it was drawn for.
 */
private class Render(widthDp: Float, heightDp: Float, density: Float) {
    val scale: Float
    val width: Int
    val height: Int

    init {
        val w = widthDp * density
        val h = heightDp * density
        // A widget's RemoteViews travel over an IPC transaction. Above this a picture
        // is drawn smaller and scaled up, which is softer than it should be but still
        // a great deal better than not arriving.
        val k = if (w * h > BUDGET_PX) sqrt(BUDGET_PX / (w * h)) else 1f
        scale = density * k
        width = (widthDp * scale).roundToInt().coerceAtLeast(1)
        height = (heightDp * scale).roundToInt().coerceAtLeast(1)
    }

    companion object {
        private const val BUDGET_PX = 600_000f
    }
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

    /**
     * Exact, and not Responsive, though Responsive is the obvious answer to the
     * launcher in [Cell]'s note: it ships a layout per declared rung and lets the host
     * pick by the bounds it measured itself, so a launcher that lies about the cell
     * cannot mislead it. It was tried, and it made every device worse.
     *
     * The host picks the largest declared rung that fits inside the real cell, and a
     * rung is never the cell: a 4x2 at 386x213 dp composes for the 330x160 rung, so
     * the chart is drawn for a box two thirds the height of the one it is put in and
     * the ImageView stretches the difference. Stretched gridlines are nothing, but
     * stretched figures are the axis labels reading as though they were squeezed. That
     * was on every device, in exchange for a launcher-specific fault on one.
     *
     * Exact reports the true cell wherever the launcher is honest — which is the case
     * that has to be right — so the pictures are drawn at exactly the size they are
     * put in, and nothing is scaled at all. Where the launcher is not honest, the
     * report is a floor: [Cell.scale] never reads it downwards, the layout still fills
     * the real cell because it fills with weights, and only the pictures inside it are
     * scaled up by however much the launcher understated.
     */
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

/**
 * The progress bar, as a picture. It is a bitmap rather than a box inside a box
 * because Glance has no fractional width, and the dp arithmetic that stood in for one
 * was measured against a cell width the launcher may have understated.
 */
@Composable
private fun ProgressTrack(
    progress: Float,
    palette: WidgetPalette,
    widthDp: Float,
    heightDp: Float,
    density: Float,
) {
    val render = Render(widthDp, heightDp.coerceAtLeast(3f), density)
    Image(
        provider = ImageProvider(
            WidgetPainter.track(render.width, render.height, progress, palette)
        ),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    palette: WidgetPalette,
    labelSp: Float,
    valueSp: Float,
    padV: Float,
    valueColor: Int = palette.onSurface,
) {
    // Centred both ways. Three tiles side by side are read as a row of three
    // figures, not as three paragraphs: ragged left edges under a centred chart
    // made the widget look assembled rather than laid out, and the tile is taller
    // than its two lines, so top-aligning them left the spare height under the
    // figures instead of split evenly around them.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(palette.surfaceAlt))
            .padding(horizontal = 6.dp, vertical = padV.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(Color(palette.muted)),
                fontSize = labelSp.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            value,
            style = TextStyle(
                color = ColorProvider(Color(valueColor)),
                fontSize = valueSp.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth(),
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
        val c = cell(designWidthDp = 96f, designHeightDp = 104f)

        val labelSp = c.text(12f)
        val kcalSp = c.text(10.5f)
        val gap = c.space(6f)
        // Both lines under the ring are optional, and they go in that order: §12 would
        // rather drop a label than leave the ring no room. What is left is the ring's,
        // and it takes it as a weight, so a cell larger than the one reported gives the
        // ring the difference instead of leaving a hole.
        val labelH = c.lineH(labelSp, 1.5f)
        val kcalH = c.lineH(kcalSp, 1.5f)
        val kcal = kcalLabel(stats)?.takeIf { c.height - labelH - kcalH - gap >= c.space(40f) }
        val label = c.height - labelH - gap >= c.space(28f)
        val labelsH = (if (label) labelH else 0f) + (if (kcal != null) kcalH else 0f)
        val ringBox = (c.height - labelsH - (if (labelsH > 0f) gap else 0f)).coerceAtLeast(16f)
        val render = Render(minOf(c.width, ringBox), minOf(c.width, ringBox), density)

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(
                        WidgetPainter.ring(
                            sizePx = minOf(render.width, render.height),
                            progress = stats.progress,
                            palette = palette,
                            label = pctLabel(stats),
                            caption = "of plan",
                            density = render.scale,
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    // Square and centred: the ring keeps its circle and its figures
                    // keep their proportion to it at whatever size the cell gives.
                    contentScale = ContentScale.Fit,
                )
            }
            if (label) {
                Spacer(GlanceModifier.height(gap.dp))
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = labelSp.sp),
                    maxLines = 1,
                )
            }
            if (kcal != null) {
                Text(
                    kcal,
                    style = TextStyle(color = ColorProvider(Color(palette.muted)), fontSize = kcalSp.sp),
                    maxLines = 1,
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
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val c = cell(designWidthDp = 190f, designHeightDp = 78f)

        val headerSp = c.text(24f)
        val unitSp = c.text(12f)
        val pctSp = c.text(12.5f)
        val footerSp = c.text(11f)
        val gap = c.space(10f)
        // The bar takes a share of a tall cell's slack, up to double its own weight, so
        // the row it sits in reads as part of the design rather than as a line adrift
        // in the middle of it.
        val slack = c.height - c.lineH(headerSp) - c.lineH(footerSp)
        val trackH = c.stroke(8f) + (slack * 0.06f).coerceIn(0f, c.stroke(8f))

        val needed = c.lineH(headerSp) + gap * 2f + trackH + c.lineH(footerSp)
        val k = c.squeeze(needed)

        // The header carries the energy figure only when the row can hold weight,
        // unit, kcal and percent with a gap left between them — measured, because a
        // dp budget cannot see the reader's text setting. The budget this replaces
        // was `c.width >= c.text(190f)`, which is degenerate whenever the width is
        // what limits the scale: c.text(190f) inverts back to exactly c.width, so it
        // answered yes at the very size that had nothing to spare. At 1.3x the three
        // labels ran together into one word.
        val kcal = kcalLabel(stats)?.takeIf { label ->
            val ink = WidgetPainter.textWidthDp(
                context, Units.format(stats.currentKg, data.unit), headerSp * k, medium = true,
            ) +
                WidgetPainter.textWidthDp(context, data.unit.label, unitSp * k) +
                WidgetPainter.textWidthDp(context, label, footerSp * k) +
                WidgetPainter.textWidthDp(context, pctLabel(stats), pctSp * k, medium = true)
            // 6 dp is the fixed spacer after the weight; the other two gaps have to be
            // wide enough to read as gaps, which is what the eye is judging. They are
            // asked for at c.space(6f) rather than at the width of the gaps a roomy
            // cell ends up with: the question is whether three labels can sit apart,
            // not whether they can sit as far apart as they do at 316x280.
            c.width - GLANCE_TEXT_SLACK - ink >= 6f + c.space(6f) * 2f
        }

        // Header at the top, footer at the bottom, bar between them: the slack goes
        // into the gaps rather than into a margin at each end, so the design fills the
        // cell however tall the cell turns out to be.
        Column(modifier = GlanceModifier.fillMaxSize()) {
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
                        fontSize = (unitSp * k).sp,
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
                        fontSize = (pctSp * k).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.height((gap * k).dp))
            Spacer(GlanceModifier.defaultWeight())
            ProgressTrack(stats.progress, palette, c.width, trackH * k, density)
            Spacer(GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height((gap * k).dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    Units.formatWithUnit(stats.startKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (footerSp * k).sp,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    remainingLabel(stats, data),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (footerSp * k).sp,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    Units.formatWithUnit(stats.targetKg, data.unit),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = (footerSp * k).sp,
                    ),
                    maxLines = 1,
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
        val c = cell(designWidthDp = 175f, designHeightDp = 86f)

        val bigSp = c.text(20f)
        val smallSp = c.text(11f)
        val gap = c.space(8f)

        // Both figures sit in the header, so everything below it belongs to the chart:
        // one arrangement at every size, and the whole width for the plot. The right
        // column is two or three small lines tall, so the header is measured as
        // whichever side is taller. The energy line is the one that goes first, when
        // keeping it would leave the plot no room for its own axis.
        val kcal = kcalLabel(stats)
        fun headerFor(lines: Int) = maxOf(c.lineH(bigSp), c.lineH(smallSp) * lines)
        // 60 dp is what a plot needs to be a plot; it does not grow with the design,
        // or a cell big enough to hold the line comfortably would drop it for being
        // comfortable.
        val headerKcal = kcal?.takeIf { c.height - headerFor(3) - gap >= 60f }
        val headerH = headerFor(if (headerKcal != null) 3 else 2)
        val chartH = (c.height - headerH - gap).coerceAtLeast(24f)
        val render = Render(c.width, chartH, density)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // The header's height is imposed rather than measured, so that what is left
            // for the chart is exactly [chartH] — the size its picture was drawn at. Left
            // to wrap, the row comes out a few dp under the estimate and the ImageView
            // stretches the picture by the difference, which is only ever visible in the
            // axis figures. [headerH] is the taller of the two sides, so nothing clips.
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(headerH.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
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
                        maxLines = 1,
                    )
                    Text(
                        Units.format(stats.targetKg, data.unit) + " goal",
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = smallSp.sp,
                        ),
                        maxLines = 1,
                    )
                    if (headerKcal != null) {
                        Text(
                            headerKcal,
                            style = TextStyle(
                                color = ColorProvider(Color(palette.muted)),
                                fontSize = smallSp.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(gap.dp))
            Image(
                provider = ImageProvider(
                    WidgetPainter.sparkline(
                        widthPx = render.width,
                        heightPx = render.height,
                        entries = data.entries,
                        stats = stats,
                        palette = palette,
                        density = render.scale,
                        // Fewer ticks than the 4x4, but a line with no scale beside
                        // it is just a squiggle.
                        axes = WidgetPainter.Axes(
                            data.unit,
                            textSp = c.axis(9.5f),
                            typeface = WidgetPainter.mono(context),
                        ),
                    )
                ),
                contentDescription = null,
                // The remainder of the cell, and the picture scaled into it without
                // ever being distorted.
                //
                // Because every row above is given the height it was counted as rather
                // than left to measure itself, that remainder is [chartH] to the pixel
                // wherever the launcher reports its cells honestly — the box and the
                // picture are then the same shape and the same size, and Fit does
                // nothing at all. Where the launcher understates the cell the box comes
                // out taller than the picture was drawn for, and Fit enlarges the whole
                // picture to the width and centres it, which costs a band above and
                // below. FillBounds would spend that band instead of stretching the
                // figures on the axes, and axis figures stretched by half are the
                // squashed type this went looking for.
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentScale = ContentScale.Fit,
            )
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

        // A quarter-width tile cannot hold "370 kcal", so the energy figure is a
        // full-width line under the chart instead — the same place the app puts it.
        val kcal = kcalLabel(stats)

        // Every other row has a known height, so the chart takes the remainder — and
        // takes it as a weight, so it grows into a cell bigger than the one reported
        // instead of leaving the bottom of the tile empty. The energy line is dropped
        // first when the sums do not leave the plot enough to be a plot.
        fun fixedFor(withKcal: Boolean) =
            c.lineH(headerSp, 1.4f) + gap * 3f + trackH + tileH +
                if (withKcal) c.lineH(kcalSp, 1.4f) + c.space(6f) else 0f
        val shownKcal = kcal?.takeIf { c.height - fixedFor(true) >= 60f }
        val chartH = (c.height - fixedFor(shownKcal != null)).coerceAtLeast(24f)
        val render = Render(c.width, chartH, density)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Every row but the chart is given the height it was counted as, so what is
            // left over for the chart is the height its picture was drawn at. See the
            // 4x2 for why that matters.
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(c.lineH(headerSp, 1.4f).dp),
                verticalAlignment = Alignment.Bottom,
            ) {
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
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(gap.dp))
            Image(
                provider = ImageProvider(
                    WidgetPainter.sparkline(
                        widthPx = render.width,
                        heightPx = render.height,
                        entries = data.entries,
                        stats = stats,
                        palette = palette,
                        density = render.scale,
                        // Big enough to carry a scale, so the line can be read.
                        axes = WidgetPainter.Axes(
                            data.unit,
                            textSp = c.axis(9.5f),
                            typeface = WidgetPainter.mono(context),
                        ),
                    )
                ),
                contentDescription = null,
                // See the 4x2: exactly the box on a launcher that reports honestly,
                // and scaled without distortion on one that does not.
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentScale = ContentScale.Fit,
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
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().height(c.lineH(kcalSp, 1.4f).dp),
                )
            }
            Spacer(GlanceModifier.height(gap.dp))
            ProgressTrack(stats.progress, palette, c.width, trackH, density)
            Spacer(GlanceModifier.height(gap.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().height(tileH.dp)) {
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    StatTile(
                        "Left", Units.formatWithUnit(stats.leftKg, data.unit), palette,
                        tileLabelSp, tileValueSp, tilePadV,
                    )
                }
                Spacer(GlanceModifier.width(1.dp))
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    StatTile(
                        "Per day",
                        if (stats.hasRate) Units.format(stats.neededPerDay, data.unit, 2) else "—",
                        palette, tileLabelSp, tileValueSp, tilePadV,
                    )
                }
                Spacer(GlanceModifier.width(1.dp))
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    StatTile(
                        "Finish",
                        stats.projectedFinish?.format(isoShort) ?: "—",
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
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val c = cell(designWidthDp = 165f, designHeightDp = 44f, maxScale = 1.7f)

        val trackH = c.stroke(6f)
        val gap = c.space(6f)
        val weightSp = c.text(15f)
        val subSp = c.text(11.5f)
        // The ring shares the row with the two lines beside it and is the shorter of
        // the two, so it is sized against them rather than against the cell.
        val rowH = (c.height - trackH - gap).coerceAtLeast(12f)
        val k = (rowH / (c.lineH(weightSp, 1.3f) + c.lineH(subSp, 1.3f))).coerceIn(0.6f, 1f)
        val diameter = minOf(rowH, c.space(44f)).coerceAtLeast(12f)
        val render = Render(diameter, diameter, density)

        // The middle line takes the energy figure only when ring, label and percent
        // all fit the span; a narrow 4x1 keeps the plain "left" label instead. The
        // em counts this replaces (`subSp * 13f`) could not see the reader's text
        // setting, so the same strip fitted or clipped by the font scale alone.
        val kcal = kcalLabel(stats)?.takeIf { label ->
            val line = "${remainingLabel(stats, data)} · $label"
            val ink = WidgetPainter.textWidthDp(context, line, subSp * k) +
                WidgetPainter.textWidthDp(context, pctLabel(stats), weightSp * k, medium = true)
            c.width - diameter - 12f - GLANCE_TEXT_SLACK - ink >= c.space(10f)
        }

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(
                        WidgetPainter.ring(
                            sizePx = render.width,
                            progress = stats.progress,
                            palette = palette,
                            strokeFraction = 0.118f,
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(diameter.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    Text(
                        Units.formatWithUnit(stats.currentKg, data.unit),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.onSurface)),
                            fontSize = (weightSp * k).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        if (kcal != null) "${remainingLabel(stats, data)} · $kcal"
                        else remainingLabel(stats, data),
                        style = TextStyle(
                            color = ColorProvider(Color(palette.muted)),
                            fontSize = (subSp * k).sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    pctLabel(stats),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = (weightSp * k).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.height(gap.dp))
            ProgressTrack(stats.progress, palette, c.width, trackH, density)
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
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val c = cell(designWidthDp = 122f, designHeightDp = 36f, maxScale = 1.7f)

        val weightSp = c.text(15f)
        val subSp = c.text(11.5f)
        val diameter = minOf(c.height, c.space(44f)).coerceAtLeast(12f)
        val weightText = Units.formatWithUnit(stats.currentKg, data.unit)
        val caption = pctLabel(stats) + " of plan"

        // A 2x1 is the one widget whose type is decided by the width and not the
        // height. The column beside the ring is whatever the ring leaves, and a
        // taller strip grows the type without growing that column — so the caption
        // was ellipsised on the strips that had the most room for it, and whether it
        // survived at all came down to how the density happened to round it: the same
        // 110x40 dp cell fitted "43% of plan" at 480 dpi and cut it at 420.
        val textW = (c.width - diameter - 10f - GLANCE_TEXT_SLACK).coerceAtLeast(1f)
        val kH = c.height / (c.lineH(weightSp, 1.3f) + c.lineH(subSp, 1.3f))
        val kW = minOf(
            textW / WidgetPainter.textWidthDp(context, weightText, weightSp, medium = true),
            textW / WidgetPainter.textWidthDp(context, caption, subSp),
        )
        val k = minOf(kH, kW).coerceIn(0.6f, 1f)
        val render = Render(diameter, diameter, density)

        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(
                    WidgetPainter.ring(
                        sizePx = render.width,
                        progress = stats.progress,
                        palette = palette,
                        strokeFraction = 0.118f,
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(diameter.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(
                    weightText,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.onSurface)),
                        fontSize = (weightSp * k).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    caption,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.accent)),
                        fontSize = (subSp * k).sp,
                    ),
                    maxLines = 1,
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
