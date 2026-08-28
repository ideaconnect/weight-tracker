package tech.idct.weighttracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.SecondaryButton
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/**
 * The finish line: shown once, the moment a logged weight crosses the plan's
 * target. The numbers do the talking — start, target, days — and the user
 * chooses whether the story continues with a new goal or just keeps logging.
 */
@Composable
fun SuccessScreen(
    state: AppUiState,
    onDismiss: () -> Unit,
    onNewGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val stats = state.stats ?: return
    val unit = state.settings.unit

    BackHandler { onDismiss() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
                .padding(top = 64.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .background(colors.surfaceAlt, CircleShape)
                    .border(WtDimens.hairline, colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    WtIcons.Trophy,
                    contentDescription = "Trophy",
                    modifier = Modifier.size(64.dp),
                    tint = colors.onTrack,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "You did it",
                style = TextStyle(
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                ),
                color = colors.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(52.dp).height(2.dp).background(colors.onTrack))
            Spacer(Modifier.height(14.dp))
            Text(
                "Congratulations — you reached " +
                    "${Units.format(stats.plan.targetKg, unit)} ${unit.label}.",
                style = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            WtCard(contentPadding = 18.dp) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(
                        label = "STARTED AT",
                        value = Units.format(stats.plan.startKg, unit),
                        suffix = unit.label,
                        modifier = Modifier.weight(1f),
                    )
                    Figure(
                        label = "NOW",
                        value = Units.format(stats.currentKg, unit),
                        suffix = unit.label,
                        modifier = Modifier.weight(1f),
                    )
                    Figure(
                        label = "DAYS",
                        value = stats.daysSinceStart.coerceAtLeast(1).toString(),
                        suffix = "",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Set a new goal", onClick = onNewGoal, icon = WtIcons.Flag)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Keep tracking", onClick = onDismiss)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, suffix: String, modifier: Modifier = Modifier) {
    val colors = WtTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = TextStyle(fontSize = 10.5.sp, letterSpacing = 0.4.sp),
            color = colors.muted,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = TextStyle(fontFamily = RobotoMono, fontSize = 20.sp),
                color = colors.onSurface,
            )
            if (suffix.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    suffix,
                    style = TextStyle(fontSize = 11.sp),
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}
