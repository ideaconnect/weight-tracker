package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.IconTapTarget
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.SegmentedControl
import tech.idct.weighttracker.ui.components.StepperButton
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtChip
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max

/**
 * Section 7 Plan edit: target stepper of 0.5, the three-way mode control, then
 * either date options (each showing the pace it implies) or pace options (showing
 * the date it implies).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSave: (targetKg: Float, mode: PlanMode, targetDate: LocalDate?, ratePerWeek: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val unit = state.settings.unit
    val plan = state.plan
    val startKg = plan?.startKg ?: state.currentKg ?: 80f
    val currentKg = state.currentKg ?: startKg
    val today = state.today

    var targetKg by remember { mutableFloatStateOf(plan?.targetKg ?: (startKg - 5f)) }
    var mode by remember { mutableStateOf(plan?.mode ?: PlanMode.BY_DATE) }
    var targetDate by remember {
        mutableStateOf(plan?.targetDate ?: defaultDateOptions(today).first())
    }
    var ratePerWeek by remember { mutableFloatStateOf(plan?.ratePerWeek ?: 0.34f) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateOptions = remember(today, targetDate) {
        (defaultDateOptions(today) + targetDate).distinct().sorted()
    }
    val error = PlanMath.validateTarget(startKg, targetKg)

    // Step by 0.5 in whatever unit is on screen, so the control feels the same in both.
    val step = Units.fromDisplay(0.5f, unit)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap + 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            IconTapTarget(WtIcons.ArrowBack, onBack, contentDescription = "Back")
            Text(
                "Your goal",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                color = colors.onSurface,
            )
        }

        // Target weight.
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                "Target weight",
                style = TextStyle(fontSize = 12.sp),
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp),
            )
            WtCard(contentPadding = 16.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepperButton("−", { targetKg = clampTarget(targetKg - step) }, size = 40.dp)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            Units.format(targetKg, unit),
                            style = TextStyle(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp,
                            ),
                            color = colors.onSurface,
                        )
                        Text(unit.label, style = TextStyle(fontSize = 14.sp), color = colors.muted)
                    }
                    StepperButton("+", { targetKg = clampTarget(targetKg + step) }, size = 40.dp)
                }
            }
            Text(
                text = error ?: "Starting from ${Units.formatWithUnit(startKg, unit)} on " +
                    "${(plan?.startDate ?: today).format(Format.isoDate)}.",
                style = TextStyle(fontSize = 11.5.sp),
                color = if (error != null) colors.behind else colors.muted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // Mode.
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                "What should stay fixed?",
                style = TextStyle(fontSize = 12.sp),
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp),
            )
            SegmentedControl(
                options = listOf("By a date", "At a pace", "No deadline"),
                selectedIndex = when (mode) {
                    PlanMode.BY_DATE -> 0
                    PlanMode.AT_PACE -> 1
                    PlanMode.NO_DEADLINE -> 2
                },
                onSelect = {
                    mode = when (it) {
                        0 -> PlanMode.BY_DATE
                        1 -> PlanMode.AT_PACE
                        else -> PlanMode.NO_DEADLINE
                    }
                },
            )
        }

        when (mode) {
            PlanMode.BY_DATE -> {
                val days = max(1, ChronoUnit.DAYS.between(today, targetDate).toInt())
                val impliedPerDay = abs(currentKg - targetKg) / days
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Reach it by",
                        style = TextStyle(fontSize = 12.sp),
                        color = colors.muted,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    WtCard(contentPadding = 8.dp) {
                        dateOptions.forEach { option ->
                            val optionDays = max(1, ChronoUnit.DAYS.between(today, option).toInt())
                            val perWeek = abs(currentKg - targetKg) / optionDays * 7f
                            DateOptionRow(
                                date = option.format(Format.isoDate),
                                note = "${Units.format(perWeek, unit, 2)} ${unit.label}/wk",
                                selected = option == targetDate,
                                onClick = { targetDate = option },
                            )
                        }
                        DateOptionRow(
                            date = "Pick a date",
                            note = "",
                            selected = false,
                            onClick = { showDatePicker = true },
                        )
                    }
                    WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 15.dp) {
                        Text(
                            "${Units.format(impliedPerDay, unit, 2)} ${unit.label} per day from today",
                            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "That is ${Units.format(impliedPerDay * 7f, unit, 2)} ${unit.label} a week, " +
                                "drawn on the chart as the dashed plan line.",
                            style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                            color = colors.muted,
                        )
                    }
                }
            }

            PlanMode.AT_PACE -> {
                val perDay = ratePerWeek / 7f
                val impliedDays = if (perDay <= 0f) 0 else (abs(startKg - targetKg) / perDay).toInt()
                val impliedDate = (plan?.startDate ?: today).plusDays(impliedDays.toLong())
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Pace per week",
                        style = TextStyle(fontSize = 12.sp),
                        color = colors.muted,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    WtCard(contentPadding = 16.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                Units.format(ratePerWeek, unit, 2),
                                style = TextStyle(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = (-1).sp,
                                ),
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  ${unit.label} / week",
                                style = TextStyle(fontSize = 14.sp),
                                color = colors.muted,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.25f, 0.34f, 0.5f, 0.7f).forEach { candidate ->
                                Box(modifier = Modifier.weight(1f)) {
                                    WtChip(
                                        label = Units.format(candidate, unit, 2),
                                        selected = abs(ratePerWeek - candidate) < 0.01f,
                                        onClick = { ratePerWeek = candidate },
                                        fillWidth = true,
                                    )
                                }
                            }
                        }
                    }
                    WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 15.dp) {
                        Text(
                            "Target reached around ${impliedDate.format(Format.isoDate)}",
                            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Fixing the pace lets the date move. The chart still draws the plan line.",
                            style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                            color = colors.muted,
                        )
                    }
                }
            }

            PlanMode.NO_DEADLINE -> {
                WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 16.dp) {
                    Text(
                        "No daily rate, no plan line",
                        style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "The chart shows your actual weight, the target as a flat line, and how far " +
                            "you are from it. Nothing tells you to hurry.",
                        style = TextStyle(fontSize = 12.5.sp, lineHeight = 20.sp),
                        color = colors.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            label = "Save plan",
            icon = WtIcons.Check,
            enabled = error == null,
            onClick = {
                onSave(
                    Units.roundKg(targetKg),
                    mode,
                    if (mode == PlanMode.BY_DATE) targetDate else null,
                    if (mode == PlanMode.AT_PACE) ratePerWeek else null,
                )
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (picked.isAfter(today)) targetDate = picked
                    }
                    showDatePicker = false
                }) { Text("Choose") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateOptionRow(date: String, note: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WtTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WtDimens.touchTarget)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) colors.surfaceAlt else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            date,
            style = TextStyle(fontFamily = RobotoMono, fontSize = 14.sp),
            color = if (selected) colors.onSurface else colors.muted,
        )
        if (note.isNotEmpty()) {
            Text(
                note,
                style = TextStyle(fontSize = 12.5.sp),
                color = if (selected) colors.onSurface.copy(alpha = 0.7f) else colors.muted,
            )
        }
    }
}

/** Four month-end options, far enough apart that the implied paces differ usefully. */
private fun defaultDateOptions(today: LocalDate): List<LocalDate> =
    listOf(2L, 3L, 4L, 6L).map { today.plusMonths(it).with(TemporalAdjusters.lastDayOfMonth()) }

private fun clampTarget(kg: Float): Float = Units.roundKg(kg.coerceIn(20f, 400f))
