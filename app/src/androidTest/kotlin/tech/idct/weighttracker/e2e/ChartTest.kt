package tech.idct.weighttracker.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 6: the chart's ranges, and its three gestures — a drag scrubs, a pinch
 * zooms about the fingers, two fingers moving together pan. The visible window is
 * read back from the chart's own accessibility description, so the assertions
 * are about days on the axis rather than pixels on a screenshot.
 */
class ChartTest : E2eTestBase() {

    @Test
    fun rangesZoomAndPan() {
        resetApp(seed = true)
        launchApp()
        waitFor("79.2", substring = true)
        val wholePlan = chartWindow()
        screenshot("chart-plan")

        compose.onNodeWithTag("range-D90").performClick()
        compose.waitForIdle()
        screenshot("chart-90d")
        compose.onNodeWithTag("range-D30").performClick()
        compose.waitForIdle()
        screenshot("chart-30d")
        compose.onNodeWithTag("range-D7").performClick()
        compose.waitForIdle()
        val week = chartWindow()
        assertNotEquals(wholePlan, week)
        screenshot("chart-7d")

        // Back to the whole plan, then pinch out around the middle: the window
        // narrows and the hint says how to undo it.
        compose.onNodeWithTag("range-PLAN").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chart").performTouchInput {
            pinch(
                start0 = Offset(width * 0.45f, height * 0.5f),
                end0 = Offset(width * 0.2f, height * 0.5f),
                start1 = Offset(width * 0.55f, height * 0.5f),
                end1 = Offset(width * 0.85f, height * 0.5f),
            )
        }
        waitFor("tap a range to reset", substring = true)
        val zoomed = chartWindow()
        assertNotEquals(wholePlan, zoomed)
        screenshot("chart-pinched")

        // Two fingers moving together pan towards the plan's start.
        compose.onNodeWithTag("chart").performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.5f))
            down(1, Offset(width * 0.5f, height * 0.5f))
            repeat(12) {
                updatePointerBy(0, Offset(width * 0.03f, 0f))
                updatePointerBy(1, Offset(width * 0.03f, 0f))
                move()
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        val panned = chartWindow()
        assertNotEquals(zoomed, panned)
        assertTrue("panning right must move the window earlier", panned.first < zoomed.first)
        screenshot("chart-panned")

        // A range chip resets the window; a held finger scrubs to the nearest reading.
        compose.onNodeWithTag("range-D30").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chart").performTouchInput {
            down(Offset(width * 0.6f, height * 0.5f))
            moveBy(Offset(-width * 0.1f, 0f))
        }
        waitFor("vs plan", substring = true)
        screenshot("chart-scrub")
        compose.onNodeWithTag("chart").performTouchInput { up() }
    }

    /** The first and last ISO dates the chart says it is showing. */
    private fun chartWindow(): Pair<String, String> {
        val description = compose.onNodeWithTag("chart")
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        val dates = Regex("\\d{4}-\\d{2}-\\d{2}").findAll(description).map { it.value }.toList()
        check(dates.size == 2) { "unexpected chart description: $description" }
        return dates[0] to dates[1]
    }
}
