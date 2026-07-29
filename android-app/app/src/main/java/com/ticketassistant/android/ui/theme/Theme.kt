package com.ticketassistant.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F4EF),
    onPrimaryContainer = Color(0xFF073B37),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFFC2410C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5D5),
    onTertiaryContainer = Color(0xFF6D2408),
    background = Color(0xFFF5F9F8),
    onBackground = Color(0xFF14201F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14201F),
    surfaceVariant = Color(0xFFEAF2F0),
    onSurfaceVariant = Color(0xFF475D59),
    outline = Color(0xFF9FB7B2),
    outlineVariant = Color(0xFFD5E3E0),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF063E39),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF063E39),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF5A1F05),
    tertiaryContainer = Color(0xFF7C2D12),
    onTertiaryContainer = Color(0xFFFFE5D5),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFE4ECEA),
    surface = Color(0xFF151E1C),
    onSurface = Color(0xFFE4ECEA),
    surfaceVariant = Color(0xFF23312F),
    onSurfaceVariant = Color(0xFFB7C9C5),
    outline = Color(0xFF78908B),
    outlineVariant = Color(0xFF344844),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val TicketTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val TicketShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TicketAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TicketTypography,
        shapes = TicketShapes,
        content = content,
    )
}
