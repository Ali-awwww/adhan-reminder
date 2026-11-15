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

private val DarkColors = darkColorScheme(
    background = AdhanColors.darkBackground,
    surface = AdhanColors.darkSurface,
    surfaceVariant = AdhanColors.darkRaised,
    primary = AdhanColors.darkText,
    onPrimary = AdhanColors.darkBackground,
    onBackground = AdhanColors.darkText,
    onSurface = AdhanColors.darkText,
    onSurfaceVariant = AdhanColors.darkSecondary,
    outline = AdhanColors.darkBorder,
)

private val LightColors = lightColorScheme(
    background = AdhanColors.lightBackground,
    surface = AdhanColors.lightSurface,
    surfaceVariant = AdhanColors.lightRaised,
    primary = AdhanColors.lightText,
    onPrimary = AdhanColors.lightBackground,
    onBackground = AdhanColors.lightText,
    onSurface = AdhanColors.lightText,
    onSurfaceVariant = AdhanColors.lightSecondary,
    outline = AdhanColors.lightBorder,
)

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_regular))

private val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = AdhanTypeScale.screenTitle),
    headlineMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = AdhanTypeScale.screenTitle),
    headlineLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 44.sp),
    headlineSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.body),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.body),
    bodySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.secondary),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = AdhanTypeScale.prayerTime),
    labelMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = AdhanTypeScale.secondary),
)

@Composable
fun AdhanReminderTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
