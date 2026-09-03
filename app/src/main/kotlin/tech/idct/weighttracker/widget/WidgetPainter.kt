package tech.idct.weighttracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import tech.idct.weighttracker.domain.ChartScale
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import androidx.core.content.res.ResourcesCompat
import tech.idct.weighttracker.R
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Glance lays out with RemoteViews, which cannot draw arbitrary paths, so the ring,
 * the progress track and the sparklines are painted into bitmaps here and shown as
 * images. The in-app widget previews draw through the same functions, so what the
 * gallery promises is what the launcher shows.
 *
 * Everything here fills the bitmap it is given and carries its own type, so a widget
 * can hand the picture to an ImageView and let the launcher scale it to the cell it
 * really has. That matters because some launchers — HyperOS is the one this was found
 * on — never tell a widget how big its cell became, and a layout that sizes itself in
 * dp from that figure draws a small design in the middle of a large cell.
 */
private val axisDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

/**
 * Where a line reaches the goal: the day, the date to print, the line's colour, and
 * whether it needs a mark of its own — the plan's landing already carries the hollow
 * goal ring, so only the trend's does.
 */
private class Landing(
    val day: Float,
    val date: java.time.LocalDate,
    val color: Int,
    val dot: Boolean,
)

object WidgetPainter {

    /**
     * The 2x2 and lock-screen rings: progress swept clockwise from twelve o'clock,
     * with the percentage inside it.
     *
     * The figures are painted into the bitmap rather than laid over it, so they keep
     * their proportion to the ring at whatever size the image is finally drawn.
     */
    fun ring(
        sizePx: Int,
        progress: Float,
        palette: WidgetPalette,
        label: String? = null,
        caption: String? = null,
        strokeFraction: Float = 0.079f,
        /** Pixels per dp in the bitmap, so a type size can be judged in dp. */
        density: Float = 1f,
    ): Bitmap {
        val size = max(1, sizePx)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val strokePx = size * strokeFraction
        val inset = strokePx / 2f
        val rect = RectF(inset, inset, size - inset, size - inset)

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

        if (label != null) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.onSurface
                textSize = size * 0.215f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.muted
                textSize = size * 0.102f
                textAlign = Paint.Align.CENTER
            }
            // Below this the caption is a smudge rather than a word, and the ring is
            // better off carrying the percentage alone. Judged in dp: the text size is
            // a fraction of a bitmap measured in pixels, so comparing it to a bare 7
            // asks "is the ring at least 68.6 px across", which is 23 dp on a 3x screen
            // — a caption 2 dp tall, kept — and 46 dp on a 1.5x one, where a legible
            // caption is dropped. Both faults hide on whichever screen you look at.
            val shownCaption = caption?.takeIf { captionPaint.textSize >= 7f * density }
            val labelH = -labelPaint.ascent() + labelPaint.descent()
            val captionH = if (shownCaption != null) -captionPaint.ascent() + captionPaint.descent() else 0f
            val top = size / 2f - (labelH + captionH) / 2f
            canvas.drawText(label, size / 2f, top - labelPaint.ascent(), labelPaint)
            if (shownCaption != null) {
                canvas.drawText(
                    shownCaption,
                    size / 2f,
                    top + labelH - captionPaint.ascent(),
                    captionPaint,
                )
            }
        }
        return bitmap
    }

    /**
     * The pill progress bar of sections 6 and 12.
     *
     * Glance has no fractional width, so this used to be a track with an explicitly
     * sized child, measured in dp against the size the launcher reported. On a
     * launcher that reports a cell smaller than the one it gave, a finished plan drew
     * a bar that stopped short of the end. A bitmap has no such doubt: the fill is a
     * fraction of the picture, and the picture is stretched to whatever the track
     * really turns out to be.
     */
    fun track(widthPx: Int, heightPx: Int, progress: Float, palette: WidgetPalette): Bitmap {
        val w = max(1, widthPx)
        val h = max(1, heightPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = h / 2f
        canvas.drawRoundRect(
            RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surfaceAlt },
        )
        val fill = w * progress.coerceIn(0f, 1f)
        if (fill > 0.5f) {
            canvas.drawRoundRect(
                RectF(0f, 0f, max(fill, h.toFloat()), h.toFloat()), r, r,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
            )
        }
        return bitmap
    }

    /**
     * Gridlines and labels for the bigger widgets. A bare sparkline on a 4x4 tile is a
     * squiggle with no scale to read it against; §6 gives the full chart weight labels
     * in a left gutter and dates underneath, and at this size the widget can afford
     * the same. [maxTicks] and [maxDates] are budgets: the labels land on round
     * weights and calendar boundaries, as many as fit.
     */
    class Axes(val unit: WeightUnit, val textSp: Float, val typeface: Typeface)

    /**
     * §12's Roboto Mono, the face the home screen's chart sets its figures in. The
     * widget used Typeface.MONOSPACE, which is whatever the platform calls monospace
     * and is not that font — the single biggest reason the two charts did not look
     * like each other. Cached because a widget redraw should not read a font file.
     */
    @Volatile
    private var mono: Typeface? = null

    fun mono(context: Context): Typeface = mono ?: runCatching {
        ResourcesCompat.getFont(context, R.font.robotomono_regular)
    }.getOrNull().let { it ?: Typeface.MONOSPACE }.also { mono = it }

    /**
     * The width a line of Glance [androidx.glance.text.Text] will take, in dp.
     *
     * Glance cannot measure text, so whether a row of labels fits has to be settled
     * before the layout runs, and the widgets settled it with em counts written as dp
     * — `subSp * 13f`, `c.text(190f)`. Those are the horizontal twin of the bug
     * `Cell.lineH` fixed for columns: type is asked for in sp, the host multiplies it
     * by the reader's text setting, and a row measured in bare dp runs its labels
     * together by exactly that factor. At 1.3x the 4x2's header became one word and
     * the 2x1's "43% of plan" was ellipsised on the strips with the most room for it.
     *
     * Paint measures the face Glance lays out, at the size it will really be, so the
     * question is answered rather than estimated.
     */
    fun textWidthDp(context: Context, text: String, sp: Float, medium: Boolean = false): Float {
        val metrics = context.resources.displayMetrics
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp * metrics.density * context.resources.configuration.fontScale
            typeface = if (medium) mediumFace else Typeface.DEFAULT
        }
        return paint.measureText(text) / metrics.density
    }

    /** Glance's [androidx.glance.text.FontWeight.Medium] is the system's sans-serif-medium. */
    private val mediumFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /**
     * The 4x2 and 4x4 sparklines: the same layers as the home screen's chart, in the
     * same order — gridlines, tolerance band, plan line, trend projection, actual
     * weights and their points — over the whole plan, from the day it started to the
     * target date.
     */
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
        val dotR = 2.6f * density

        val span = max(1, stats.spanDays)
        val lastDay = max(
            stats.daysSinceStart,
            entries.lastOrNull()?.let { PlanMath.dayIndex(plan.startDate, it.date) } ?: 0,
        )
        // The whole plan, always: the day it started to its target date, or to today
        // when today has run past it. A projection that lands later reaches further.
        val finishDay = stats.projectedFinish?.let { PlanMath.dayIndex(plan.startDate, it) } ?: 0
        val xMax = max(max(span, lastDay), finishDay).toFloat().coerceAtLeast(1f)

        // Only entries inside the drawn window own the vertical scale. History from
        // before the plan started maps to a negative x and is clipped away, so
        // letting it widen the range would squash the visible line to nothing.
        val plotted = entries.filter { PlanMath.dayIndex(plan.startDate, it.date) >= 0 }

        // The floor is the lowest weight the chart actually draws — the goal, on a
        // loss plan — and carries no padding under it. Padding there is what left the
        // plan line ending in mid-air with a strip of empty plot beneath it. The
        // tolerance band reaches half a kilogram lower still and is clipped at the
        // floor instead of being allowed to push it down.
        var lo = min(plan.targetKg, plan.startKg)
        var hi = max(plan.targetKg, plan.startKg) + 0.5f
        plotted.forEach {
            lo = min(lo, it.kg)
            hi = max(hi, it.kg + 0.3f)
        }

        // Where each line meets the axis: the plan on its target date, the trend on
        // the date the weights are actually heading for. Both are worth naming — they
        // are the two answers the chart exists to give — so they are found before the
        // gutter is sized, because naming them can cost it a second row.
        // The projection comes first: where a cell has room for only one of them, its
        // dot on the axis would otherwise sit there unexplained, while the plan's
        // landing is the hollow goal ring and reads as the goal without a date on it.
        val landings = if (axes != null && stats.dated) listOfNotNull(
            stats.projectedFinish
                ?.takeIf { finishDay > 0 && finishDay.toFloat() <= xMax }
                ?.let { Landing(finishDay.toFloat(), it, palette.trend, dot = true) },
            plan.startDate.plusDays(span.toLong())
                .takeIf { span.toFloat() <= xMax }
                ?.let { Landing(span.toFloat(), it, palette.muted, dot = false) },
        ) else emptyList()

        // Axes claim a left gutter for the weight labels and a strip underneath for
        // the dates; without them the plot uses the whole bitmap as before.
        val labelPx = (axes?.textSp ?: 0f) * density
        val gutterL = if (axes != null) labelPx * 3.2f else 0f
        fun gutter(rows: Int) = if (axes != null) labelPx * (0.7f + rows * 1.2f) else 0f
        // A second row of dates costs the plot a whole label's height. That is affordable
        // on a cell with room to spare and ruinous on one where the plot is already only
        // three labels tall, so a small cell keeps one row and shows whichever landing
        // fits — the projection, by the order above.
        val dateRows =
            if (landings.size > 1 && heightPx - gutter(2) - padY * 2f >= labelPx * 3f) 2 else 1
        val gutterB = gutter(dateRows)
        val plotL = gutterL + padX
        // The goal marker sits on the plan's last day, so the plot stops a dot short of
        // the right edge rather than drawing half of one.
        val plotR = widthPx - padX - dotR
        val plotT = padY
        val plotB = heightPx - gutterB - padY

        // How many figures the plot can carry, read off the plot rather than fixed by
        // the caller — the home screen's chart sizes its own scales this way, and a
        // widget guessing at three left a 4x4 with a third of the detail it had room
        // for. The caps are the app's own.
        val tickBudget = ((plotB - plotT) / (labelPx * 1.8f)).toInt().coerceIn(2, 6)

        // The scale, found in the display unit so a pound reader gets round pounds.
        // Its first tick is the bottom of the plot, which is what puts the lowest
        // label on the X axis instead of a few pixels above it; it never rises above
        // [lo], so nothing that has to be drawn falls off the bottom.
        val scale = axes?.let {
            ChartScale.axis(Units.toDisplay(lo, it.unit), Units.toDisplay(hi, it.unit), tickBudget)
        }
        if (scale != null && scale.ticks.isNotEmpty()) lo = Units.fromDisplay(scale.lo, axes.unit)

        fun x(day: Float) = plotL + (day / xMax) * (plotR - plotL)
        fun y(kg: Float) = plotT + (hi - kg) / (hi - lo) * (plotB - plotT)

        if (axes != null) {
            // A hairline, as on the home screen, and in the same fainter grey. A
            // device-pixel-wide rule in the outline colour was drawing a cage.
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = palette.grid
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.muted
                textSize = labelPx
                typeface = axes.typeface
                textAlign = Paint.Align.RIGHT
            }
            // Round weights in the display unit, exactly as the full chart labels them.
            scale?.ticks?.forEach { tick ->
                val gy = y(Units.fromDisplay(tick, axes.unit))
                canvas.drawLine(plotL, gy, plotR, gy, gridPaint)
                canvas.drawText(ChartScale.label(tick), plotL - 3f * density, gy + labelPx * 0.36f, labelPaint)
            }
            // The axes themselves, over the gridlines rather than under them: the
            // lowest gridline now lies exactly along the X axis, and a pale rule
            // painted afterwards would rub it out.
            val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * density
                color = palette.muted
                alpha = 120
            }
            canvas.drawLine(plotL, plotT, plotL, plotB, axisPaint)
            canvas.drawLine(plotL, plotB, plotR, plotB, axisPaint)
            // The dates under the axis, in two passes. The landings go first and take
            // whichever row they fit — on a narrow widget the target and the projected
            // finish are a fortnight apart and their labels are not, so the second
            // drops to a row of its own rather than being dropped altogether. The
            // calendar then fills in around whatever they left.
            labelPaint.textAlign = Paint.Align.CENTER
            val taken = Array(dateRows) { mutableListOf<Pair<Float, Float>>() }
            // A landing blocks its column in every row, not just its own: a calendar
            // tick three days from the target is no use stacked under it, and reads as
            // clutter rather than as a scale.
            val claimed = mutableListOf<Pair<Float, Float>>()
            fun place(day: Float, text: String, color: Int, rows: IntRange, marked: Boolean, dot: Boolean) {
                val half = labelPaint.measureText(text) / 2f
                val centre = x(day).coerceIn(half, max(half, widthPx - half))
                val l = centre - half
                val r = centre + half
                val gap = 3f * density
                fun clashes(spans: List<Pair<Float, Float>>) =
                    spans.any { (a, b) -> l < b + gap && a - gap < r }
                if (!marked && clashes(claimed)) return
                val row = rows.firstOrNull { i -> !clashes(taken[i]) } ?: return
                taken[row].add(l to r)
                if (marked) claimed.add(l to r)
                val tick = if (marked) 4.5f * density else 2.5f * density
                canvas.drawLine(
                    x(day), plotB, x(day), plotB + tick,
                    if (marked) Paint(gridPaint).apply { this.color = color } else gridPaint,
                )
                // A dot on the axis itself, so the point is marked and not only dated.
                if (dot) {
                    canvas.drawCircle(
                        x(day), plotB, dotR * 0.62f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
                    )
                }
                labelPaint.color = color
                canvas.drawText(
                    text, centre,
                    plotB + labelPx * (1f + row * 1.2f),
                    labelPaint,
                )
                labelPaint.color = palette.muted
            }
            landings.forEach {
                place(it.day, it.date.format(axisDate), it.color, 0 until dateRows, true, it.dot)
            }
            val dateBudget = ((plotR - plotL) / (labelPaint.measureText("00-00") * 1.7f))
                .toInt().coerceIn(2, 8)
            ChartScale.dateTicks(plan.startDate, 0f, xMax, dateBudget).forEach { date ->
                val day = PlanMath.dayIndex(plan.startDate, date).toFloat()
                place(day, date.format(axisDate), palette.muted, (dateRows - 1) until dateRows, false, false)
            }
        }

        // Nothing below the floor: the band, and the goal marker sitting on it, would
        // otherwise paint down into the strip the dates live in.
        canvas.save()
        canvas.clipRect(0f, 0f, widthPx.toFloat(), plotB)

        if (stats.dated && withBand) {
            val band = Path().apply {
                moveTo(x(0f), y(plan.startKg + PlanMath.TOLERANCE_KG))
                lineTo(x(span.toFloat()), y(plan.targetKg + PlanMath.TOLERANCE_KG))
                lineTo(x(xMax), y(plan.targetKg + PlanMath.TOLERANCE_KG))
                lineTo(x(xMax), y(plan.targetKg - PlanMath.TOLERANCE_KG))
                lineTo(x(span.toFloat()), y(plan.targetKg - PlanMath.TOLERANCE_KG))
                lineTo(x(0f), y(plan.startKg - PlanMath.TOLERANCE_KG))
                close()
            }
            canvas.drawPath(
                band,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = palette.accent
                    alpha = 23 // 9%, as on the home screen
                },
            )
        }

        // §5's plan function: sloped from the start weight to the target, then flat at
        // the target for however much longer the window runs.
        val planPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
            color = palette.muted
            alpha = 217 // 85%
            pathEffect = DashPathEffect(floatArrayOf(2.4f * density, 2.4f * density), 0f)
        }
        if (stats.dated) {
            canvas.drawPath(
                Path().apply {
                    moveTo(x(0f), y(plan.startKg))
                    lineTo(x(span.toFloat()), y(plan.targetKg))
                    if (xMax > span) lineTo(x(xMax), y(plan.targetKg))
                },
                planPaint,
            )
        } else {
            canvas.drawLine(x(0f), y(plan.targetKg), x(xMax), y(plan.targetKg), planPaint)
        }

        // §13's projection, in blue: never the status colour, and never mistakable for
        // the grey plan line it exists to be compared with.
        val last = plotted.lastOrNull()
        if (stats.dated && finishDay > 0 && last != null) {
            val from = PlanMath.dayIndex(plan.startDate, last.date).toFloat()
            canvas.drawLine(
                x(from), y(stats.currentKg), x(finishDay.toFloat()), y(plan.targetKg),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.6f * density
                    color = palette.trend
                    pathEffect = DashPathEffect(floatArrayOf(1.5f * density, 3f * density), 0f)
                },
            )
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
                    strokeWidth = 2.2f * density
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = palette.accent
                },
            )
        }

        // The hairline on today, as on the home screen: it is what makes the drawn
        // half of the plan read as the past and the rest as the plan.
        if (axes != null && stats.daysSinceStart.toFloat() in 0f..xMax) {
            canvas.drawLine(
                x(stats.daysSinceStart.toFloat()), plotT,
                x(stats.daysSinceStart.toFloat()), plotB,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = palette.outline
                },
            )
        }

        // The points get a clip of their own, reaching a little past the axis. The
        // floor is the lowest weight the chart draws — the goal, on a loss plan — so
        // the goal ring and the lowest reading sit exactly ON the axis, and a clip
        // that stops there draws them as half moons. The home screen's chart draws
        // the same marks whole, and drift between the two is a bug.
        canvas.restore()
        canvas.save()
        canvas.clipRect(0f, 0f, widthPx.toFloat(), plotB + 5f * density)

        // A weigh-in is a reading, not a bend in a curve, so each one gets its point —
        // as long as the days stand far enough apart to be read as separate readings.
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        if (plotted.size >= 2 && (plotR - plotL) / xMax >= 6f * density) {
            plotted.forEach {
                val day = PlanMath.dayIndex(plan.startDate, it.date).toFloat()
                canvas.drawCircle(x(day), y(it.kg), 2.2f * density, dotPaint)
            }
        }

        // The goal, hollow, where the plan reaches it.
        if (stats.dated) {
            canvas.drawCircle(
                x(span.toFloat()), y(plan.targetKg), 3.5f * density,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.4f * density
                    color = palette.onSurface
                },
            )
        }

        last?.let {
            val day = PlanMath.dayIndex(plan.startDate, it.date).toFloat()
            canvas.drawCircle(x(day), y(it.kg), 4f * density, dotPaint)
        }
        canvas.restore()
        return bitmap
    }
}
