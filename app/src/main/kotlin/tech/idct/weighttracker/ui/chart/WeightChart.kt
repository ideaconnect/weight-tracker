package tech.idct.weighttracker.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.ChartScale
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.WtChip
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Range chips: 7d / 30d / 90d / whole plan span, defaulting to the whole span. */
enum class ChartRange(val label: String, val days: Int?) {
    D7("7d", 7),
    D30("30d", 30),
    D90("90d", 90),
    PLAN("Plan", null),
}

/** A window of day indices, counted from the plan's start date. */
internal data class Win(val x0: Float, val x1: Float) {
    val width: Float get() = x1 - x0
}

private data class YDomain(val lo: Float, val hi: Float)

/** Narrower than three days and a daily series is a single dot. */
internal const val MIN_WINDOW_DAYS = 3f

/**
 * The chart is the top of the home screen and the reason the app exists (section 6).
 * Five layers, drawn in order: gridlines, tolerance band, plan line, trend
 * projection, actual weights.
 *
 * Interactions: one finger scrubs; two fingers zoom about the point between them
 * and, moved together, pan. A range chip puts the window back where it belongs.
 */
@Composable
fun WeightChart(
    entries: List<WeightEntry>,
    plan: Plan,
    stats: PlanStats,
    unit: WeightUnit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(ChartRange.PLAN) }
    // A window the user has pinched or panned to; null means the range's own.
    var custom by remember { mutableStateOf<Win?>(null) }
    var scrubDay by remember { mutableStateOf<Float?>(null) }
    var plotSize by remember { mutableStateOf(IntSize.Zero) }

    val colors = WtTheme.colors
    val accent = WtTheme.accent
    val behind = WtTheme.behind
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val startDate = plan.startDate
    val todayIndex = PlanMath.dayIndex(startDate, today)
    val entryDays = remember(entries, startDate) {
        entries.map { PlanMath.dayIndex(startDate, it.date) to it }
    }
    val firstDay = min(todayIndex, entryDays.firstOrNull()?.first ?: todayIndex)
    val lastDay = max(todayIndex, entryDays.lastOrNull()?.first ?: todayIndex)
    val dated = plan.mode != PlanMode.NO_DEADLINE
    val span = stats.spanDays

    val bounds = remember(span, firstDay, lastDay) { chartBounds(span, firstDay, lastDay) }
    val presetWin = remember(range, bounds, todayIndex) { defaultWindow(range, bounds, todayIndex) }
    val win = remember(presetWin, custom, bounds) { custom?.let { clampWindow(it, bounds) } ?: presetWin }
    val zoom = presetWin.width / win.width
    val yDom = remember(win, entryDays, plan) { computeYDomain(win, entryDays, plan, stats) }

    // Pixel geometry, in the proportions the prototype lays out (331 x 206 units).
    // Derived from whatever width and height are in hand: the draw pass uses the
    // DrawScope's own size, so the first frame is never blank, while the pointer
    // and overlay maths fall back to the last measured size.
    fun axOf(width: Float) = width * 34f / 331f
    fun awOf(width: Float) = width * 286f / 331f
    fun ayOf(height: Float) = height * 14f / 206f
    fun ahOf(height: Float) = height * 150f / 206f

    fun xIn(width: Float, day: Float): Float = axOf(width) + (day - win.x0) / win.width * awOf(width)
    fun yIn(height: Float, kg: Float): Float =
        ayOf(height) + (yDom.hi - kg) / (yDom.hi - yDom.lo) * ahOf(height)

    val w = plotSize.width.toFloat()
    val h = plotSize.height.toFloat()

    // Overlay positions, measured against the laid-out size.
    fun xOf(day: Float): Float = xIn(w, day)
    fun yOf(kg: Float): Float = yIn(h, kg)

    // Round weights in the display unit, so a pound user sees 175 and 180 rather
    // than the odd fractions a kilogram step converts to.
    val axisStyle = TextStyle(fontFamily = RobotoMono, fontSize = 9.5.sp, color = colors.muted)
    val yTicks = remember(yDom, unit) {
        ChartScale.niceTicks(Units.toDisplay(yDom.lo, unit), Units.toDisplay(yDom.hi, unit), 6)
            .map { Units.fromDisplay(it, unit) to ChartScale.label(it) }
    }
    // Calendar-aligned dates, as many as the plot can label without them touching.
    val dateLabelWidth = measurer.measure("00-00", axisStyle).size.width.toFloat()
    val maxDateTicks = if (w > 0f) (awOf(w) / (dateLabelWidth * 1.7f)).toInt().coerceIn(2, 8) else 4
    val dateTicks = remember(win, startDate, maxDateTicks) {
        ChartScale.dateTicks(startDate, win.x0, win.x1, maxDateTicks)
            .map { PlanMath.dayIndex(startDate, it).toFloat() to it.format(Format.monthDay) }
    }

    // The gesture handler installed below outlives the composition that created it,
    // so it must read the window and the measured width at call time rather than
    // capturing them. Capturing froze it to the first composition, where the width
    // was still zero: every scrub divided by zero and resolved to the oldest entry.
    // Keying pointerInput on `win` was no fix either, because a pinch mutates `win`
    // on every step and so restarted the block mid-gesture.
    val liveWindow = rememberUpdatedState(win)
    val liveBounds = rememberUpdatedState(bounds)
    val liveWidth = rememberUpdatedState(w)

    // The scrubber snaps to the nearest entry in view — never to one off-screen,
    // which used to fling the marker weeks away at the edge of a narrow window.
    val scrubEntry = scrubDay?.let { d ->
        entryDays.filter { it.first >= win.x0 && it.first <= win.x1 }.minByOrNull { abs(it.first - d) }
    }

    val windowDescription = "Weight chart, " +
        "${startDate.plusDays(ceil(win.x0).toLong()).format(Format.isoDate)} to " +
        startDate.plusDays(floor(win.x1).toLong()).format(Format.isoDate)

    Column(modifier = modifier) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(331f / 206f)
                    .onSizeChanged { plotSize = it }
                    .testTag("chart")
                    .semantics { contentDescription = windowDescription }
                    .pointerInput(Unit) {
                        fun dayAt(px: Float, window: Win): Float {
                            val width = liveWidth.value
                            if (width <= 0f) return window.x0
                            return window.x0 + (px - axOf(width)) / awOf(width) * window.width
                        }
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            var pinching = false
                            var pinched = false
                            var prevCentroid = 0f
                            var prevDistance = 0f
                            // The window as this gesture last left it. Read back from
                            // the composition instead, two events in one frame both
                            // started from the same window and the second overwrote
                            // the first: a pinch stepped, never accumulated.
                            var window = liveWindow.value
                            scrubDay = dayAt(first.position.x, window)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val a = pressed[0].position.x
                                    val b = pressed[1].position.x
                                    val centroid = (a + b) / 2f
                                    val distance = max(20f, abs(a - b))
                                    val width = liveWidth.value
                                    if (!pinching) window = liveWindow.value
                                    if (pinching && width > 0f) {
                                        // Zoom about the day between the fingers, and
                                        // keep that day under them as they move: that
                                        // is both the pinch and the pan, in one rule.
                                        val limits = liveBounds.value
                                        val newWidth = (window.width * prevDistance / distance)
                                            .coerceIn(MIN_WINDOW_DAYS, limits.width)
                                        val focal = dayAt(prevCentroid, window)
                                        val frac = (centroid - axOf(width)) / awOf(width)
                                        val x0 = focal - frac * newWidth
                                        window = clampWindow(Win(x0, x0 + newWidth), limits)
                                        custom = window
                                    }
                                    pinching = true
                                    pinched = true
                                    prevCentroid = centroid
                                    prevDistance = distance
                                    scrubDay = null
                                    pressed.forEach { it.consume() }
                                } else {
                                    pinching = false
                                    val change = pressed.first()
                                    // The finger left over from a pinch does not scrub.
                                    if (!pinched) scrubDay = dayAt(change.position.x, liveWindow.value)
                                    if (change.positionChange() != Offset.Zero) change.consume()
                                }
                            }
                            // Releasing dismisses the scrubber.
                            scrubDay = null
                        }
                    }
            ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas

                val ax = axOf(size.width)
                val aw = awOf(size.width)
                val ay = ayOf(size.height)
                val ah = ahOf(size.height)
                val dpx = density.density
                fun xOf(day: Float): Float = xIn(size.width, day)
                fun yOf(kg: Float): Float = yIn(size.height, kg)
                fun planAt(day: Float): Float = PlanMath.planKgAt(plan, day)

                // 1. Horizontal gridlines at the round weights labelled in the gutter.
                yTicks.forEach { (kg, _) ->
                    val y = yOf(kg)
                    drawLine(colors.grid, Offset(ax, y), Offset(ax + aw, y), strokeWidth = 1f)
                }

                // The data layers stay inside the plot: zoomed in, the band and the
                // plan line run far past both edges and used to paint over the gutter.
                clipRect(left = ax - dpx, top = 0f, right = ax + aw + dpx, bottom = size.height) {
                    // 2. Tolerance band, plan +/- 0.6 kg, at 9% opacity in the status
                    //    colour. The plan line is the §5 function: sloped from the
                    //    start to the target date, flat at the target after it, and
                    //    nothing before the plan existed.
                    val from = max(0f, win.x0)
                    val to = win.x1
                    if (dated && span > 0 && to > from) {
                        val corner = span.toFloat() in from..to
                        val band = Path().apply {
                            moveTo(xOf(from), yOf(planAt(from) + PlanMath.TOLERANCE_KG))
                            if (corner) lineTo(xOf(span.toFloat()), yOf(plan.targetKg + PlanMath.TOLERANCE_KG))
                            lineTo(xOf(to), yOf(planAt(to) + PlanMath.TOLERANCE_KG))
                            lineTo(xOf(to), yOf(planAt(to) - PlanMath.TOLERANCE_KG))
                            if (corner) lineTo(xOf(span.toFloat()), yOf(plan.targetKg - PlanMath.TOLERANCE_KG))
                            lineTo(xOf(from), yOf(planAt(from) - PlanMath.TOLERANCE_KG))
                            close()
                        }
                        drawPath(band, accent, alpha = 0.09f)
                    }

                    // 3. Plan line: dashed, neutral grey.
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx() * 0.6f, 4.dp.toPx() * 0.6f)
                    )
                    val planStroke = Stroke(width = 1.4f * dpx, pathEffect = dash)
                    if (dated && span > 0) {
                        if (to > from) {
                            val line = Path().apply {
                                moveTo(xOf(from), yOf(planAt(from)))
                                if (span.toFloat() in from..to) lineTo(xOf(span.toFloat()), yOf(plan.targetKg))
                                lineTo(xOf(to), yOf(planAt(to)))
                            }
                            drawPath(line, colors.muted, style = planStroke, alpha = 0.85f)
                        }
                    } else {
                        drawLine(
                            color = colors.muted,
                            start = Offset(xOf(win.x0), yOf(plan.targetKg)),
                            end = Offset(xOf(win.x1), yOf(plan.targetKg)),
                            strokeWidth = 1.4f * dpx,
                            pathEffect = dash,
                            alpha = 0.85f,
                        )
                    }

                    // 4. Trend projection: finely dotted, deliberately neutral so it
                    //    cannot be confused with a status. Clamped to the right edge.
                    val finish = stats.projectedFinish
                    if (dated && finish != null) {
                        val finishDay = PlanMath.dayIndex(startDate, finish).toFloat()
                        val xEnd = min(finishDay, win.x1)
                        val f = if (finishDay > lastDay) {
                            ((xEnd - lastDay) / (finishDay - lastDay)).coerceIn(0f, 1f)
                        } else 1f
                        val yEnd = stats.currentKg + (plan.targetKg - stats.currentKg) * f
                        val trendFrom = entryDays.lastOrNull()?.first?.toFloat() ?: lastDay.toFloat()
                        drawLine(
                            color = colors.onSurface,
                            start = Offset(xOf(trendFrom), yOf(stats.currentKg)),
                            end = Offset(xOf(xEnd), yOf(yEnd)),
                            strokeWidth = 1.2f * dpx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * dpx, 3.5f * dpx)),
                            alpha = 0.5f,
                        )
                    }

                    // 5. Actual weights: solid 2.2 px line in the status colour.
                    //    Section 13: gaps are a straight segment between known points.
                    //    One entry beyond each edge is kept so the line enters and
                    //    leaves the plot rather than starting inside it.
                    val visible = entriesAround(entryDays, win)
                    if (visible.size >= 2) {
                        val path = Path()
                        visible.forEachIndexed { i, (day, entry) ->
                            val px = xOf(day.toFloat())
                            val py = yOf(entry.kg)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path,
                            accent,
                            style = Stroke(
                                width = 2.2f * dpx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                    // Zoomed in far enough for readings to stand apart, each gets a
                    // dot: a week of weigh-ins is seven numbers, not one wobble.
                    if (aw / win.width >= 6f * dpx) {
                        visible.forEach { (day, entry) ->
                            drawCircle(accent, radius = 2.2f * dpx, center = Offset(xOf(day.toFloat()), yOf(entry.kg)))
                        }
                    }

                    // A vertical hairline marks today.
                    drawLine(
                        colors.outline,
                        Offset(xOf(todayIndex.toFloat()), ay),
                        Offset(xOf(todayIndex.toFloat()), ay + ah),
                        strokeWidth = 1f,
                    )

                    // A filled dot on the latest reading. Anchored to the entry's own
                    // day, not to `lastDay` — that is today whenever today has not been
                    // logged, which left the dot floating to the right of the line.
                    entryDays.lastOrNull()?.let { (lastEntryDay, lastEntry) ->
                        drawCircle(
                            accent,
                            radius = 4f * dpx,
                            center = Offset(xOf(lastEntryDay.toFloat()), yOf(lastEntry.kg)),
                        )
                    }

                    // Scrubber: a vertical line follows the finger, the nearest entry a ring.
                    scrubEntry?.let { (day, entry) ->
                        val sx = xOf(day.toFloat())
                        drawLine(colors.onSurface, Offset(sx, ay), Offset(sx, ay + ah), strokeWidth = 1f, alpha = 0.45f)
                        drawCircle(colors.background, radius = 5f * dpx, center = Offset(sx, yOf(entry.kg)))
                        drawCircle(
                            accent,
                            radius = 5f * dpx,
                            center = Offset(sx, yOf(entry.kg)),
                            style = Stroke(width = 2f * dpx),
                        )
                    }
                }

                // A hollow ring on the target, with its label, when the goal is in view.
                val targetX = if (dated && span > 0) span.toFloat() else win.x1 - 6f
                if (targetX >= win.x0 && targetX <= win.x1) {
                    drawCircle(
                        colors.onSurface,
                        radius = 3.5f * dpx,
                        center = Offset(xOf(targetX), yOf(plan.targetKg)),
                        style = Stroke(width = 1.4f * dpx),
                    )
                    val goalStyle = TextStyle(
                        fontFamily = RobotoMono,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                    )
                    val goalLabel = measurer.measure("${Units.format(plan.targetKg, unit)} goal", goalStyle)
                    drawText(
                        goalLabel,
                        topLeft = Offset(
                            (xOf(targetX) - goalLabel.size.width).coerceAtLeast(ax),
                            yOf(plan.targetKg) + 8f * dpx,
                        ),
                    )
                }

                // Axis figures, always monospaced.
                yTicks.forEach { (kg, label) ->
                    val layout = measurer.measure(label, axisStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            ax - layout.size.width - 5f * dpx,
                            yOf(kg) - layout.size.height / 2f,
                        ),
                    )
                }
                // Dates under the plot, centred on their tick and never overlapping.
                var lastRight = -Float.MAX_VALUE
                dateTicks.forEach { (day, label) ->
                    val layout = measurer.measure(label, axisStyle)
                    val left = (xOf(day) - layout.size.width / 2f)
                        .coerceIn(0f, size.width - layout.size.width)
                    if (left < lastRight + 4f * dpx) return@forEach
                    drawText(layout, topLeft = Offset(left, ay + ah + 5f * dpx))
                    lastRight = left + layout.size.width
                }
            }

            // A small card shows that day's date, weight and difference from plan.
            scrubEntry?.let { (day, entry) ->
                val sx = xOf(day.toFloat())
                val planAt = PlanMath.planKgAt(plan, day)
                val diff = (planAt - entry.kg) * stats.direction
                val tipWidthPx = with(density) { 106.dp.toPx() }
                val left = if (sx > w * 0.55f) sx - tipWidthPx else sx
                val top = max(ayOf(h), yOf(entry.kg) - with(density) { 56.dp.toPx() })
                Column(
                    modifier = Modifier
                        .offsetPx(left, top)
                        .width(106.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.surfaceAlt)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        entry.date.toString(),
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 9.5.sp),
                        color = colors.muted,
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            Units.formatWithUnit(entry.kg, unit),
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                        )
                        Text(
                            (if (diff >= 0) "−" else "+") + Units.format(abs(diff), unit),
                            style = TextStyle(
                                fontFamily = RobotoMono,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = if (diff >= 0) colors.onTrack else colors.behind,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ChartRange.entries.forEach { r ->
                    WtChip(
                        label = r.label,
                        selected = range == r,
                        onClick = { range = r; custom = null; scrubDay = null },
                        modifier = Modifier.testTag("range-${r.name}"),
                    )
                }
            }
            Text(
                text = when {
                    custom == null -> "drag to scrub · pinch to zoom"
                    abs(zoom - 1f) < 0.05f -> "moved · two fingers pan · tap a range to reset"
                    else -> "${"%.1f".format(zoom)}× · two fingers pan · tap a range to reset"
                },
                style = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp),
                color = colors.muted,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }

        // Legend. Amber means only one thing, so the label says it out loud.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(if (behind) "Actual · behind" else "Actual") {
                Box(Modifier.width(14.dp).height(2.dp).background(accent))
            }
            LegendItem("Plan") {
                LineSwatch(colors.muted, dash = 3f, gap = 3f)
            }
            LegendItem("Trend") {
                LineSwatch(colors.onSurface.copy(alpha = 0.55f), dash = 1.5f, gap = 2.5f)
            }
            if (dated) {
                LegendItem("±0.6 band") {
                    Box(
                        Modifier
                            .width(14.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent.copy(alpha = 0.22f))
                    )
                }
            }
        }
    }
}

/** A dashed or dotted legend swatch, so Plan and Trend read as the lines they mark. */
@Composable
private fun LineSwatch(color: Color, dash: Float, gap: Float) {
    Canvas(Modifier.width(14.dp).height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dash * density, gap * density)
            ),
        )
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        swatch()
        Text(label, style = TextStyle(fontSize = 10.5.sp), color = WtTheme.colors.muted)
    }
}

private fun Modifier.offsetPx(x: Float, y: Float) =
    this.offset { IntOffset(x.roundToInt(), y.roundToInt()) }

/**
 * How far the window may ever be panned or zoomed out: from a little before the
 * plan (or the oldest entry, when there is history from before it) to a little
 * after the target date, or after the newest entry when the plan has no date.
 */
internal fun chartBounds(span: Int, firstDay: Int, lastDay: Int): Win =
    Win(min(-2f, firstDay - 2f), max(span, lastDay + 4) + 6f)

/**
 * The window a range chip shows: the whole plan, or the last N days up to today
 * with a short lead for the trend — never further back than there is anything
 * to draw, so "90d" on a fortnight-old plan does not open on ten empty weeks.
 */
internal fun defaultWindow(range: ChartRange, bounds: Win, todayIndex: Int): Win {
    val days = range.days ?: return bounds
    val lead = max(1f, (days * 0.12f).roundToInt().toFloat())
    val x1 = min(bounds.x1, todayIndex + lead)
    val x0 = max(bounds.x0, x1 - days)
    return Win(x0, x1)
}

/** Keep a pinched or panned window inside the bounds without changing its width. */
internal fun clampWindow(win: Win, bounds: Win): Win {
    val width = win.width.coerceIn(MIN_WINDOW_DAYS, bounds.width)
    val x0 = win.x0.coerceIn(bounds.x0, bounds.x1 - width)
    return Win(x0, x0 + width)
}

/** The entries in the window plus the one just outside it on each side. */
private fun entriesAround(entryDays: List<Pair<Int, WeightEntry>>, win: Win): List<Pair<Int, WeightEntry>> {
    if (entryDays.isEmpty()) return entryDays
    val firstInside = entryDays.indexOfFirst { it.first >= win.x0 }.let { if (it < 0) entryDays.size else it }
    val lastInside = entryDays.indexOfLast { it.first <= win.x1 }
    val from = max(0, firstInside - 1)
    val to = min(entryDays.lastIndex, lastInside + 1)
    return if (from <= to) entryDays.subList(from, to + 1) else emptyList()
}

private fun computeYDomain(
    win: Win,
    entryDays: List<Pair<Int, WeightEntry>>,
    plan: Plan,
    stats: PlanStats,
): YDomain {
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    fun include(v: Float) {
        lo = min(lo, v)
        hi = max(hi, v)
    }

    // The plan line where it crosses the visible window, and its tolerance band.
    val planAtStart = PlanMath.planKgAt(plan, floor(win.x0).toInt())
    val planAtEnd = PlanMath.planKgAt(plan, ceil(win.x1).toInt())
    include(planAtStart)
    include(planAtEnd)
    if (stats.dated) {
        include(min(planAtStart, planAtEnd) - PlanMath.TOLERANCE_KG)
        include(max(planAtStart, planAtEnd) + PlanMath.TOLERANCE_KG)
    }

    // The goal marker, but only when it is actually in view.
    val targetDay = if (stats.dated && stats.spanDays > 0) stats.spanDays.toFloat() else win.x1 - 6f
    if (targetDay >= win.x0 && targetDay <= win.x1) include(plan.targetKg)

    entryDays.forEach { (day, entry) ->
        if (day >= win.x0 - 1 && day <= win.x1 + 1) include(entry.kg)
    }

    // Nothing visible at all: fall back to framing the plan itself.
    if (lo > hi) {
        include(plan.startKg)
        include(plan.targetKg)
    }

    val pad = max(0.7f, (hi - lo) * 0.14f)
    return YDomain(lo - pad, hi + pad)
}
