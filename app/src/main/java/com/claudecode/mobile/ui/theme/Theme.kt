package com.claudecode.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// CloudCLI Compose 主题
// - 支持 Material 3 动态配色 (Android 12+)
// - 支持亮色/暗色模式切换
// - 非 Android 12 设备使用 CloudCLI 品牌色
// ============================================================

// --- 亮色配色方案 (CloudCLI 品牌色) ---
private val CloudCLILightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

// --- 暗色配色方案 (CloudCLI 品牌色) ---
private val CloudCLIDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

/**
 * CloudCLI 应用主题
 *
 * @param darkTheme 是否使用暗色主题，默认跟随系统设置
 * @param dynamicColor 是否启用 Material 3 动态配色 (仅 Android 12+)，默认为 true
 * @param content 主题包裹的内容
 */
@Composable
fun CloudCLITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 确定最终配色方案:
    // 1. Android 12+ 且启用动态配色 -> 使用系统动态配色
    // 2. 否则 -> 使用 CloudCLI 品牌色
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> CloudCLIDarkColorScheme
        else -> CloudCLILightColorScheme
    }

    // 设置状态栏和导航栏外观，使其与主题协调
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏图标颜色 (亮色模式用深色图标，暗色模式用浅色图标)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            // 设置导航栏图标颜色
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            // 状态栏背景透明，由 Compose 内容控制
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    // 应用 Material 3 主题
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CloudCLITypography,
        content = content
    )
}
