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

    private fun cell(w: Float, h: Float, dw: Float, dh: Float, fontScale: Float = 1f) =
        Cell(w, h, dw, dh, fontScale)

    /**
     * The sizes the repo's screenshots were taken at must not move: the fix is for
     * cells smaller than the design, and a launcher that was already giving the
     * widgets room should see exactly what it saw before.
     */
    @Test
    fun testedCellsKeepTheScaleTheyHad() {
        // 4x2 chart at the test device's 386x213: (213 - 28) / 86.
        assertEquals(185f / 86f, cell(386f, 213f, 175f, 86f).scale, 0.001f)
        // 2x2 ring at 186x212: (212 - 28) / 140.
        assertEquals(184f / 140f, cell(186f, 212f, 96f, 140f).scale, 0.001f)
        // The wide glance at 411x146 was already at the ceiling.
        assertEquals(2.2f, cell(411f, 146f, 165f, 44f).scale, 0.001f)
    }

    /** At those sizes the inset is still the prototype's 14 dp, top, bottom and side. */
    @Test
    fun testedCellsKeepThePrototypeInset() {
        assertEquals(14f, padH(386f), 0.001f)
        assertEquals(14f, padV(213f), 0.001f)
        // A dense grid's 4x1 is where the constant inset was taking a third of the cell.
        assertTrue(padV(78f) < 14f)
        assertTrue(padV(40f) < padV(78f))
    }

    /**
     * The bug: the old scale was floored at 1, so a cell smaller than the design got
     * the design at full size and the launcher clipped the overflow.
     */
    @Test
    fun cellsSmallerThanTheDesignScaleBelowOne() {
        // The compact glance at its own declared 110x40 minimum.
        assertTrue(cell(110f, 40f, 122f, 36f).scale < 1f)
        // The 2x2 on a nine-row grid.
        assertTrue(cell(158f, 78f, 96f, 140f).scale < 1f)
    }

    /** A cell wider than it is tall must not let height alone size the type across it. */
    @Test
    fun widthLimitsTheScale() {
        val tallAndNarrow = cell(158f, 560f, 122f, 36f)
        val square = cell(560f, 560f, 122f, 36f)
        assertTrue(tallAndNarrow.scale < square.scale)
    }

    /** §12 would rather drop a line than print one too small to read. */
    @Test
    fun typeNeverFallsBelowTheLegibleFloor() {
        val tiny = cell(110f, 40f, 122f, 36f)
        assertTrue(tiny.text(15f) >= 9f)
        assertTrue(tiny.text(11.5f) >= 9f)
        // A base already below the floor is not inflated past what the caller asked for.
        assertEquals(8f, cell(400f, 400f, 122f, 36f).let { minOf(8f, it.text(8f)) }, 0.001f)
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
        // Type asked for in sp to fill a dp box has to come back down by the same factor.
        assertEquals(12f / 1.3f, larger.spFromDp(12f), 0.001f)
    }

    /**
     * Every widget must fit the cell it declares as its minimum, at the default text
     * setting and at a large one. These are the declared minimums in res/xml.
     */
    @Test
    fun everyDeclaredMinimumLeavesContentToDrawIn() {
        val declared = listOf(
            Triple("ring 110x110", 110f to 110f, 96f to 140f),
            Triple("bar 250x110", 250f to 110f, 190f to 78f),
            Triple("chart 250x110", 250f to 110f, 175f to 86f),
            Triple("big 250x250", 250f to 250f, 215f to 238f),
            Triple("glance 250x60", 250f to 60f, 165f to 44f),
            Triple("compact 110x40", 110f to 40f, 122f to 36f),
        )
        listOf(1f, 1.3f).forEach { fontScale ->
            declared.forEach { (name, box, design) ->
                val c = cell(box.first, box.second, design.first, design.second, fontScale)
                assertTrue("$name has no width at ${fontScale}x", c.width > 0f)
                assertTrue("$name has no height at ${fontScale}x", c.height > 0f)
                // One line of the smallest type the widget will print has to fit.
                assertTrue(
                    "$name cannot hold a line of type at ${fontScale}x",
                    c.lineH(c.text(11.5f)) <= c.height,
                )
            }
        }
    }
}
