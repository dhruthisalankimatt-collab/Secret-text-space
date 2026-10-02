package com.dsi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two people, one thread, read mostly at night. The palette is deliberately quiet:
 * a deep pine for your own messages, warm paper for hers, ink for text. No brand
 * colour shouts from the screen, which also means a glance over the shoulder reveals
 * nothing recognisable.
 */
object DsiColors {
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14171A)
    val Muted = Color(0xFF8A8F98)
    val Hairline = Color(0xFFE6E3DD)

    val OutgoingBubble = Color(0xFF1F4E46)
    val OutgoingText = Color(0xFFF6F8F7)
    val IncomingBubble = Color(0xFFF1EEE8)
    val IncomingText = Color(0xFF14171A)

    val Online = Color(0xFF2E9E6B)
    val Danger = Color(0xFFB3261E)

    val NightPaper = Color(0xFF101315)
    val NightInk = Color(0xFFE9E7E2)
    val NightIncoming = Color(0xFF1E2427)
    val NightHairline = Color(0xFF262C30)
}

private val LightScheme = lightColorScheme(
    primary = DsiColors.OutgoingBubble,
    onPrimary = DsiColors.OutgoingText,
    background = DsiColors.Paper,
    onBackground = DsiColors.Ink,
    surface = DsiColors.Paper,
    onSurface = DsiColors.Ink,
    surfaceVariant = DsiColors.IncomingBubble,
    onSurfaceVariant = DsiColors.IncomingText,
    outline = DsiColors.Hairline,
    error = DsiColors.Danger
)

private val DarkScheme = darkColorScheme(
    primary = DsiColors.OutgoingBubble,
    onPrimary = DsiColors.OutgoingText,
    background = DsiColors.NightPaper,
    onBackground = DsiColors.NightInk,
    surface = DsiColors.NightPaper,
    onSurface = DsiColors.NightInk,
    surfaceVariant = DsiColors.NightIncoming,
    onSurfaceVariant = DsiColors.NightInk,
    outline = DsiColors.NightHairline,
    error = DsiColors.Danger
)

private val DsiTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp
    )
)

@Composable
fun DsiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = DsiTypography,
        content = content
    )
}
