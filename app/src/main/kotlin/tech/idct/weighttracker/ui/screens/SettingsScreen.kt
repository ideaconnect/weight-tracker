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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.ThemeChoice
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.HealthState
import tech.idct.weighttracker.ui.components.SectionLabel
import tech.idct.weighttracker.ui.components.SegmentedControl
import tech.idct.weighttracker.ui.components.WtBadge
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.components.WtRow
import tech.idct.weighttracker.ui.components.WtRowGroup
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/** Section 7 Settings. */
@Composable
fun SettingsScreen(
    state: AppUiState,
    health: HealthState,
    onUnit: (WeightUnit) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onHealthConnect: () -> Unit,
    onBackgroundSync: () -> Unit,
    onReminder: () -> Unit,
    onAccount: () -> Unit,
    onExportCsv: () -> Unit,
    onWidgets: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val settings = state.settings

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap + 2.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("UNITS")
            SegmentedControl(
                options = listOf("Kilograms", "Pounds"),
                selectedIndex = if (settings.unit == WeightUnit.KG) 0 else 1,
                onSelect = { onUnit(if (it == 0) WeightUnit.KG else WeightUnit.LB) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("APPEARANCE")
            SegmentedControl(
                options = listOf("Dark", "Light", "System"),
                selectedIndex = when (settings.theme) {
                    ThemeChoice.DARK -> 0
                    ThemeChoice.LIGHT -> 1
                    ThemeChoice.SYSTEM -> 2
                },
                onSelect = {
                    onTheme(
                        when (it) {
                            0 -> ThemeChoice.DARK
                            1 -> ThemeChoice.LIGHT
                            else -> ThemeChoice.SYSTEM
                        }
                    )
                },
            )
            Text(
                "Dark uses true black so AMOLED panels can switch pixels off.",
                style = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("DATA")
            WtRowGroup {
                WtRow(onClick = onHealthConnect) {
                    Icon(WtIcons.EcgHeart, null, Modifier.size(21.dp), tint = colors.muted)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("Health Connect", style = TextStyle(fontSize = 14.5.sp), color = colors.onSurface)
                        Text(
                            "Autosync on app open",
                            style = TextStyle(fontSize = 11.5.sp),
                            color = colors.muted,
                        )
                    }
                    WtBadge(
                        label = when {
                            !health.available -> "Unavailable"
                            settings.healthConnectEnabled && health.readGranted -> "Connected"
                            else -> "Off"
                        },
                        contentColor = if (settings.healthConnectEnabled && health.readGranted) {
                            colors.onTrack
                        } else {
                            colors.muted
                        },
                    )
                }
                WtRow(onClick = onBackgroundSync) {
                    Icon(WtIcons.Sync, null, Modifier.size(21.dp), tint = colors.muted)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("Background sync", style = TextStyle(fontSize = 14.5.sp), color = colors.onSurface)
                        Text(
                            when {
                                !health.backgroundSupported -> "Needs Android 15 or newer"
                                settings.backgroundSyncEnabled -> "Once a day, app closed"
                                else -> "Off — syncs on app open"
                            },
                            style = TextStyle(fontSize = 11.5.sp),
                            color = colors.muted,
                        )
                    }
                    WtBadge(
                        label = if (settings.backgroundSyncEnabled) "On" else "Off",
                        contentColor = if (settings.backgroundSyncEnabled) colors.onTrack else colors.muted,
                    )
                }
                WtRow(onClick = onReminder) {
                    Icon(WtIcons.Notifications, null, Modifier.size(21.dp), tint = colors.muted)
                    Text(
                        "Daily reminder",
                        style = TextStyle(fontSize = 14.5.sp),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (settings.reminderEnabled) settings.reminderTime.format(Format.clock) else "Off",
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 13.sp),
                        color = colors.muted,
                    )
                }
                WtRow(onClick = onAccount) {
                    Icon(WtIcons.AccountCircle, null, Modifier.size(21.dp), tint = colors.muted)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("Google account", style = TextStyle(fontSize = 14.5.sp), color = colors.onSurface)
                        // Section 11: the account row states which state is active in plain words.
                        // §11 asks for plain words about which state is active. Backup
                        // is not built yet, so this does not pretend otherwise.
                        Text(
                            settings.signedInEmail?.let { "$it · backup not available yet" }
                                ?: "Offline — nothing uploaded",
                            style = TextStyle(fontSize = 11.5.sp),
                            color = colors.muted,
                        )
                    }
                    Text(
                        if (settings.signedInEmail != null) "Sign out" else "Sign in",
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onTrack,
                    )
                }
                WtRow(onClick = onExportCsv) {
                    Icon(WtIcons.Download, null, Modifier.size(21.dp), tint = colors.muted)
                    Text(
                        "Export CSV",
                        style = TextStyle(fontSize = 14.5.sp),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.entries.size} " + if (state.entries.size == 1) "entry" else "entries",
                        style = TextStyle(fontSize = 12.5.sp),
                        color = colors.muted,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("WIDGETS & ADS")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(WtDimens.cardRadius))
                    .background(colors.surface)
                    .border(WtDimens.hairline, colors.outline, RoundedCornerShape(WtDimens.cardRadius))
                    .clickable(onClick = onWidgets)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(WtIcons.Widgets, null, Modifier.size(21.dp), tint = colors.muted)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.unlocked) "Widgets unlocked" else "Widgets & no ads",
                        style = TextStyle(fontSize = 14.5.sp),
                        color = colors.onSurface,
                    )
                    // Section 10: the same wording in all three places.
                    Text(
                        if (state.unlocked) "Ad banner is off. Thanks." else "One payment, no subscription",
                        style = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = colors.muted,
                    )
                }
                if (state.unlocked) {
                    WtBadge("Active", contentColor = colors.onTrack)
                } else {
                    WtBadge(
                        "Unlock",
                        contentColor = colors.onAccent,
                        background = colors.onTrack,
                        borderColor = colors.onTrack,
                    )
                }
            }
        }

        DangerRow(
            icon = WtIcons.DeleteForever,
            label = "Delete all data",
            note = "Can't be undone",
            onClick = onDeleteAll,
        )

        Text(
            "Weight Tracker 1.0 · 2026",
            style = TextStyle(fontSize = 11.sp),
            color = colors.muted,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DangerRow(icon: ImageVector, label: String, note: String, onClick: () -> Unit) {
    val colors = WtTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WtDimens.cardRadius))
            .border(WtDimens.hairline, colors.outline, RoundedCornerShape(WtDimens.cardRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = colors.behind)
        Text(label, style = TextStyle(fontSize = 14.5.sp), color = colors.behind, modifier = Modifier.weight(1f))
        Text(note, style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
    }
    Box(Modifier.size(0.dp))
}
