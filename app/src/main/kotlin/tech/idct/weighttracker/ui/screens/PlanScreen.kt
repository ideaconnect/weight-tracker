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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.components.WtProgressBar
import tech.idct.weighttracker.ui.components.WtRow
import tech.idct.weighttracker.ui.components.WtRowGroup
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/**
 * Section 7 Plan: start / now / target across the top, progress bar, then lost so
 * far, left to go, needed per day, needed per week, at current pace, and a closing
 * sentence comparing the user's pace to the plan's.
 */
@Composable
fun PlanScreen(
    state: AppUiState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val unit = state.settings.unit
    val stats = state.stats

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Plan", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Box(
                modifier = Modifier
                    .height(WtDimens.touchTarget)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (stats == null) "Set" else "Edit",
                    style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    color = colors.onTrack,
                )
            }
        }

        if (stats == null) {
            WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 20.dp) {
                Text("No plan yet", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Pick a target weight. You can fix a date, fix a weekly pace, or leave it open-ended.",
                    style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = colors.muted,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Set a goal", onClick = onEdit, icon = WtIcons.Flag, height = 46.dp)
            }
            return@Column
        }

        WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 18.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Start", style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                    Text(
                        Units.formatWithUnit(stats.startKg, unit),
                        style = TextStyle(fontSize = 17.sp),
                        color = colors.onSurface,
                    )
                    Text(
                        stats.startDate.format(Format.isoDate),
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp),
                        color = colors.muted,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 0.dp)
                        .height(1.dp)
                        .background(colors.outline)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Now", style = TextStyle(fontSize = 11.5.sp), color = WtTheme.accent)
                    Text(
                        Units.format(stats.currentKg, unit),
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.4).sp,
                        ),
                        color = colors.onSurface,
                    )
                    Text(
                        (stats.lastEntryDate ?: state.today).format(Format.isoDate),
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp),
                        color = colors.muted,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(colors.outline)
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Target", style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                    Text(
                        Units.formatWithUnit(stats.targetKg, unit),
                        style = TextStyle(fontSize = 17.sp),
                        color = colors.onSurface,
                    )
                    Text(
                        stats.targetDate?.format(Format.isoDate) ?: "no date",
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp),
                        color = colors.muted,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            WtProgressBar(stats.progress, height = 8.dp)
            Spacer(Modifier.height(16.dp))

            WtRowGroup(radius = WtDimens.rowRadius) {
                StatLine("Lost so far", Units.formatWithUnit(stats.lostKg, unit), valueColor = colors.onTrack)
                StatLine("Left to go", Units.formatWithUnit(stats.leftKg, unit))
                StatLine(
                    "Needed per day",
                    if (stats.hasRate) Units.formatWithUnit(stats.neededPerDay, unit, 2) else "—",
                )
                StatLine(
                    "Needed per week",
                    if (stats.hasRate) Units.formatWithUnit(stats.neededPerWeek, unit, 2) else "—",
                )
                StatLine(
                    "At current pace",
                    stats.projectedFinish?.format(Format.isoDate) ?: "—",
                    valueColor = WtTheme.accent,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                Format.planNote(stats, unit),
                style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = WtTheme.colors.onSurface,
) {
    WtRow(
        background = WtTheme.colors.surfaceAlt,
        horizontalPadding = 15.dp,
        verticalPadding = 13.dp,
    ) {
        Text(label, style = TextStyle(fontSize = 13.5.sp), color = WtTheme.colors.muted)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}
