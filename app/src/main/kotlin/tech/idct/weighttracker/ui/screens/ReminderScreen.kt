package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.components.IconTapTarget
import tech.idct.weighttracker.ui.components.SecondaryButton
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtChip
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.components.WtSwitch
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.LocalTime
import java.util.Locale

private val quickTimes = listOf(
    LocalTime.of(6, 30),
    LocalTime.of(7, 0),
    LocalTime.of(8, 0),
    LocalTime.of(21, 0),
)

/** Section 12: the clock is 24-hour and its digits are the same in every locale. */
private fun twoDigits(value: Int): String = String.format(Locale.US, "%02d", value)

/**
 * Section 7 Reminder: master switch, a large time with quick options, a switch for
 * logging straight from the notification, and a preview of the notification itself.
 *
 * Two lines that are not on the prototype appear only when Android has taken
 * something away: notifications blocked for the app, or exact alarms denied. Both
 * say what that means for the reminder and open the settings page that can
 * change it — the switch would otherwise read "On" indefinitely while nothing
 * arrived.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    state: AppUiState,
    notificationsBlocked: Boolean,
    exactAlarmsDenied: Boolean,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
    onQuickLog: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val settings = state.settings
    var showTimePicker by remember { mutableStateOf(false) }

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
                "Daily reminder",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                color = colors.onSurface,
            )
        }

        WtCard(contentPadding = 16.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Remind me to weigh in",
                        style = TextStyle(fontSize = 14.5.sp),
                        color = colors.onSurface,
                    )
                    Text("Every day, same time", style = TextStyle(fontSize = 12.sp), color = colors.muted)
                }
                WtSwitch(
                    checked = settings.reminderEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("reminderSwitch"),
                )
            }
            if (settings.reminderEnabled && notificationsBlocked) {
                SystemNote(
                    text = "Notifications are turned off for Weight Tracker, so this will not arrive.",
                    action = "Open settings",
                    onAction = onOpenNotificationSettings,
                )
            }
            if (settings.reminderEnabled && exactAlarmsDenied) {
                SystemNote(
                    text = "Without exact alarms Android delivers this within the hour after the time.",
                    action = "Allow exact alarms",
                    onAction = onAllowExactAlarms,
                )
            }
        }

        WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 18.dp) {
            // The hour, colon and minutes are three texts so the colon can be muted;
            // a numeric time is left-to-right in every script, so the row must not
            // mirror under an RTL locale (it read "00:08" for 08:00).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        twoDigits(settings.reminderTime.hour),
                        style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                        color = colors.onSurface,
                    )
                    Text(
                        ":",
                        style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                        color = colors.muted,
                    )
                    Text(
                        twoDigits(settings.reminderTime.minute),
                        style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                        color = colors.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // FlowRow, not Row: at a large font size on a narrow phone the fourth chip
            // was squeezed until its label broke, and it wraps instead.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickTimes.forEach { time ->
                    WtChip(
                        label = "${twoDigits(time.hour)}:${twoDigits(time.minute)}",
                        selected = settings.reminderTime == time,
                        onClick = { onTime(time) },
                    )
                }
            }
        }

        WtCard(contentPadding = 16.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f).padding(end = 14.dp),
                ) {
                    Text(
                        "Log straight from the notification",
                        style = TextStyle(fontSize = 14.5.sp),
                        color = colors.onSurface,
                    )
                    Text(
                        "Type the number without opening the app",
                        style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.muted,
                    )
                }
                WtSwitch(
                    checked = settings.quickLogFromNotification,
                    onCheckedChange = onQuickLog,
                    modifier = Modifier.testTag("quickLogSwitch"),
                )
            }
        }

        SecondaryButton(
            "Preview the notification",
            onClick = onPreview,
            icon = WtIcons.Notifications,
            height = 48.dp,
        )
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.reminderTime.hour,
            initialMinute = settings.reminderTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

/** A factual line about something the system has switched off, with the way to it. */
@Composable
private fun SystemNote(text: String, action: String, onAction: () -> Unit) {
    val colors = WtTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp), color = colors.muted)
        Text(
            action,
            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = colors.onTrack,
            modifier = Modifier.clickable(onClick = onAction).padding(vertical = 4.dp),
        )
    }
}
