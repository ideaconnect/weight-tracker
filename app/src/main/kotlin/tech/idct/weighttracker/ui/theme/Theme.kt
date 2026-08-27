package tech.idct.weighttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import tech.idct.weighttracker.domain.ThemeChoice

private val LocalWtColors: ProvidableCompositionLocal<WtColors> =
    staticCompositionLocalOf { DarkWtColors }

/** Accent for progress-carrying elements: green on or ahead of plan, amber behind. */
private val LocalAccent: ProvidableCompositionLocal<AccentState> =
    staticCompositionLocalOf { AccentState(behind = false) }

data class AccentState(val behind: Boolean)

object WtTheme {
    val colors: WtColors
        @Composable @ReadOnlyComposable get() = LocalWtColors.current

    /**
     * The status colour. Everything progress-related uses this: chart line, band,
     * dots, progress bars, rings, percentage labels, projected finish (§6).
     */
    val accent: androidx.compose.ui.graphics.Color
        @Composable @ReadOnlyComposable get() =
            if (LocalAccent.current.behind) LocalWtColors.current.behind else LocalWtColors.current.onTrack

    val behind: Boolean
        @Composable @ReadOnlyComposable get() = LocalAccent.current.behind
}

@Composable
fun isDark(choice: ThemeChoice): Boolean = when (choice) {
    ThemeChoice.DARK -> true
    ThemeChoice.LIGHT -> false
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun WeightTrackerTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    behindPlan: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(themeChoice)
    val colors = if (dark) DarkWtColors else LightWtColors

    // Material 3 is used for its components, mapped onto the flat palette above.
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.onTrack,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.muted,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.behind,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.onTrack,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.muted,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.behind,
            scrim = colors.scrim,
        )
    }

    CompositionLocalProvider(
        LocalWtColors provides colors,
        LocalAccent provides AccentState(behindPlan),
    ) {
        MaterialTheme(colorScheme = scheme, typography = WtTypography, content = content)
    }
}
