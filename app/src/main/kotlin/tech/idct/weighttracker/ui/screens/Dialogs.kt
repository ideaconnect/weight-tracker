package tech.idct.weighttracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import tech.idct.weighttracker.widget.WidgetKind

@Composable
fun DialogScaffold(
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
                )
                .padding(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surface)
                    .border(WtDimens.hairline, colors.outline, RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

/**
 * Section 8 Locked behaviour: tapping a locked preview opens a dialog that names
 * the widget, says it needs the one-time unlock, notes that the unlock covers all
 * five sizes and removes ads, and offers the purchase.
 */
@Composable
fun WidgetInfoDialog(
    kind: WidgetKind,
    unlocked: Boolean,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WtTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(colors.surfaceAlt)
            .border(WtDimens.hairline, colors.outline, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (unlocked) WtIcons.AddToHomeScreen else WtIcons.Lock,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = if (unlocked) colors.onTrack else colors.muted,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (unlocked) "Add the ${kind.title}" else "Widgets are part of the unlock",
            style = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
            color = colors.onSurface,
        )
        Text(
            if (unlocked) {
                "It reads the same plan and updates after every sync, manual or background."
            } else {
                "The ${kind.title} needs the one-time unlock before it can go on your home " +
                    "screen. The unlock covers all five sizes and removes the ad banner."
            },
            style = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp),
            color = colors.muted,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WtDimens.rowRadius))
            .background(colors.outline)
            .border(WtDimens.hairline, colors.outline, RoundedCornerShape(WtDimens.rowRadius)),
        verticalArrangement = Arrangement.spacedBy(WtDimens.hairline),
    ) {
        InfoRow(WtIcons.TouchApp, "Long-press the home screen → Widgets → Weight Tracker")
        InfoRow(WtIcons.AddToHomeScreen, "Or tap a widget here and add it from the app")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryButton(
            label = if (unlocked) "Add to home screen" else "Unlock widgets",
            icon = if (unlocked) WtIcons.Add else WtIcons.LockOpen,
            onClick = onPrimary,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (unlocked) "Close" else "Not now",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = WtTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = colors.muted)
        Text(text, style = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp), color = colors.onSurface)
    }
}

@Composable
fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = WtTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(colors.surfaceAlt)
            .border(WtDimens.hairline, colors.outline, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(WtIcons.DeleteForever, contentDescription = null, modifier = Modifier.size(23.dp), tint = colors.behind)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Delete every entry and your plan?",
            style = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
            color = colors.onSurface,
        )
        Text(
            "Entries, plan and settings are removed from this phone and cannot be recovered. " +
                "Your widget unlock is kept.",
            style = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp),
            color = colors.muted,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryButton(
            label = "Delete everything",
            icon = WtIcons.DeleteForever,
            background = colors.behind,
            contentColor = colors.background,
            onClick = onConfirm,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Keep my data",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = colors.muted,
            )
        }
    }
}

/** Section 7 Reminder: a preview of the notification itself. */
@Composable
fun NotificationPreview(
    body: String,
    lastKnownKg: Float?,
    unit: WeightUnit,
    quickLog: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = WtTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xE0000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(horizontal = 12.dp)
            .padding(top = 44.dp, bottom = 22.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault())
                    ),
                    style = TextStyle(fontSize = 12.5.sp),
                    color = androidx.compose.ui.graphics.Color(0xCCEDEDED),
                )
                Text(
                    java.time.LocalTime.now().format(tech.idct.weighttracker.ui.Format.clock),
                    style = TextStyle(fontSize = 12.5.sp),
                    color = androidx.compose.ui.graphics.Color(0xCCEDEDED),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .border(WtDimens.hairline, colors.outline, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(colors.onTrack))
                    Text(
                        "Weight Tracker",
                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.muted,
                    )
                    Text("· now", style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Morning weigh-in",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Text(
                        body,
                        style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
                        color = colors.muted,
                    )
                }
                if (quickLog) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(colors.surfaceAlt)
                                .border(WtDimens.hairline, colors.outline, RoundedCornerShape(21.dp))
                                .padding(horizontal = 15.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                lastKnownKg?.let {
                                    tech.idct.weighttracker.domain.Units.formatWithUnit(it, unit)
                                } ?: unit.label,
                                style = TextStyle(fontFamily = RobotoMono, fontSize = 13.5.sp),
                                color = colors.muted,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(colors.onTrack)
                                .padding(horizontal = 17.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Log",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                color = colors.onAccent,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        "Open app",
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onTrack,
                    )
                    Text(
                        "Snooze 1h",
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.muted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Tap anywhere to close the preview",
                style = TextStyle(fontSize = 11.5.sp),
                color = androidx.compose.ui.graphics.Color(0x8CEDEDED),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
