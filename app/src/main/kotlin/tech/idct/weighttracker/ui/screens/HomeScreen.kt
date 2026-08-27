package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.chart.WeightChart
import tech.idct.weighttracker.ui.components.IconTapTarget
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.components.WtProgressBar
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/**
 * Section 7 Home: chart on top; current weight small in the header with a vs-plan
 * chip; two cards; a plan summary row; the sync line; the ad banner at the bottom
 * when not unlocked.
 */
@Composable
fun HomeScreen(
    state: AppUiState,
    syncing: Boolean,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onPlanEdit: () -> Unit,
    onHealthConnect: () -> Unit,
    onLog: () -> Unit,
    onSyncNow: () -> Unit,
    adSlot: @Composable () -> Unit,
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
        // Header: greeting, current weight, vs-plan chip, settings.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    Format.greeting(today = state.today),
                    style = TextStyle(fontSize = 12.5.sp),
                    color = colors.muted,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        state.currentKg?.let { Units.format(it, unit) } ?: "—",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.4).sp,
                        ),
                        color = colors.onSurface,
                    )
                    Text(unit.label, style = TextStyle(fontSize = 13.sp), color = colors.muted)
                    if (stats != null && stats.dated) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.surfaceAlt)
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Text(
                                Format.aheadChip(stats, unit),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                color = WtTheme.accent,
                            )
                        }
                    }
                }
            }
            IconTapTarget(
                icon = WtIcons.Settings,
                onClick = onSettings,
                iconSize = 21.dp,
                background = colors.surface,
                border = true,
                contentDescription = "Settings",
            )
        }

        if (stats != null && state.plan != null) {
            WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 8.dp) {
                Spacer(Modifier.height(4.dp))
                WeightChart(
                    entries = state.entries,
                    plan = state.plan,
                    stats = stats,
                    unit = unit,
                    today = state.today,
                )
            }

            // Two cards: today's target vs actual, last 7 days with the pace the plan asks.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WtCard(modifier = Modifier.weight(1f), contentPadding = 13.dp) {
                    Text("Today's target", style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            Units.format(stats.planKgToday, unit),
                            style = TextStyle(fontSize = 20.sp, letterSpacing = (-0.3).sp),
                            color = colors.onSurface,
                        )
                        Text(unit.label, style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Format.aheadLine(stats, unit),
                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                        color = if (stats.dated) WtTheme.accent else colors.muted,
                    )
                }
                WtCard(modifier = Modifier.weight(1f), contentPadding = 13.dp) {
                    Text("Last 7 days", style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            Format.weekChange(stats, unit),
                            style = TextStyle(fontSize = 20.sp, letterSpacing = (-0.3).sp),
                            color = if ((stats.weekChangeKg ?: 0f) >= 0f) colors.onTrack else colors.behind,
                        )
                        Text(unit.label, style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Format.weekPace(stats, unit),
                        style = TextStyle(fontSize = 11.5.sp),
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Plan summary row.
            WtCard(onClick = onPlan, contentPadding = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Format.planHeadline(stats, unit),
                        style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Text(Format.percent(stats), style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                }
                Spacer(Modifier.height(9.dp))
                WtProgressBar(stats.progress)
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        Format.rateNeeded(stats, unit),
                        style = TextStyle(fontSize = 11.5.sp),
                        color = colors.muted,
                    )
                    Text(Format.projection(stats), style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                }
            }
        } else {
            NoPlanCard(onPlanEdit = onPlanEdit)
        }

        // The sync line, with a manual "Sync now" beside it.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Format.syncLine(state.settings),
                style = TextStyle(fontSize = 11.5.sp),
                color = colors.muted,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .height(WtDimens.touchTarget)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = !syncing) {
                        if (state.settings.healthConnectEnabled) onSyncNow() else onHealthConnect()
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(WtIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onTrack)
                Text(
                    if (syncing) "Syncing…" else if (state.settings.healthConnectEnabled) "Sync now" else "Connect",
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                    color = colors.onTrack,
                )
            }
        }

        // Section 10: one banner, home screen only, never over the chart.
        if (!state.unlocked) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { adSlot() }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun NoPlanCard(onPlanEdit: () -> Unit) {
    val colors = WtTheme.colors
    WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 20.dp) {
        Text(
            "No goal yet",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Set a target weight and the chart gains a plan line to measure against.",
            style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = colors.muted,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Set a goal", onClick = onPlanEdit, icon = WtIcons.Flag, height = 46.dp)
    }
}

/** Section 7 Day one / empty: no chart, a placeholder and two shortcuts. */
@Composable
fun EmptyHomeScreen(
    state: AppUiState,
    onLogFirst: () -> Unit,
    onImport: () -> Unit,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap + 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                state.today.format(Format.isoDate),
                style = TextStyle(fontFamily = RobotoMono, fontSize = 12.5.sp),
                color = colors.muted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WtDimens.cardRadiusLarge))
                .background(colors.surface)
                .border(
                    WtDimens.hairline,
                    colors.outline,
                    RoundedCornerShape(WtDimens.cardRadiusLarge),
                )
                .padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceAlt)
            )
            Text("No weight yet", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                "Log today's weight and the chart starts drawing itself. One number is enough to begin.",
                style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = colors.muted,
                modifier = Modifier.width(250.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            PrimaryButton(
                "Log first weight",
                onClick = onLogFirst,
                icon = WtIcons.Add,
                height = 46.dp,
                background = colors.onSurface,
                contentColor = colors.background,
                modifier = Modifier.width(210.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShortcutCard(
                icon = WtIcons.EcgHeart,
                title = "Import from Health Connect",
                subtitle = "Pull in history your scale already recorded",
                onClick = onImport,
            )
            ShortcutCard(
                icon = WtIcons.Flag,
                title = "Set a goal",
                subtitle = "With a date, or open-ended",
                onClick = onSetGoal,
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = WtTheme.colors
    WtCard(onClick = onClick, contentPadding = 15.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = colors.muted)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
                Text(subtitle, style = TextStyle(fontSize = 12.5.sp), color = colors.muted)
            }
        }
    }
}
