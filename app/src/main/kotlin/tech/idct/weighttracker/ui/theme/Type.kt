package tech.idct.weighttracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.R

/**
 * §12 Type: Roboto — 300 for large numerals and the wordmark, 400 for body,
 * 500 for labels and buttons. Roboto Mono for dates and axis figures.
 * Roboto is the platform default sans on Android, so it needs no bundling.
 */
val Roboto = FontFamily.Default

val RobotoMono = FontFamily(
    Font(R.font.robotomono_regular, FontWeight.Normal),
    Font(R.font.robotomono_medium, FontWeight.Medium),
)

/** Dates and axis figures — always monospaced. */
fun mono(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = RobotoMono,
    fontWeight = weight,
    fontSize = size.sp,
)

val WtTypography = Typography(
    displayLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Light, fontSize = 54.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1.6).sp),
    displaySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 26.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
    labelSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
)
