package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.WtBadge
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import tech.idct.weighttracker.widget.WidgetKind

/**
 * Section 7 Widgets: live previews of every widget on a wallpaper backdrop,
 * each labelled with its size and marked Locked until purchase. Tapping any
 * preview opens the explainer dialog.
 */
@Composable
fun WidgetsScreen(
    state: AppUiState,
    onTapWidget: (WidgetKind) -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val unit = state.settings.unit
    val stats = state.stats
    val locked = !state.unlocked

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.wall)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap + 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Widgets",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                    color = colors.onSurface,
                )
                Text(
                    if (locked) "Preview · unlock to place them" else "Unlocked · ads off",
                    style = TextStyle(fontSize = 12.sp),
                    color = colors.muted,
                )
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.onTrack)
                        .clickable(onClick = onUnlock)
                        .padding(horizontal = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Unlock",
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onAccent,
                    )
                }
            }
        }

        // The standing note explaining the two ways to add one.
        WtCard(radius = WtDimens.rowRadius, contentPadding = 13.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    WtIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(top = 1.dp),
                    tint = colors.muted,
                )
                Text(
                    "Two ways to add one: long-press your home screen and pick Weight Tracker " +
                        "from the widget list, or tap a widget here and place it from the app.",
                    style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                    color = colors.muted,
                )
            }
        }

        if (stats == null) {
            WtCard(radius = WtDimens.cardRadiusLarge, contentPadding = 20.dp) {
                Text("Set a goal first", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = colors.onSurface)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Every widget shows progress against a plan, so they need a target weight " +
                        "before they have anything to draw.",
                    style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = colors.muted,
                )
            }
            return@Column
        }

        WidgetSection(WidgetKind.RING, locked, onTapWidget) {
            RingWidgetPreview(stats, unit, Modifier)
        }
        WidgetSection(WidgetKind.BAR, locked, onTapWidget) {
            BarWidgetPreview(stats, unit)
        }
        WidgetSection(WidgetKind.CHART, locked, onTapWidget) {
            ChartWidgetPreview(state.entries, stats, unit)
        }
        WidgetSection(WidgetKind.BIG, locked, onTapWidget) {
            BigWidgetPreview(state.entries, stats, unit)
        }
        WidgetSection(WidgetKind.GLANCE, locked, onTapWidget) {
            GlanceWidgetPreview(stats, unit)
        }
        WidgetSection(WidgetKind.GLANCE_COMPACT, locked, onTapWidget) {
            GlanceCompactWidgetPreview(stats, unit)
        }

        if (locked) {
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                label = "Unlock widgets — one payment",
                icon = WtIcons.LockOpen,
                height = 64.dp,
                onClick = onUnlock,
            )
        }
    }
}

@Composable
private fun WidgetSection(
    kind: WidgetKind,
    locked: Boolean,
    onTap: (WidgetKind) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = WtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(kind.sizeLabel, style = TextStyle(fontSize = 11.5.sp), color = colors.muted)
            if (locked) {
                WtBadge("Locked", contentColor = colors.muted)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(WtDimens.widgetRadius))
                .clickable { onTap(kind) }
                .testTag("widget-${kind.name}")
        ) { content() }
    }
}
