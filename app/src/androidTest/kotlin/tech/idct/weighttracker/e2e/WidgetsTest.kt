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
        tapTab("Settings")
        tap("Widgets unlocked")
        waitFor("Unlocked · ads off")
        screenshot("gallery")

        placeWidget("RING")
        placeWidget("BAR")

        device.pressHome()
        SystemClock.sleep(1_500)
        screenshot("launcher-with-widgets")

        val bound = device.executeShellCommand("dumpsys appwidget")
        assertTrue("ring widget must be bound", bound.contains("RingWidgetReceiver"))
        assertTrue("bar widget must be bound", bound.contains("BarWidgetReceiver"))
    }

    private fun placeWidget(kind: String) {
        compose.onNodeWithTag("widget-$kind").performScrollTo().performClick()
        compose.waitForIdle()
        tap("Add to home screen")
        val added = tapSystemButton("Add automatically", "Add to home screen", "ADD", "Add")
        assertTrue("the launcher never offered to pin the widget", added)
        SystemClock.sleep(1_500)
        // The pin flow hands focus back to the app on the placement screen or
        // the gallery; make sure the gallery is in front for the next widget.
        if (!hasNode("Unlocked · ads off")) {
            device.pressBack()
            SystemClock.sleep(600)
        }
        compose.waitForIdle()
    }

    private fun hasNode(text: String): Boolean =
        compose.onAllNodes(
            androidx.compose.ui.test.hasText(text, substring = true)
        ).fetchSemanticsNodes().isNotEmpty()
}
