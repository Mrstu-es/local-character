package com.localcharacter.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.localcharacter.app.data.settings.ThemeMode

object LocalCharacterColors {
    val Lavender = Color(0xFFB8A1FF)
    val LavenderSoft = Color(0xFFE6DEFF)
    val Night = Color(0xFF121116)
    val NightSurface = Color(0xFF1A181F)
    val NightElevated = Color(0xFF24212B)
    val Cloud = Color(0xFFF8F6FB)
    val Ink = Color(0xFF26222D)
    val Coral = Color(0xFFFFB4A8)
}

private val DarkColors = darkColorScheme(
    primary = LocalCharacterColors.Lavender,
    onPrimary = Color(0xFF2B1D54),
    primaryContainer = Color(0xFF3D2F68),
    secondary = Color(0xFFD0C1E8),
    background = LocalCharacterColors.Night,
    surface = LocalCharacterColors.NightSurface,
    surfaceVariant = LocalCharacterColors.NightElevated,
    outline = Color(0xFF49444F),
    error = LocalCharacterColors.Coral,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF65558F),
    onPrimary = Color.White,
    primaryContainer = LocalCharacterColors.LavenderSoft,
    secondary = Color(0xFF665A70),
    background = LocalCharacterColors.Cloud,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0ECF4),
    outline = Color(0xFF7B7480),
)

val LocalCharacterTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun LocalCharacterTheme(
    mode: ThemeMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val activity = context as? Activity
    activity?.window?.navigationBarColor = scheme.background.value.toInt()
    MaterialTheme(colorScheme = scheme, typography = LocalCharacterTypography, content = content)
}
