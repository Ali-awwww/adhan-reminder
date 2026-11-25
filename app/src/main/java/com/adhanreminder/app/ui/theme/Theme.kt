package com.adhanreminder.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adhanreminder.app.R

private val LightColors = lightColorScheme(
    background = AdhanColors.lightBackground,
    surface = AdhanColors.lightSurface,
    surfaceVariant = AdhanColors.lightRaised,
    primary = AdhanColors.lightPrimary,
    onPrimary = AdhanColors.lightOnPrimary,
    primaryContainer = AdhanColors.lightPrimaryContainer,
    onPrimaryContainer = AdhanColors.lightOnPrimaryContainer,
    secondary = AdhanColors.lightSecondaryAccent,
    onSecondary = AdhanColors.lightOnPrimary,
    onBackground = AdhanColors.lightText,
    onSurface = AdhanColors.lightText,
    onSurfaceVariant = AdhanColors.lightSecondary,
    outline = AdhanColors.lightBorder,
    outlineVariant = AdhanColors.lightBorder,
    error = AdhanColors.lightError,
)

private val DarkColors = darkColorScheme(
    background = AdhanColors.darkBackground,
    surface = AdhanColors.darkSurface,
    surfaceVariant = AdhanColors.darkRaised,
    primary = AdhanColors.darkPrimary,
    onPrimary = AdhanColors.darkOnPrimary,
    primaryContainer = AdhanColors.darkPrimaryContainer,
    onPrimaryContainer = AdhanColors.darkOnPrimaryContainer,
    onBackground = AdhanColors.darkText,
    onSurface = AdhanColors.darkText,
    onSurfaceVariant = AdhanColors.darkSecondary,
    outline = AdhanColors.darkBorder,
    outlineVariant = AdhanColors.darkBorder,
    error = AdhanColors.darkError,
)

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_regular))

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = AdhanTypeScale.heroCountdown, lineHeight = 54.sp),
    headlineLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = AdhanTypeScale.screenTitle, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = AdhanTypeScale.screenTitle, lineHeight = 36.sp),
    titleMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = AdhanTypeScale.cardTitle, lineHeight = 30.sp),
    titleSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.body, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.body, lineHeight = 27.sp),
    bodySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.secondary, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    labelMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = AdhanTypeScale.secondary, lineHeight = 22.sp),
    labelSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = AdhanTypeScale.secondary, lineHeight = 22.sp),
)

@Composable
fun AdhanReminderTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
