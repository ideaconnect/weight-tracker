package tech.idct.weighttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the notification's inline field accepts, and what it refuses. */
class UnitsParseTest {

    private fun parsed(raw: String) = Units.parseDisplayWeight(raw)

    @Test
    fun `plain decimals`() {
        assertEquals(79.2f, parsed("79.2"))
        assertEquals(79.2f, parsed(" 79.2 "))
        assertEquals(80f, parsed("80"))
    }

    @Test
    fun `comma and arabic decimal separators`() {
        assertEquals(79.2f, parsed("79,2"))
        assertEquals(79.2f, parsed("79٫2"))
    }

    @Test
    fun `digits of other scripts`() {
        assertEquals(79.2f, parsed("٧٩.٢"))
        assertEquals(79.2f, parsed("۷۹٫۲"))
    }

    @Test
    fun `a trailing unit word is tolerated`() {
        assertEquals(79.2f, parsed("79.2 kg"))
        assertEquals(174.6f, parsed("174.6lb"))
        assertEquals(174.6f, parsed("174.6 LBS"))
    }

    @Test
    fun `anything that is not one number is refused, not repaired`() {
        // The old filter turned "7,9" into 7.9 and "8 0.5" into 80.5.
        assertNull(parsed("8 0.5"))
        assertNull(parsed("79..2"))
        assertNull(parsed("1e2"))
        assertNull(parsed("-79"))
        assertNull(parsed("seventy nine"))
        assertNull(parsed("kg"))
        assertNull(parsed(""))
        assertNull(parsed("."))
    }

    @Test
    fun `plausibility bounds in words`() {
        assertEquals("between 20 and 400 kg", Units.plausibleRangeLabel(WeightUnit.KG))
        assertEquals("between 44 and 882 lb", Units.plausibleRangeLabel(WeightUnit.LB))
    }
}
