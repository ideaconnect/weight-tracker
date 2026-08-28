package tech.idct.weighttracker.e2e

import android.os.SystemClock
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        openGallery()
        screenshot("gallery")
        placeWidget("RING")

        // The pin flow can leave the launcher in front (always after its data
        // was cleared), so the second placement starts from a fresh launch
        // rather than assuming the gallery survived.
        launchApp()
        openGallery()
        placeWidget("BAR")

        device.pressHome()
        SystemClock.sleep(1_500)
        screenshot("launcher-with-widgets")

        val bound = device.executeShellCommand("dumpsys appwidget")
        assertTrue("ring widget must be bound", bound.contains("RingWidgetReceiver"))
        assertTrue("bar widget must be bound", bound.contains("BarWidgetReceiver"))
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

        // The placement scenario usually ran first and left both widgets on the
        // launcher; when this scenario runs on its own, place them itself.
        val bound = device.executeShellCommand("dumpsys appwidget")
        for ((kind, receiver) in listOf("RING" to "RingWidgetReceiver", "BAR" to "BarWidgetReceiver")) {
            if (!bound.contains(receiver)) {
                launchApp()
                openGallery()
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

    private fun openGallery() {
        tapTab("Settings")
        tap("Widgets unlocked")
        waitFor("Unlocked · ads off")
    }

    private fun placeWidget(kind: String) {
        compose.onNodeWithTag("widget-$kind").performScrollTo().performClick()
        compose.waitForIdle()
        tap("Add to home screen")
        val added = tapSystemButton("Add automatically", "Add to home screen", "ADD", "Add")
        assertTrue("the launcher never offered to pin the widget", added)
        SystemClock.sleep(1_500)
    }
}
