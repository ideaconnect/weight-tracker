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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.components.WtChip
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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

private data class Win(val x0: Float, val x1: Float) {
    val width: Float get() = x1 - x0
}

private data class YDomain(val lo: Float, val hi: Float)

/**
 * The chart is the top of the home screen and the reason the app exists (section 6).
 * Five layers, drawn in order: gridlines, tolerance band, plan line, trend
 * projection, actual weights.
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
    var zoom by remember { mutableFloatStateOf(1f) }
    var scrubDay by remember { mutableStateOf<Float?>(null) }
    var plotSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

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
    val lastDay = max(todayIndex, entryDays.lastOrNull()?.first ?: todayIndex)
    val dated = plan.mode != PlanMode.NO_DEADLINE
    val span = stats.spanDays

    val win = remember(range, zoom, span, lastDay, todayIndex) {
        computeWindow(range, zoom, span, lastDay, todayIndex)
    }
    val yDom = remember(win, entryDays, plan) { computeYDomain(win, entryDays, plan, stats) }

    // Pixel geometry, in the proportions the prototype lays out (331 x 206 units).
    val w = plotSize.width.toFloat()
    val h = plotSize.height.toFloat()
    val ax = w * 34f / 331f
    val ay = h * 14f / 206f
    val aw = w * 286f / 331f
    val ah = h * 150f / 206f

    fun xOf(day: Float): Float = ax + (day - win.x0) / win.width * aw
    fun yOf(kg: Float): Float = ay + (yDom.hi - kg) / (yDom.hi - yDom.lo) * ah
    fun dayAt(px: Float): Float = win.x0 + (px - ax) / aw * win.width

    val scrubEntry = scrubDay?.let { d -> entryDays.minByOrNull { abs(it.first - d) } }

    Column(modifier = modifier) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(331f / 206f)
                    .onSizeChanged { plotSize = it }
                    .pointerInput(win, entryDays) {
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            var pinchStartDistance = 0f
                            var pinchStartZoom = zoom
                            scrubDay = dayAt(first.position.x)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    // Pinch zooms the visible window around today, 1x to 8x.
                                    val distance = abs(pressed[0].position.x - pressed[1].position.x)
                                    if (pinchStartDistance == 0f) {
                                        pinchStartDistance = max(20f, distance)
                                        pinchStartZoom = zoom
                                    }
                                    zoom = (pinchStartZoom * (distance / pinchStartDistance)).coerceIn(1f, 8f)
                                    scrubDay = null
                                    pressed.forEach { it.consume() }
                                } else {
                                    pinchStartDistance = 0f
                                    val change = pressed.first()
                                    scrubDay = dayAt(change.position.x)
                                    if (change.positionChange() != Offset.Zero) change.consume()
                                }
                            }
                            // Releasing dismisses the scrubber.
                            scrubDay = null
                        }
                    }
            ) {
                if (w <= 0f || h <= 0f) return@Canvas

                val ticks = yTicks(yDom)

                // 1. Horizontal gridlines with weight labels in the left gutter.
                ticks.forEach { v ->
                    val y = yOf(v)
                    drawLine(colors.grid, Offset(ax, y), Offset(size.width * 320f / 331f, y), strokeWidth = 1f)
                }

                // 2. Tolerance band, plan +/- 0.6 kg, at 9% opacity in the status colour.
                if (dated && span > 0) {
                    val band = Path().apply {
                        moveTo(xOf(0f), yOf(plan.startKg + PlanMath.TOLERANCE_KG))
                        lineTo(xOf(span.toFloat()), yOf(plan.targetKg + PlanMath.TOLERANCE_KG))
                        lineTo(xOf(span.toFloat()), yOf(plan.targetKg - PlanMath.TOLERANCE_KG))
                        lineTo(xOf(0f), yOf(plan.startKg - PlanMath.TOLERANCE_KG))
                        close()
                    }
                    drawPath(band, accent, alpha = 0.09f)
                }

                // 3. Plan line: dashed, neutral grey, start to target.
                val dash = PathEffect.dashPathEffect(
                    floatArrayOf(with(density) { 4.dp.toPx() } * 0.6f, with(density) { 4.dp.toPx() } * 0.6f)
                )
                if (dated && span > 0) {
                    drawLine(
                        color = colors.muted,
                        start = Offset(xOf(0f), yOf(plan.startKg)),
                        end = Offset(xOf(span.toFloat()), yOf(plan.targetKg)),
                        strokeWidth = 1.4f * density.density,
                        pathEffect = dash,
                        alpha = 0.85f,
                    )
                } else {
                    drawLine(
                        color = colors.muted,
                        start = Offset(xOf(win.x0), yOf(plan.targetKg)),
                        end = Offset(xOf(win.x1), yOf(plan.targetKg)),
                        strokeWidth = 1.4f * density.density,
                        pathEffect = dash,
                        alpha = 0.85f,
                    )
                }

                // 4. Trend projection: finely dotted, deliberately neutral so it cannot
                //    be confused with a status. Clamped to the right edge of the window.
                val finish = stats.projectedFinish
                if (dated && finish != null) {
                    val finishDay = PlanMath.dayIndex(startDate, finish).toFloat()
                    val xEnd = min(finishDay, win.x1)
                    val f = if (finishDay > lastDay) {
                        ((xEnd - lastDay) / (finishDay - lastDay)).coerceIn(0f, 1f)
                    } else 1f
                    val yEnd = stats.currentKg + (plan.targetKg - stats.currentKg) * f
                    drawLine(
                        color = colors.onSurface,
                        start = Offset(xOf(lastDay.toFloat()), yOf(stats.currentKg)),
                        end = Offset(xOf(xEnd), yOf(yEnd)),
                        strokeWidth = 1.2f * density.density,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(1.5f * density.density, 3.5f * density.density)
                        ),
                        alpha = 0.5f,
                    )
                }

                // 5. Actual weights: solid 2.2 px line in the status colour.
                //    Section 13: gaps are a straight segment between known points.
                val visible = entryDays.filter { it.first >= win.x0 - 3 && it.first <= win.x1 + 3 }
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
                            width = 2.2f * density.density,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }

                // A vertical hairline marks today.
                drawLine(
                    colors.outline,
                    Offset(xOf(todayIndex.toFloat()), ay),
                    Offset(xOf(todayIndex.toFloat()), ay + ah),
                    strokeWidth = 1f,
                )

                // A filled dot on the latest reading, a hollow ring on the target.
                if (entryDays.isNotEmpty()) {
                    drawCircle(
                        accent,
                        radius = 4f * density.density,
                        center = Offset(xOf(lastDay.toFloat()), yOf(stats.currentKg)),
                    )
                }
                val targetX = if (dated && span > 0) span.toFloat() else win.x1 - 6f
                drawCircle(
                    colors.onSurface,
                    radius = 3.5f * density.density,
                    center = Offset(xOf(targetX), yOf(plan.targetKg)),
                    style = Stroke(width = 1.4f * density.density),
                )

                // Scrubber: a vertical line follows the finger, the nearest entry a ring.
                scrubEntry?.let { (day, entry) ->
                    val sx = xOf(day.toFloat())
                    drawLine(colors.onSurface, Offset(sx, ay), Offset(sx, ay + ah), strokeWidth = 1f, alpha = 0.45f)
                    drawCircle(colors.background, radius = 5f * density.density, center = Offset(sx, yOf(entry.kg)))
                    drawCircle(
                        accent,
                        radius = 5f * density.density,
                        center = Offset(sx, yOf(entry.kg)),
                        style = Stroke(width = 2f * density.density),
                    )
                }

                // Axis figures, always monospaced.
                val axisStyle = TextStyle(fontFamily = RobotoMono, fontSize = 9.5.sp, color = colors.muted)
                ticks.forEach { v ->
                    val label = Units.format(v, unit)
                    val layout = measurer.measure(label, axisStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            ax - layout.size.width - 5f * density.density,
                            yOf(v) - layout.size.height / 2f,
                        ),
                    )
                }
                // Four date labels sit under the plot.
                for (i in 0..3) {
                    val d = win.x0 + win.width * (i + 0.5f) / 4f
                    val date = startDate.plusDays(max(0f, d).roundToInt().toLong())
                    val label = date.format(DateTimeFormatter.ofPattern("MM-dd"))
                    val layout = measurer.measure(label, axisStyle)
                    val centre = ax + aw * (i + 0.5f) / 4f
                    drawText(
                        layout,
                        topLeft = Offset(centre - layout.size.width / 2f, ay + ah + 5f * density.density),
                    )
                }
                // The goal marker's own label.
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
                        yOf(plan.targetKg) + 8f * density.density,
                    ),
                )
            }

            // A small card shows that day's date, weight and difference from plan.
            scrubEntry?.let { (day, entry) ->
                val sx = xOf(day.toFloat())
                val planAt = PlanMath.planKgAt(plan, day)
                val diff = (planAt - entry.kg) * stats.direction
                val tipWidthPx = with(density) { 106.dp.toPx() }
                val left = if (sx > w * 0.55f) sx - tipWidthPx else sx
                val top = max(ay, yOf(entry.kg) - with(density) { 56.dp.toPx() })
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ChartRange.entries.forEach { r ->
                    WtChip(
                        label = r.label,
                        selected = range == r,
                        onClick = { range = r; zoom = 1f; scrubDay = null },
                    )
                }
            }
            Text(
                text = if (zoom > 1.05f) "zoom ${"%.1f".format(zoom)}× · tap a range to reset"
                else "drag to scrub · pinch to zoom",
                style = TextStyle(fontSize = 10.5.sp),
                color = colors.muted,
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
                Box(Modifier.width(14.dp).height(1.5.dp).background(colors.muted))
            }
            LegendItem("Trend") {
                Box(Modifier.width(14.dp).height(1.5.dp).background(colors.onSurface.copy(alpha = 0.55f)))
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

/** Five ticks, from the visible data range plus 14% padding. */
private fun yTicks(dom: YDomain): List<Float> {
    val step = (dom.hi - dom.lo) / 4f
    return (0..4).map { dom.lo + step * it }
}

private fun computeWindow(
    range: ChartRange,
    zoom: Float,
    span: Int,
    lastDay: Int,
    todayIndex: Int,
): Win {
    val fullSpan = max(span, lastDay + 4)
    var x0: Float
    var x1: Float
    val days = range.days
    if (days == null) {
        x0 = -2f
        x1 = fullSpan + 6f
    } else {
        x1 = todayIndex + max(2f, (days * 0.12f).roundToInt().toFloat())
        x0 = x1 - days
    }
    // Pinch zooms the visible window around today.
    val centre = todayIndex.toFloat()
    val width = (x1 - x0) / zoom
    x0 = centre - (centre - x0) / zoom
    x1 = x0 + width
    return Win(x0, x1)
}

private fun computeYDomain(
    win: Win,
    entryDays: List<Pair<Int, WeightEntry>>,
    plan: Plan,
    stats: PlanStats,
): YDomain {
    var lo = min(plan.targetKg, PlanMath.planKgAt(plan, win.x1.roundToInt()))
    var hi = plan.startKg
    entryDays.forEach { (day, entry) ->
        if (day >= win.x0 - 1 && day <= win.x1 + 1) {
            lo = min(lo, entry.kg)
            hi = max(hi, entry.kg)
        }
    }
    if (stats.dated) {
        lo = min(lo, plan.targetKg - PlanMath.TOLERANCE_KG)
        hi = max(hi, plan.startKg + PlanMath.TOLERANCE_KG)
    }
    val pad = max(0.7f, (hi - lo) * 0.14f)
    return YDomain(lo - pad, hi + pad)
}
