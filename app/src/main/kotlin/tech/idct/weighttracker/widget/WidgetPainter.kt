package tech.idct.weighttracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Glance lays out with RemoteViews, which cannot draw arbitrary paths, so the ring
 * and the sparklines are painted into bitmaps here and shown as images.
 */
private val axisDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

object WidgetPainter {

    /** The 2x2 and lock-screen rings: progress swept clockwise from twelve o'clock. */
    fun ring(sizePx: Int, strokePx: Float, progress: Float, palette: WidgetPalette): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = palette.surfaceAlt
        }
        canvas.drawArc(rect, 0f, 360f, false, track)

        if (progress > 0f) {
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = palette.accent
            }
            canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, arc)
        }
        return bitmap
    }

    /**
     * The 4x2 and 4x4 sparklines: actual weight over the plan line and the
     * tolerance band, in the same layer order as the full chart.
     */
    /**
     * Gridlines and labels for the bigger widgets. A bare sparkline on a 4x4 tile is a
     * squiggle with no scale to read it against; §6 gives the full chart weight labels
     * in a left gutter and dates underneath, and at this size the widget can afford
     * the same.
     */
    class Axes(val unit: WeightUnit, val textSp: Float, val ticks: Int = 4, val dates: Int = 3)

    fun sparkline(
        widthPx: Int,
        heightPx: Int,
        entries: List<WeightEntry>,
        stats: PlanStats,
        palette: WidgetPalette,
        withBand: Boolean = true,
        density: Float = 1f,
        axes: Axes? = null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val plan = stats.plan
        val padX = 3f * density
        val padY = 5f * density

        val span = max(1, stats.spanDays)
        val lastDay = max(
            stats.daysSinceStart,
            entries.lastOrNull()?.let { PlanMath.dayIndex(plan.startDate, it.date) } ?: 0,
        )
        val xMax = max(span, lastDay).toFloat()

        // Only entries inside the drawn window own the vertical scale. History from
        // before the plan started maps to a negative x and is clipped away, so
        // letting it widen the range would squash the visible line to nothing.
        val plotted = entries.filter { PlanMath.dayIndex(plan.startDate, it.date) >= 0 }

        var lo = min(plan.targetKg, plan.startKg) - 0.8f
        var hi = max(plan.targetKg, plan.startKg) + 0.5f
        plotted.forEach {
            lo = min(lo, it.kg - 0.3f)
            hi = max(hi, it.kg + 0.3f)
        }

        // Axes claim a left gutter for the weight labels and a strip underneath for
        // the dates; without them the plot uses the whole bitmap as before.
        val labelPx = (axes?.textSp ?: 0f) * density
        val gutterL = if (axes != null) labelPx * 3.2f else 0f
        val gutterB = if (axes != null) labelPx * 1.9f else 0f
        val plotL = gutterL + padX
        val plotR = widthPx - padX
        val plotT = padY
        val plotB = heightPx - gutterB - padY

        fun x(day: Float) = plotL + (day / xMax) * (plotR - plotL)
        fun y(kg: Float) = plotT + (hi - kg) / (hi - lo) * (plotB - plotT)

        if (axes != null) {
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * density
                color = palette.outline
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.muted
                textSize = labelPx
                typeface = Typeface.MONOSPACE
            }
            val step = (hi - lo) / (axes.ticks - 1)
            for (i in 0 until axes.ticks) {
                val v = lo + step * i
                val gy = y(v)
                canvas.drawLine(plotL, gy, plotR, gy, gridPaint)
                val label = Units.format(v, axes.unit)
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawLine(plotL, gy, plotR, gy, gridPaint)
                canvas.drawText(label, plotL - 3f * density, gy + labelPx * 0.36f, labelPaint)
            }
            labelPaint.textAlign = Paint.Align.CENTER
            for (i in 0 until axes.dates) {
                val day = xMax * (i + 0.5f) / axes.dates
                val date = plan.startDate.plusDays(day.toLong())
                canvas.drawText(
                    date.format(axisDate),
                    x(day),
                    heightPx - labelPx * 0.5f,
                    labelPaint,
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
            canvas.drawPath(
                band,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = palette.accent
                    alpha = 26 // ~10%
                },
            )
        }

        val planPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = palette.muted
            pathEffect = DashPathEffect(floatArrayOf(3f * density, 3f * density), 0f)
        }
        if (stats.dated) {
            canvas.drawLine(x(0f), y(plan.startKg), x(span.toFloat()), y(plan.targetKg), planPaint)
        } else {
            canvas.drawLine(x(0f), y(plan.targetKg), x(xMax), y(plan.targetKg), planPaint)
        }

        if (plotted.size >= 2) {
            val path = Path()
            plotted.forEachIndexed { index, entry ->
                val day = PlanMath.dayIndex(plan.startDate, entry.date).toFloat()
                if (index == 0) path.moveTo(x(day), y(entry.kg)) else path.lineTo(x(day), y(entry.kg))
            }
            canvas.drawPath(
                path,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * density
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = palette.accent
                },
            )
        }

        plotted.lastOrNull()?.let { last ->
            val day = PlanMath.dayIndex(plan.startDate, last.date).toFloat()
            canvas.drawCircle(
                x(day),
                y(last.kg),
                3f * density,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
            )
        }
        return bitmap
    }
}
