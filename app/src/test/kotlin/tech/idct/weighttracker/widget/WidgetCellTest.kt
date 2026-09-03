package tech.idct.weighttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The §8 widget fit, pinned at both ends.
 *
 * The whole of the dense-grid bug was arithmetic — invisible in code, obvious in a
 * screenshot — so CLAUDE.md's rule for `PlanMath` applies here for the same reason:
 * fix the maths and extend the test rather than adjust a screen. `WidgetSizingTest`
 * draws the widgets at these same cells on a device; this pins the numbers underneath
 * them without one.
 *
 * The design sizes are the ones the six widgets pass to `cell`.
 */
class WidgetCellTest {

    private fun cell(
        w: Float,
        h: Float,
        dw: Float,
        dh: Float,
        fontScale: Float = 1f,
        maxScale: Float = 2.2f,
    ) = Cell(w, h, dw, dh, fontScale, maxScale)

    /** The declared minimums in res/xml, with the design each widget is drawn to. */
    private val declared = listOf(
        Widget("ring", 110f, 110f, 96f, 104f),
        Widget("bar", 250f, 110f, 190f, 78f),
        Widget("chart", 250f, 110f, 175f, 86f),
        Widget("big", 250f, 250f, 215f, 238f),
        Widget("glance", 250f, 60f, 165f, 44f, maxScale = 1.7f),
        Widget("compact", 110f, 40f, 122f, 36f, maxScale = 1.7f),
    )

    private data class Widget(
        val name: String,
        val minWidth: Float,
        val minHeight: Float,
        val designWidth: Float,
        val designHeight: Float,
        val maxScale: Float = 2.2f,
    )

    /**
     * The bug, and the rule that replaced it.
     *
     * The size a launcher reports is a floor, not a measurement: HyperOS keeps
     * answering with the provider's own declared minimum however large the cell it
     * gave. The old scale read that figure in both directions, so on that launcher a
     * 4x2 drew its design at a fraction of the cell — small type, a chart in half its
     * tile, a margin at each end. A report can never be a reason to draw smaller than
     * the design, because the design fits the smallest cell the widget accepts.
     */
    @Test
    fun theDesignIsNeverDrawnSmallerThanItself() {
        declared.forEach {
            assertTrue(
                "${it.name} shrinks at its own declared minimum",
                cell(it.minWidth, it.minHeight, it.designWidth, it.designHeight).scale >= 1f,
            )
        }
        // Even asked to draw into less than it declares, which the lock screen does.
        assertEquals(1f, cell(90f, 30f, 122f, 36f).scale, 0.001f)
        assertEquals(1f, cell(158f, 78f, 96f, 104f).scale, 0.001f)
        assertEquals(1f, cell(250f, 110f, 215f, 238f).scale, 0.001f)
    }

    /** A launcher that reports honestly still gets the design grown into the cell. */
    @Test
    fun aLargerCellGrowsTheDesign() {
        // The 4x2 chart at the test device's 386x213.
        val big = cell(386f, 213f, 175f, 86f)
        assertTrue(big.scale > 1.9f)
        assertTrue(big.text(20f) > 20f)
        assertTrue(big.space(8f) > 8f)
        // Type grows more slowly than the box, or a large cell is all numerals.
        assertTrue(big.text(20f) / 20f < big.scale)
        assertTrue(big.stroke(8f) / 8f < big.scale)
    }

    /** A cell wider than it is tall must not let height alone size the type across it. */
    @Test
    fun widthLimitsTheScale() {
        val tallAndNarrow = cell(158f, 560f, 122f, 36f)
        val square = cell(560f, 560f, 122f, 36f)
        assertTrue(tallAndNarrow.scale < square.scale)
    }

    /** A lock-screen strip has its own, lower ceiling; the home-screen sizes share one. */
    @Test
    fun growthHasACeiling() {
        assertEquals(2.2f, cell(900f, 900f, 175f, 86f).scale, 0.001f)
        assertEquals(1.7f, cell(900f, 900f, 122f, 36f, maxScale = 1.7f).scale, 0.001f)
    }

    /** At the tested sizes the inset is still the prototype's 14 dp at the sides. */
    @Test
    fun theInsetShrinksOnlyForACellThatCannotSpareIt() {
        assertEquals(14f, padH(386f), 0.001f)
        assertEquals(12f, padV(213f), 0.001f)
        // A 4x1 is where a constant inset was taking a third of the cell.
        assertTrue(padV(78f) < 12f)
        assertTrue(padV(40f) < padV(78f))
        assertTrue(padH(110f) < 14f)
    }

    /**
     * A column measured in bare sp overruns by exactly the reader's text setting,
     * which is the same clipping arriving by a different door.
     */
    @Test
    fun lineHeightCountsTheReadersTextSetting() {
        val default = cell(316f, 156f, 175f, 86f, fontScale = 1f)
        val larger = cell(316f, 156f, 175f, 86f, fontScale = 1.3f)
        assertEquals(default.lineH(12f) * 1.3f, larger.lineH(12f), 0.001f)
    }

    /** The safety net stays out of the way until it is needed, and never collapses. */
    @Test
    fun theSqueezeIsOneWhateverFits() {
        val c = cell(250f, 110f, 190f, 78f)
        assertEquals(1f, c.squeeze(c.height), 0.001f)
        assertEquals(1f, c.squeeze(c.height / 2f), 0.001f)
        assertTrue(c.squeeze(c.height * 2f) < 1f)
        assertEquals(0.6f, c.squeeze(c.height * 100f), 0.001f)
    }

    /**
     * The rule above is only safe while it is true that every design fits the cell its
     * provider declares as a minimum, so that is checked rather than assumed. The row
     * stacks mirror the widgets; change one and this says so.
     */
    @Test
    fun everyDesignFitsTheMinimumItsProviderDeclares() {
        fun needed(name: String, c: Cell): Float = when (name) {
            // Ring: the two captions, with something left over to draw a ring in.
            "ring" -> c.lineH(c.text(12f), 1.5f) + c.lineH(c.text(10.5f), 1.5f) +
                c.space(6f) + 40f
            "bar" -> c.lineH(c.text(24f)) + c.space(10f) * 2f + c.stroke(8f) +
                c.lineH(c.text(11f))
            // Chart: the header, and a plot deep enough to be a plot.
            "chart" -> maxOf(c.lineH(c.text(20f)), c.lineH(c.text(11f)) * 2f) +
                c.space(8f) + 24f
            "big" -> c.lineH(c.text(26f), 1.4f) + c.space(12f) * 3f + c.stroke(6f) +
                (c.lineH(c.text(10f), 1.4f) + 2f + c.lineH(c.text(13.5f), 1.4f) +
                    c.space(10f) * 2f) + 24f
            "glance" -> c.lineH(c.text(15f), 1.3f) + c.lineH(c.text(11.5f), 1.3f) +
                c.space(6f) + c.stroke(6f)
            "compact" -> c.lineH(c.text(15f), 1.3f) + c.lineH(c.text(11.5f), 1.3f)
            else -> error("no row stack for $name")
        }

        // Every home-screen size fits its declared minimum outright. The two
        // lock-screen strips ask for two lines of type inside 40 and 60 dp and do not
        // quite, which is the whole reason the squeeze exists; it must stay a trim.
        val outright = setOf("ring", "bar", "chart", "big")
        declared.forEach {
            val c = cell(it.minWidth, it.minHeight, it.designWidth, it.designHeight)
            val k = c.squeeze(needed(it.name, c))
            if (it.name in outright) {
                assertEquals(
                    "${it.name} overruns its own declared minimum by " +
                        "${needed(it.name, c) - c.height} dp",
                    1f, k, 0.001f,
                )
            }
            assertTrue("${it.name} is squeezed hard at its own minimum", k >= 0.9f)
        }

        // And at a large text setting nothing collapses.
        declared.forEach {
            val c = cell(it.minWidth, it.minHeight, it.designWidth, it.designHeight, fontScale = 1.3f)
            assertTrue(
                "${it.name} collapses at a 1.3x text setting",
                c.squeeze(needed(it.name, c)) >= 0.7f,
            )
        }
    }
}
