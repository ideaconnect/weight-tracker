package tech.idct.weighttracker.e2e

import android.os.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placing two widgets through the gallery, ending on the launcher with both
 * bound. The pin confirmation is a system dialog, so UiAutomator taps it.
 */
class WidgetsTest : E2eTestBase() {

    @Test
    fun multipleWidgets() {
        resetApp(seed = true, unlock = true)
        launchApp()
        openWidgetGallery()
        screenshot("gallery")
        placeWidget("RING")

        // The pin flow can leave the launcher in front (always after its data
        // was cleared), so the second placement starts from a fresh launch
        // rather than assuming the gallery survived.
        launchApp()
        openWidgetGallery()
        placeWidget("BAR")

        // The two lock-screen sizes were the only widgets no scenario ever placed.
        launchApp()
        openWidgetGallery()
        placeWidget("GLANCE")
        launchApp()
        openWidgetGallery()
        placeWidget("GLANCE_COMPACT")

        device.pressHome()
        SystemClock.sleep(1_500)
        screenshot("launcher-with-widgets")

        val bound = device.executeShellCommand("dumpsys appwidget")
        assertTrue("ring widget must be bound", bound.contains("RingWidgetReceiver"))
        assertTrue("bar widget must be bound", bound.contains("BarWidgetReceiver"))
        assertTrue("wide glance must be bound", bound.contains("GlanceWidgetReceiver"))
        assertTrue("compact glance must be bound", bound.contains("GlanceCompactWidgetReceiver"))
    }

    /**
     * §6: the status colour reaches every widget. The same two widgets, with the
     * behind fixture underneath — rings, bars and percentages all turn amber.
     */
    @Test
    fun widgetsBehindPlan() {
        resetApp(seed = true, behind = true, unlock = true)
        launchApp()
        waitFor("behind", substring = true)
        screenshot("app-behind")

        // The widgets scenario usually ran first and left both widgets on the
        // launcher; when this scenario runs on its own, place them itself.
        val bound = device.executeShellCommand("dumpsys appwidget")
        for ((kind, receiver) in listOf("RING" to "RingWidgetReceiver", "BAR" to "BarWidgetReceiver")) {
            if (!bound.contains(receiver)) {
                launchApp()
                openWidgetGallery()
                placeWidget(kind)
            }
        }

        device.pressHome()
        SystemClock.sleep(2_500) // give Glance a moment to redraw with the amber data
        screenshot("launcher-widgets-behind")

        val after = device.executeShellCommand("dumpsys appwidget")
        assertTrue("ring widget must be bound", after.contains("RingWidgetReceiver"))
        assertTrue("bar widget must be bound", after.contains("BarWidgetReceiver"))
    }

    /**
     * The two widgets that carry a chart, on an otherwise empty launcher so both
     * land on the first page: their sparklines must show a readable scale — round
     * weights down the side, calendar dates underneath — like the app's own chart.
     */
    @Test
    fun chartWidgets() {
        resetApp(seed = true, unlock = true)
        device.executeShellCommand("pm clear com.google.android.apps.nexuslauncher")
        device.pressHome()
        SystemClock.sleep(1_500)

        launchApp()
        openWidgetGallery()
        screenshot("gallery-chart-previews")
        placeWidget("CHART")
        launchApp()
        openWidgetGallery()
        placeWidget("BIG")

        device.pressHome()
        SystemClock.sleep(2_500)
        screenshot("launcher-chart-widgets")

        val bound = device.executeShellCommand("dumpsys appwidget")
        assertTrue("chart widget must be bound", bound.contains("ChartWidgetReceiver"))
        assertTrue("big widget must be bound", bound.contains("BigWidgetReceiver"))
    }
}
