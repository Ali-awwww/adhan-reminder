package com.adhanreminder.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AdhanColors {
    // Calm, high-contrast light palette designed for elderly readability
    val lightBackground = Color(0xFFF7F5EE)
    val lightSurface = Color(0xFFFFFFFF)
    val lightRaised = Color(0xFFEBE6D8)
    val lightBorder = Color(0xFFB8B1A0)
    val lightPrimary = Color(0xFF14533C)
    val lightOnPrimary = Color(0xFFFFFFFF)
    val lightPrimaryContainer = Color(0xFFD8EBE1)
    val lightOnPrimaryContainer = Color(0xFF0A2E20)
    val lightSecondaryAccent = Color(0xFF7D5A18)
    val lightText = Color(0xFF101513)
    val lightSecondary = Color(0xFF333D39)
    val lightError = Color(0xFFA61B1B)

    // High-contrast calm dark palette (optional toggle)
    val darkBackground = Color(0xFF0F1412)
    val darkSurface = Color(0xFF1A221F)
    val darkRaised = Color(0xFF232E2A)
    val darkBorder = Color(0xFF3D4B45)
    val darkPrimary = Color(0xFF6BC496)
    val darkOnPrimary = Color(0xFF082216)
    val darkPrimaryContainer = Color(0xFF1A4331)
    val darkOnPrimaryContainer = Color(0xFFD9F2E4)
    val darkText = Color(0xFFF5F3EC)
    val darkSecondary = Color(0xFFC5CCC7)
    val darkError = Color(0xFFFF8A80)
}

object AdhanDimensions {
    val border: Dp = 1.5.dp
    val fieldRadius: Dp = 12.dp
    val cardRadius: Dp = 16.dp
    val pillRadius: Dp = 999.dp
    val touchTarget: Dp = 52.dp
    val buttonHeight: Dp = 60.dp
    val largeButtonHeight: Dp = 68.dp
    val screenPadding: Dp = 20.dp
    val groupGap: Dp = 24.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s48 = 48.dp
}

object AdhanTypeScale {
    val heroCountdown = 46.sp
    val screenTitle = 28.sp
    val cardTitle = 22.sp
    val prayerTime = 24.sp
    val body = 19.sp
    val secondary = 16.sp
    val reasonCode = 16.sp
}
