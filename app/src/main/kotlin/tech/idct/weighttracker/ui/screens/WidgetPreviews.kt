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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.WtProgressBar
import tech.idct.weighttracker.ui.theme.DarkWtColors
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import tech.idct.weighttracker.widget.WidgetPainter
import tech.idct.weighttracker.widget.WidgetPalette
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

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

/**
 * The widget sparkline, drawn by [WidgetPainter] itself into a bitmap the exact
 * size of this box. The preview used to re-implement the painter in Compose and
 * the two drifted — a scale fixed in one was still wrong in the other.
 */
@Composable
fun Sparkline(
    entries: List<WeightEntry>,
    stats: PlanStats,
    modifier: Modifier = Modifier,
    withBand: Boolean = true,
    /**
     * Axes, gridlines and labels, to match what the placed 4x2 and 4x4 widgets draw.
     * There is no default: every chart in the app carries its scales, and the way that
     * stops being true is a caller that quietly leaves them out.
     */
    axes: WidgetPainter.Axes,
) {
    val dark = WtTheme.colors == DarkWtColors
    val behind = WtTheme.behind
    val palette = remember(dark, behind) { WidgetPalette(dark, behind) }
    val density = LocalDensity.current.density
    Box(
        modifier.drawWithCache {
            val bitmap = WidgetPainter.sparkline(
                widthPx = size.width.roundToInt(),
                heightPx = size.height.roundToInt(),
                entries = entries,
                stats = stats,
                palette = palette,
                withBand = withBand,
                density = density,
                axes = axes,
            ).asImageBitmap()
            onDrawBehind { drawImage(bitmap) }
        }
    )
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
            Format.kcalCompact(stats)?.let {
                Text(it, style = TextStyle(fontSize = 10.5.sp), color = colors.muted)
            }
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
                Format.kcalCompact(stats)?.let {
                    Text(it, style = TextStyle(fontSize = 11.sp), color = colors.muted)
                    Spacer(Modifier.weight(1f))
                }
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
                    Format.kcalCompact(stats)?.let {
                        Text(it, style = TextStyle(fontSize = 10.5.sp), color = colors.muted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(
                entries = entries,
                stats = stats,
                modifier = Modifier.fillMaxWidth().weight(1f),
                axes = WidgetPainter.Axes(unit, 9.5f, WidgetPainter.mono(LocalContext.current)),
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
            Sparkline(
                entries, stats, Modifier.fillMaxWidth().height(120.dp),
                axes = WidgetPainter.Axes(unit, 9.5f, WidgetPainter.mono(LocalContext.current)),
            )
            Format.kcalCompact(stats)?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = TextStyle(fontSize = 11.sp),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    // The preview mirrors the widget's own tile, centred both ways.
    Column(
        modifier = modifier.background(colors.surfaceAlt).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center),
            color = colors.muted,
            maxLines = 1,
        )
        Text(
            value,
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            color = valueColor,
            maxLines = 1,
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
                    Format.kcalCompact(stats)
                        ?.let { "${Units.formatWithUnit(stats.leftKg, unit)} left · $it" }
                        ?: "${Units.formatWithUnit(stats.leftKg, unit)} left",
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
