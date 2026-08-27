package tech.idct.weighttracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The palette from §12 of the build specification. Both themes are essentially
 * monochrome; colour only ever carries progress meaning.
 */
@Immutable
data class WtColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val onSurface: Color,
    val muted: Color,
    val onTrack: Color,
    val behind: Color,
    val grid: Color,
    /** Mock-launcher wallpaper behind widget previews. */
    val wall: Color,
    /** Text/icon colour that sits on top of [onTrack]. */
    val onAccent: Color,
    val scrim: Color,
)

val DarkWtColors = WtColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D0D),
    surfaceAlt = Color(0xFF161616),
    outline = Color(0xFF242424),
    onSurface = Color(0xFFF3F3F3),
    muted = Color(0xFF8B8B8B),
    onTrack = Color(0xFF4FC97F),
    behind = Color(0xFFE0A44A),
    grid = Color(0xFF1D1D1D),
    wall = Color(0xFF101215),
    onAccent = Color(0xFF00160B),
    scrim = Color(0x8C000000),
)

val LightWtColors = WtColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    surfaceAlt = Color(0xFFF2F2F2),
    outline = Color(0xFFE4E4E4),
    onSurface = Color(0xFF121212),
    muted = Color(0xFF6B6B6B),
    onTrack = Color(0xFF2E9A5E),
    behind = Color(0xFFA9720F),
    grid = Color(0xFFECECEC),
    wall = Color(0xFFDCDEE2),
    onAccent = Color(0xFFFFFFFF),
    scrim = Color(0x8C000000),
)

/** §12 Layout: 16 px screen padding, 14 px between cards, 44 px touch targets. */
object WtDimens {
    val screenPadding = 16.dp
    val cardGap = 14.dp
    val touchTarget = 44.dp
    val cardRadius = 16.dp
    val cardRadiusLarge = 18.dp
    val rowRadius = 14.dp
    val widgetRadius = 24.dp
    val sheetRadius = 26.dp
    val hairline = 1.dp
}
