package tech.idct.weighttracker.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LB_PER_KG = 2.20462f

/**
 * §13: unit switching is display-only and must round consistently — one decimal
 * in both kg and lb. Nothing here ever writes back to storage.
 */
object Units {

    fun toDisplay(kg: Float, unit: WeightUnit): Float =
        if (unit == WeightUnit.KG) kg else kg * LB_PER_KG

    fun fromDisplay(value: Float, unit: WeightUnit): Float =
        if (unit == WeightUnit.KG) value else value / LB_PER_KG

    /** One decimal, in the display unit, without a unit suffix. */
    fun format(kg: Float, unit: WeightUnit, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f", toDisplay(kg, unit))

    /** One decimal plus the unit label, e.g. "79.2 kg". */
    fun formatWithUnit(kg: Float, unit: WeightUnit, decimals: Int = 1): String =
        "${format(kg, unit, decimals)} ${unit.label}"

    /** A signed delta using the typographic minus the design uses throughout. */
    fun formatSigned(kg: Float, unit: WeightUnit, decimals: Int = 1): String {
        val sign = if (kg > 0) "+" else "\u2212"
        return sign + format(abs(kg), unit, decimals)
    }

    /** Round a stored kilogram value to the one decimal the schema keeps. */
    fun roundKg(kg: Float): Float = (kg * 10f).roundToInt() / 10f

    /** Plausibility check from the log sheet: 20–400 kg equivalent. */
    fun isPlausible(displayValue: Float, unit: WeightUnit): Boolean {
        val kg = fromDisplay(displayValue, unit)
        return kg > 20f && kg < 400f
    }
}
