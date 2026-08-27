package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

private val quickTimes = listOf(
    LocalTime.of(6, 30),
    LocalTime.of(7, 0),
    LocalTime.of(8, 0),
    LocalTime.of(21, 0),
)

/**
 * Section 7 Reminder: master switch, a large time with quick options, a switch for
 * logging straight from the notification, and a preview of the notification itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
    onQuickLog: (Boolean) -> Unit,
    onPreview: () -> Unit,
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
                WtSwitch(checked = settings.reminderEnabled, onCheckedChange = onToggle)
            }
        }

        WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 18.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "%02d".format(settings.reminderTime.hour),
                    style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                    color = colors.onSurface,
                )
                Text(
                    ":",
                    style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                    color = colors.muted,
                )
                Text(
                    "%02d".format(settings.reminderTime.minute),
                    style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                quickTimes.forEach { time ->
                    WtChip(
                        label = "%02d:%02d".format(time.hour, time.minute),
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
                WtSwitch(checked = settings.quickLogFromNotification, onCheckedChange = onQuickLog)
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
