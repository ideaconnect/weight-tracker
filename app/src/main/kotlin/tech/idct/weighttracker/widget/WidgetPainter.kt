package tech.idct.weighttracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.WeightEntry
import kotlin.math.max
import kotlin.math.min

/**
 * Glance lays out with RemoteViews, which cannot draw arbitrary paths, so the ring
 * and the sparklines are painted into bitmaps here and shown as images.
 */
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
    fun sparkline(
        widthPx: Int,
        heightPx: Int,
        entries: List<WeightEntry>,
        stats: PlanStats,
        palette: WidgetPalette,
        withBand: Boolean = true,
        density: Float = 1f,
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

        var lo = min(plan.targetKg, plan.startKg) - 0.8f
        var hi = max(plan.targetKg, plan.startKg) + 0.5f
        entries.forEach {
            lo = min(lo, it.kg - 0.3f)
            hi = max(hi, it.kg + 0.3f)
        }

        fun x(day: Float) = padX + (day / xMax) * (widthPx - 2 * padX)
        fun y(kg: Float) = padY + (hi - kg) / (hi - lo) * (heightPx - 2 * padY)

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

        if (entries.size >= 2) {
            val path = Path()
            entries.forEachIndexed { index, entry ->
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

        entries.lastOrNull()?.let { last ->
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
