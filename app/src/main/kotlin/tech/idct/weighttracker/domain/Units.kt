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

    /** The same bounds in words, in the display unit: "between 20 and 400 kg". */
    fun plausibleRangeLabel(unit: WeightUnit): String =
        "between ${format(20f, unit, 0)} and ${format(400f, unit, 0)} ${unit.label}"

    /**
     * A weight typed by hand — most often into the notification's inline field,
     * where there is no keypad to keep the input honest. Strict on purpose: the
     * old digits-only filter turned "7٫9" into 79 and "8 0.5" into 80.5. Digits of
     * any script are accepted (Character.digit normalises them), exactly one
     * decimal separator — '.', ',' or the Arabic '٫' — and an optional trailing
     * unit word. Anything else is null.
     */
    fun parseDisplayWeight(raw: String): Float? {
        var text = raw.trim()
        for (suffix in listOf("kgs", "kg", "lbs", "lb")) {
            if (text.endsWith(suffix, ignoreCase = true)) {
                text = text.dropLast(suffix.length).trim()
                break
            }
        }
        if (text.isEmpty()) return null
        val normalised = StringBuilder()
        var separators = 0
        for (c in text) {
            val digit = Character.digit(c, 10)
            when {
                digit >= 0 -> normalised.append('0' + digit)
                c == '.' || c == ',' || c == '٫' -> {
                    separators++
                    normalised.append('.')
                }
                else -> return null
            }
        }
        if (separators > 1 || normalised.none { it.isDigit() }) return null
        return normalised.toString().toFloatOrNull()
    }
}
