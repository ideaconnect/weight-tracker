package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.WtProgressBar
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max
import kotlin.math.min

/**
 * Live in-app previews of the widget sizes, drawn from the same plan and
 * history the placed widgets read.
 */

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
    stroke: Dp = 7.dp,
    content: @Composable () -> Unit = {},
) {
    val colors = WtTheme.colors
    val accent = WtTheme.accent
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = colors.surfaceAlt,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (progress > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun Sparkline(
    entries: List<WeightEntry>,
    stats: PlanStats,
    modifier: Modifier = Modifier,
    withBand: Boolean = true,
    /** Gridlines and labels, to match what the placed 4x4 widget draws. */
    axes: WeightUnit? = null,
) {
    val colors = WtTheme.colors
    val accent = WtTheme.accent
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = RobotoMono, fontSize = 9.5.sp, color = colors.muted)
    Canvas(modifier) {
        val plan = stats.plan
        val padX = 3.dp.toPx()
        val padY = 5.dp.toPx()
        val span = max(1, stats.spanDays)
        val lastDay = max(
            stats.daysSinceStart,
            entries.lastOrNull()?.let { PlanMath.dayIndex(plan.startDate, it.date) } ?: 0,
        )
        val xMax = max(span, lastDay).toFloat().coerceAtLeast(1f)

        // Same windowing as WidgetPainter: pre-plan history is clipped away, so it
        // must not widen the vertical range either.
        val plotted = entries.filter { PlanMath.dayIndex(plan.startDate, it.date) >= 0 }

        var lo = min(plan.targetKg, plan.startKg) - 0.8f
        var hi = max(plan.targetKg, plan.startKg) + 0.5f
        plotted.forEach {
            lo = min(lo, it.kg - 0.3f)
            hi = max(hi, it.kg + 0.3f)
        }

        // Axes claim a left gutter and a bottom strip, exactly as WidgetPainter does.
        val labelPx = if (axes != null) 9.5.sp.toPx() else 0f
        val gutterL = if (axes != null) labelPx * 3.2f else 0f
        val gutterB = if (axes != null) labelPx * 1.9f else 0f
        val plotL = gutterL + padX
        val plotR = size.width - padX
        val plotT = padY
        val plotB = size.height - gutterB - padY

        fun x(day: Float) = plotL + (day / xMax) * (plotR - plotL)
        fun y(kg: Float) = plotT + (hi - kg) / (hi - lo) * (plotB - plotT)

        if (axes != null) {
            val ticks = 4
            val step = (hi - lo) / (ticks - 1)
            for (i in 0 until ticks) {
                val v = lo + step * i
                val gy = y(v)
                drawLine(colors.outline, Offset(plotL, gy), Offset(plotR, gy), strokeWidth = 1f)
                val layout = measurer.measure(Units.format(v, axes), axisStyle)
                drawText(
                    layout,
                    topLeft = Offset(plotL - layout.size.width - 3.dp.toPx(), gy - layout.size.height / 2f),
                )
            }
            for (i in 0 until 3) {
                val day = xMax * (i + 0.5f) / 3f
                val date = plan.startDate.plusDays(day.toLong())
                val layout = measurer.measure(date.format(Format.monthDay), axisStyle)
                drawText(
                    layout,
                    topLeft = Offset(x(day) - layout.size.width / 2f, size.height - layout.size.height),
                )
            }
        }

        if (stats.dated && withBand) {
            val band = Path().apply {
                moveTo(x(0f), y(plan.startKg + PlanMath.TOLERANCE_KG))
                lineTo(x(span.toFloat()), y(plan.targetKg + PlanMath.TOLERANCE_KG))
                lineTo(x(span.toFloat()), y(plan.targetKg - PlanMath.TOLERANCE_KG))
                lineTo(x(0f), y(plan.startKg - PlanMath.TOLERANCE_KG))
                close()
            }
            drawPath(band, accent, alpha = 0.10f)
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        if (stats.dated) {
            drawLine(
                colors.muted,
                Offset(x(0f), y(plan.startKg)),
                Offset(x(span.toFloat()), y(plan.targetKg)),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = dash,
            )
        } else {
            drawLine(
                colors.muted,
                Offset(x(0f), y(plan.targetKg)),
                Offset(x(xMax), y(plan.targetKg)),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = dash,
            )
        }

        if (plotted.size >= 2) {
            val path = Path()
            plotted.forEachIndexed { index, entry ->
                val day = PlanMath.dayIndex(plan.startDate, entry.date).toFloat()
                if (index == 0) path.moveTo(x(day), y(entry.kg)) else path.lineTo(x(day), y(entry.kg))
            }
            drawPath(
                path,
                accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        plotted.lastOrNull()?.let { last ->
            val day = PlanMath.dayIndex(plan.startDate, last.date).toFloat()
            drawCircle(accent, radius = 3.dp.toPx(), center = Offset(x(day), y(last.kg)))
        }
    }
}

@Composable
private fun WidgetSurface(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = WtTheme.colors
    val shape = RoundedCornerShape(WtDimens.widgetRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(WtDimens.hairline, colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun RingWidgetPreview(stats: PlanStats, unit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = WtTheme.colors
    WidgetSurface(modifier.size(168.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProgressRing(stats.progress) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Format.percent(stats),
                        style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                        color = colors.onSurface,
                    )
                    Text("of plan", style = TextStyle(fontSize = 9.sp), color = colors.muted)
                }
            }
            Text(
                "${Units.formatWithUnit(stats.leftKg, unit)} left",
                style = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp),
                color = colors.muted,
            )
        }
    }
}

@Composable
fun BarWidgetPreview(stats: PlanStats, unit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = WtTheme.colors
    WidgetSurface(modifier.fillMaxWidth().height(110.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, unit),
                    style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(unit.label, style = TextStyle(fontSize = 12.sp), color = colors.muted)
                Spacer(Modifier.weight(1f))
                Text(
                    Format.percent(stats),
                    style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    color = WtTheme.accent,
                )
            }
            Spacer(Modifier.height(11.dp))
            WtProgressBar(stats.progress, height = 8.dp)
            Spacer(Modifier.height(11.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Units.formatWithUnit(stats.startKg, unit),
                    style = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp),
                    color = colors.muted,
                )
                Text(
                    "${Units.formatWithUnit(stats.leftKg, unit)} left",
                    style = TextStyle(fontSize = 11.sp),
                    color = colors.muted,
                )
                Text(
                    Units.formatWithUnit(stats.targetKg, unit),
                    style = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp),
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
fun ChartWidgetPreview(
    entries: List<WeightEntry>,
    stats: PlanStats,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    // Laid out the way a 4x2 cell on a phone actually renders it: figures across the
    // top, the chart taking the rest of the width and height.
    WidgetSurface(modifier.fillMaxWidth().height(150.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, unit),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(unit.label, style = TextStyle(fontSize = 11.sp), color = colors.muted)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${Format.weekChange(stats, unit)} / 7d",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = WtTheme.accent,
                    )
                    Text(
                        "${Units.format(stats.targetKg, unit)} goal",
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 10.5.sp),
                        color = colors.muted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(
                entries = entries,
                stats = stats,
                modifier = Modifier.fillMaxWidth().weight(1f),
                axes = unit,
            )
        }
    }
}

@Composable
fun BigWidgetPreview(
    entries: List<WeightEntry>,
    stats: PlanStats,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    WidgetSurface(modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    Units.format(stats.currentKg, unit),
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp),
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(unit.label, style = TextStyle(fontSize = 12.sp), color = colors.muted)
                Spacer(Modifier.weight(1f))
                if (stats.scheduleStarted) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.surfaceAlt)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            Format.aheadChip(stats, unit),
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = WtTheme.accent,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(entries, stats, Modifier.fillMaxWidth().height(120.dp), axes = unit)
            Spacer(Modifier.height(12.dp))
            WtProgressBar(stats.progress)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.outline),
                horizontalArrangement = Arrangement.spacedBy(WtDimens.hairline),
            ) {
                StatCell("Left", Units.formatWithUnit(stats.leftKg, unit), Modifier.weight(1f))
                StatCell(
                    "Per day",
                    if (stats.hasRate) Units.format(stats.neededPerDay, unit, 2) else "—",
                    Modifier.weight(1f),
                )
                StatCell(
                    "Finish",
                    stats.projectedFinish?.format(Format.monthDay) ?: "—",
                    Modifier.weight(1f),
                    valueColor = WtTheme.accent,
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = WtTheme.colors.onSurface,
) {
    val colors = WtTheme.colors
    Column(
        modifier = modifier.background(colors.surfaceAlt).padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = TextStyle(fontSize = 10.sp), color = colors.muted)
        Text(
            value,
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

@Composable
fun GlanceWidgetPreview(stats: PlanStats, unit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = WtTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(shape)
            .background(colors.surface)
            .border(WtDimens.hairline, colors.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(stats.progress, diameter = 36.dp, stroke = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    Units.formatWithUnit(stats.currentKg, unit),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
                Text(
                    "${Units.formatWithUnit(stats.leftKg, unit)} left",
                    style = TextStyle(fontSize = 11.5.sp),
                    color = colors.muted,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                Format.percent(stats),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = WtTheme.accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        WtProgressBar(stats.progress)
    }
}

@Composable
fun GlanceCompactWidgetPreview(stats: PlanStats, unit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = WtTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .width(186.dp)
            .height(64.dp)
            .clip(shape)
            .background(colors.surface)
            .border(WtDimens.hairline, colors.outline, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(stats.progress, diameter = 36.dp, stroke = 4.dp)
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                Units.formatWithUnit(stats.currentKg, unit),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
            Text(
                "${Format.percent(stats)} of plan",
                style = TextStyle(fontSize = 11.5.sp),
                color = WtTheme.accent,
            )
        }
    }
}
