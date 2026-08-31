package tech.idct.weighttracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.StepperButton
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.LocalDate
import java.time.LocalTime

/** A scrim plus a bottom sheet, matching the flat sheet the design draws. */
@Composable
fun BottomSheetScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = WtTheme.colors
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = WtDimens.sheetRadius, topEnd = WtDimens.sheetRadius))
                        .background(colors.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 22.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(34.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.outline)
                    )
                    Spacer(Modifier.height(14.dp))
                    content()
                }
            }
        }
    }
}

/**
 * Section 7 Log sheet: a numeric keypad, large value, unit, today's date and time.
 * The placeholder shows yesterday's weight; the hint validates plausibility.
 */
@Composable
fun LogSheet(
    unit: WeightUnit,
    lastKnownKg: Float?,
    /** [Format.lastWeighIn]: "Yesterday you were …", or the ISO date when older. */
    lastWeighIn: String?,
    today: LocalDate,
    onSave: (Float) -> Unit,
) {
    val colors = WtTheme.colors
    var typed by remember { mutableStateOf("") }
    val now = remember { LocalTime.now() }

    val parsed = typed.toFloatOrNull()
    val valid = parsed != null && Units.isPlausible(parsed, unit)
    val display = if (typed.isEmpty()) {
        lastKnownKg?.let { Units.format(it, unit) } ?: "0.0"
    } else {
        typed
    }
    val hint = when {
        typed.isEmpty() -> lastWeighIn ?: "Enter today's weight in ${unit.label}"

        valid -> "Saves to today, ${today.format(Format.isoDate)}"
        else -> "Enter a plausible weight"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Log weight",
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = colors.onSurface,
        )
        Text(
            "${today.format(Format.isoDate)} · ${now.format(Format.clock)}",
            style = TextStyle(fontFamily = RobotoMono, fontSize = 12.5.sp),
            color = colors.muted,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            display,
            style = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
            color = if (typed.isEmpty()) colors.muted else colors.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(unit.label, style = TextStyle(fontSize = 17.sp), color = colors.muted)
    }

    Text(
        hint,
        style = TextStyle(fontSize = 12.sp),
        color = if (typed.isNotEmpty() && !valid) colors.behind else colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
    )
    Spacer(Modifier.height(14.dp))

    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(248.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(keys) { key ->
            Box(
                modifier = Modifier
                    // Named because the digit alone is not enough to find a key: with
                    // "7" already on the display, a test asking for the "7" key by its
                    // text matched the merged node above the whole sheet as well, and
                    // clicked its middle — the "5" key.
                    .testTag("key-$key")
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (key == "⌫") Color.Transparent else colors.surfaceAlt)
                    .border(WtDimens.hairline, colors.outline, RoundedCornerShape(14.dp))
                    .clickable {
                        typed = when {
                            key == "⌫" -> typed.dropLast(1)
                            key == "." && typed.contains('.') -> typed
                            key == "." && typed.isEmpty() -> "0."
                            typed.replace(".", "").length >= 4 -> typed
                            else -> typed + key
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(key, style = TextStyle(fontSize = 21.sp), color = colors.onSurface)
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        label = "Save",
        enabled = valid,
        onClick = { parsed?.let(onSave) },
    )
}

/**
 * Section 7 Edit entry sheet: steppers of 0.1, the entry's date, and a line
 * explaining the source consequence.
 */
@Composable
fun EditEntrySheet(
    entry: WeightEntry,
    unit: WeightUnit,
    onSave: (Float) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = WtTheme.colors
    var kg by remember(entry.date) { mutableFloatStateOf(entry.kg) }
    // Stepped in the unit on screen. Converting a 0.1 lb step into kg gives 0.045,
    // which the one-decimal rounding put straight back where it started, so in pounds
    // the buttons did nothing at all.
    fun nudge(steps: Float) {
        val shown = Units.toDisplay(kg, unit) + steps
        kg = Units.roundKg(Units.fromDisplay(shown, unit)).coerceIn(20f, 400f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Edit entry",
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = colors.onSurface,
        )
        Text(
            entry.date.format(Format.isoDate),
            style = TextStyle(fontFamily = RobotoMono, fontSize = 12.5.sp),
            color = colors.muted,
        )
    }

    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", { nudge(-0.1f) })
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                Units.format(kg, unit),
                style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.6).sp),
                color = colors.onSurface,
            )
            Text(unit.label, style = TextStyle(fontSize = 15.sp), color = colors.muted)
        }
        StepperButton("+", { nudge(0.1f) })
    }

    Spacer(Modifier.height(12.dp))
    // Section 4 rule 4: the edit sheet says the source consequence in plain words.
    Text(
        if (entry.source == EntrySource.MANUAL) {
            "Entered by hand. Health Connect will not overwrite it."
        } else {
            "Came from Health Connect. Editing makes it a manual entry, which wins from now on."
        },
        style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        color = colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp, 50.dp)
                .clip(RoundedCornerShape(25.dp))
                .border(WtDimens.hairline, colors.outline, RoundedCornerShape(25.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(WtIcons.Delete, contentDescription = "Delete entry", Modifier.size(21.dp), tint = colors.behind)
        }
        PrimaryButton(
            label = "Save changes",
            icon = WtIcons.Check,
            height = 50.dp,
            onClick = { onSave(kg) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Section 7 Paywall: what the payment covers, one price, no tiers. Section 10
 * requires the same wording here as in the gallery button and the Settings row.
 */
@Composable
fun PaywallSheet(
    price: String?,
    billingAvailable: Boolean,
    message: String?,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Widgets on your home screen",
            style = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
            color = colors.onSurface,
        )
        Text(
            "One payment, yours for good. It also switches the ad banner off everywhere in the app.",
            style = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp),
            color = colors.muted,
        )
    }
    Spacer(Modifier.height(18.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WtDimens.cardRadius))
            .background(colors.outline)
            .border(WtDimens.hairline, colors.outline, RoundedCornerShape(WtDimens.cardRadius)),
        verticalArrangement = Arrangement.spacedBy(WtDimens.hairline),
    ) {
        listOf("Every widget size", "No ad banner", "Lock screen glance").forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceAlt)
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.onTrack))
                Text(line, style = TextStyle(fontSize = 13.5.sp), color = colors.onSurface)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    if (message != null) {
        Text(
            message,
            style = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = colors.behind,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
    PrimaryButton(
        label = price?.let { "Unlock for $it" } ?: "Unlock widgets",
        icon = WtIcons.LockOpen,
        height = 54.dp,
        enabled = billingAvailable,
        onClick = onBuy,
    )
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Text("Keep using the free version", style = TextStyle(fontSize = 13.5.sp), color = colors.muted)
    }
}
