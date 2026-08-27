package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.WtBadge
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.theme.RobotoMono
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
    onPlacement: () -> Unit,
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

        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            label = if (locked) "Unlock widgets — one payment" else "See them on the home screen",
            icon = if (locked) WtIcons.LockOpen else WtIcons.AddToHomeScreen,
            height = 64.dp,
            background = if (locked) colors.onTrack else colors.onSurface,
            contentColor = if (locked) colors.onAccent else colors.background,
            onClick = { if (locked) onUnlock() else onPlacement() },
        )
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
        ) { content() }
    }
}

/**
 * Section 7 Home-screen placement: a mock launcher showing the widgets in place,
 * with the long-press instruction and a drop zone, so the user can see the result
 * before leaving the app.
 */
@Composable
fun PlacementScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onWidgetList: () -> Unit,
    onAddToHomeScreen: (WidgetKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val unit = state.settings.unit
    val stats: PlanStats = state.stats ?: run {
        Column(modifier.fillMaxSize().background(colors.wall).padding(24.dp)) {
            Text("Set a goal first", color = colors.onSurface)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.wall)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.unlocked) "Long-press → Widgets → Weight Tracker" else "Preview only until you unlock",
                style = TextStyle(fontSize = 12.5.sp),
                color = colors.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f, fill = false),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.onSurface.copy(alpha = 0.12f))
                    .clickable(onClick = onWidgetList)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "Widget list",
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
            }
        }

        BarWidgetPreview(stats, unit)

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RingWidgetPreview(stats, unit)
            // Matched to the ring's height so the sparkline tile has room to take.
            Column(
                modifier = Modifier.weight(1f).height(168.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(WtDimens.widgetRadius))
                        .background(colors.surface)
                        .border(
                            WtDimens.hairline,
                            colors.outline,
                            RoundedCornerShape(WtDimens.widgetRadius),
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Sparkline(state.entries, stats, Modifier.fillMaxWidth().height(60.dp), withBand = false)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.onSurface.copy(alpha = 0.08f))
                        .border(WtDimens.hairline, colors.outline, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "drop zone",
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp),
                        color = colors.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { HomeScreenIcon(null) }
            HomeScreenIcon("WT")
            HomeScreenIcon(null)
        }

        Spacer(Modifier.height(2.dp))
        if (state.unlocked) {
            PrimaryButton(
                "Add the 4×2 progress bar",
                icon = WtIcons.Add,
                height = 46.dp,
                onClick = { onAddToHomeScreen(WidgetKind.BAR) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(colors.surface)
                .border(WtDimens.hairline, colors.outline, RoundedCornerShape(23.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Open Weight Tracker",
                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun HomeScreenIcon(label: String?) {
    val colors = WtTheme.colors
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(if (label == null) colors.onSurface.copy(alpha = 0.14f) else colors.surface)
            .then(
                if (label != null) Modifier.border(WtDimens.hairline, colors.outline, shape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
        }
    }
}
