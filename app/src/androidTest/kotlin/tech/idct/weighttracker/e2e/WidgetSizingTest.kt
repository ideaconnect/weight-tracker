package tech.idct.weighttracker.e2e

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.idct.weighttracker.debug.SeedData
import tech.idct.weighttracker.widget.BarWidget
import tech.idct.weighttracker.widget.BigWidget
import tech.idct.weighttracker.widget.ChartWidget
import tech.idct.weighttracker.widget.GlanceCompactWidget
import tech.idct.weighttracker.widget.GlanceWidget
import tech.idct.weighttracker.widget.RingWidget
import java.io.File

/**
 * Renders every §8 widget at an explicit dp cell and writes a PNG per cell, so a
 * launcher grid can be reproduced without a launcher.
 *
 * A home-screen grid is only ever visible to a widget as the dp size the host
 * reports, and [GlanceAppWidget.compose] takes that size directly. Driving it is
 * therefore the whole of what a denser grid does to the composition, minus the
 * launcher choreography — and unlike a screenshot of a real home screen it can
 * hold every other variable still.
 *
 * The cells below are the same span at two grid densities: a five-row grid, where
 * the repo's screenshots were taken, and a nine-row one. Rows are the axis that
 * moves — nine rows into the same workspace is a little over half the height,
 * while five columns instead of four is four fifths of the width.
 *
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *        tech.idct.weighttracker.e2e.WidgetSizingTest
 */
class WidgetSizingTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app: Context get() = instrumentation.targetContext.applicationContext

    /** Span, then the cell that span becomes on a five-row grid and on a nine-row one. */
    private data class Case(val label: String, val widget: GlanceAppWidget, val cells: List<Pair<Int, Int>>)

    private fun cases() = listOf(
        Case(
            "ring-2x2", RingWidget(),
            listOf(186 to 212, 158 to 140, 158 to 78, 110 to 110),
        ),
        Case(
            "bar-4x2", BarWidget(),
            listOf(386 to 213, 316 to 280, 316 to 156, 250 to 110),
        ),
        Case(
            "chart-4x2", ChartWidget(),
            listOf(386 to 213, 316 to 280, 316 to 156, 250 to 110),
        ),
        Case(
            "big-4x4", BigWidget(),
            listOf(386 to 426, 316 to 560, 316 to 312, 250 to 250),
        ),
        Case(
            "glance-4x1", GlanceWidget(),
            listOf(411 to 146, 316 to 140, 316 to 78, 250 to 60),
        ),
        Case(
            "compact-2x1", GlanceCompactWidget(),
            listOf(186 to 106, 158 to 78, 158 to 40, 110 to 40),
        ),
    )

    @Test
    fun renderEveryWidgetAtEveryGridDensity() {
        runBlocking { SeedData.seed(app, behind = false, unlock = true, reminder = false, reminderMinute = null) }

        val outDir = File(app.getExternalFilesDir(null), "widget-sizing").apply {
            deleteRecursively()
            mkdirs()
        }
        val density = app.resources.displayMetrics.density
        android.util.Log.i(
            "WidgetSizing",
            "density=$density fontScale=${app.resources.configuration.fontScale}",
        )

        cases().forEach { case ->
            case.cells.forEach { (wDp, hDp) ->
                val remoteViews = runBlocking {
                    case.widget.compose(app, size = DpSize(wDp.dp, hDp.dp))
                }

                // RemoteViews inflate and lay out on the main thread, the same as in a host.
                var bitmap: Bitmap? = null
                instrumentation.runOnMainSync {
                    val parent = FrameLayout(app)
                    val view = remoteViews.apply(app, parent)
                    val wPx = (wDp * density).toInt()
                    val hPx = (hDp * density).toInt()
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY),
                    )
                    view.layout(0, 0, wPx, hPx)
                    bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888).also {
                        // A launcher paints the wallpaper behind, so anything the widget
                        // does not cover has to be visible as not covered.
                        Canvas(it).apply {
                            drawColor(Color.MAGENTA)
                            view.draw(this)
                        }
                    }
                }

                val file = File(outDir, "${case.label}_${wDp}x$hDp.png")
                file.outputStream().use { out ->
                    bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                android.util.Log.i("WidgetSizing", "wrote ${file.name}")
            }
        }
    }
}
