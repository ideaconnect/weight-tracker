package tech.idct.weighttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/** Section 12 Shape: hairline 1 px borders instead of shadows. */
@Composable
fun WtCard(
    modifier: Modifier = Modifier,
    radius: Dp = WtDimens.cardRadius,
    background: Color = WtTheme.colors.surface,
    contentPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(WtDimens.hairline, WtTheme.colors.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The grouped-row look used throughout: rows sit on the surface with a 1 px gap
 * that shows the outline colour through, all clipped to one rounded rectangle.
 */
@Composable
fun WtRowGroup(
    modifier: Modifier = Modifier,
    radius: Dp = WtDimens.cardRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(WtTheme.colors.outline)
            .border(WtDimens.hairline, WtTheme.colors.outline, shape),
        verticalArrangement = Arrangement.spacedBy(WtDimens.hairline),
        content = content,
    )
}

@Composable
fun WtRow(
    modifier: Modifier = Modifier,
    background: Color = WtTheme.colors.surface,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** A settings-style section label: 11.5 px, muted, letter-spaced. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.3.sp,
        ),
        color = WtTheme.colors.muted,
    )
}

/**
 * Section 12 Layout: 44 px minimum touch target on every control. Chips grow
 * their hit area without growing visually, so the visible pill stays 34 dp
 * inside a 44 dp box.
 */
@Composable
fun WtChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val accent = WtTheme.accent
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(WtDimens.touchTarget)
            .clip(RoundedCornerShape(WtDimens.touchTarget / 2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (selected) accent else Color.Transparent)
                .border(
                    WtDimens.hairline,
                    if (selected) accent else WtTheme.colors.outline,
                    RoundedCornerShape(17.dp),
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = if (selected) WtTheme.colors.onAccent else WtTheme.colors.muted,
            )
        }
    }
}

/** The three-way plan-mode control and the two-way unit and theme controls. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(WtDimens.rowRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WtTheme.colors.outline)
            .border(WtDimens.hairline, WtTheme.colors.outline, shape),
        horizontalArrangement = Arrangement.spacedBy(WtDimens.hairline),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) WtTheme.colors.onSurface else WtTheme.colors.surface)
                    .clickable { onSelect(index) }
                    .padding(vertical = 13.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = if (on) WtTheme.colors.background else WtTheme.colors.muted,
                )
            }
        }
    }
}

/** A 64x44 hit area holding the 50x30 pill the design draws. */
@Composable
fun WtSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(64.dp, WtDimens.touchTarget)
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp, 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (checked) WtTheme.accent else WtTheme.colors.outline)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WtTheme.colors.background)
            )
        }
    }
}

@Composable
fun WtProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(WtTheme.colors.surfaceAlt),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(shape)
                .background(WtTheme.accent)
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    background: Color = WtTheme.accent,
    contentColor: Color = WtTheme.colors.onAccent,
) {
    val bg = if (enabled) background else WtTheme.colors.surfaceAlt
    val fg = if (enabled) contentColor else WtTheme.colors.muted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = fg)
            Spacer(Modifier.width(9.dp))
        }
        Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = fg)
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(WtDimens.hairline, WtTheme.colors.outline, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WtTheme.colors.onSurface)
            Spacer(Modifier.width(9.dp))
        }
        Text(
            label,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = WtTheme.colors.onSurface,
        )
    }
}

/** A small status pill, for example Connected, Locked, On. */
@Composable
fun WtBadge(
    label: String,
    modifier: Modifier = Modifier,
    contentColor: Color = WtTheme.colors.muted,
    background: Color = WtTheme.colors.surfaceAlt,
    borderColor: Color = WtTheme.colors.outline,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .border(WtDimens.hairline, borderColor, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium), color = contentColor)
    }
}

/** A 44 dp square icon button, used for back arrows and the settings cog. */
@Composable
fun IconTapTarget(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = WtTheme.colors.onSurface,
    iconSize: Dp = 24.dp,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    background: Color = Color.Transparent,
    border: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(WtDimens.touchTarget)
            .clip(shape)
            .background(background)
            .then(if (border) Modifier.border(WtDimens.hairline, WtTheme.colors.outline, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize), tint = tint)
    }
}

/** The plus and minus steppers on the plan and edit sheets. */
@Composable
fun StepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(size / 2)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(WtTheme.colors.surfaceAlt)
            .border(WtDimens.hairline, WtTheme.colors.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontSize = 21.sp), color = WtTheme.colors.onSurface)
    }
}

@Composable
fun MutedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(text, modifier = modifier, style = style, color = WtTheme.colors.muted)
}

/** A minimum-height helper so text rows still meet the 44 dp target. */
fun Modifier.minTouchTarget(): Modifier = defaultMinSize(minHeight = WtDimens.touchTarget)
