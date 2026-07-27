package com.claudecode.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// CloudCLI 品牌色系定义
// 主色: #D97757 (橙棕色 / 陶土色)
// 次色: #5E6AD2 (蓝紫色)
// ============================================================

// --- 主色 (Primary - 橙棕色) ---
val Primary = Color(0xFFD97757)           // 品牌主色
val OnPrimary = Color(0xFFFFFFFF)          // 主色上的文字/图标色
val PrimaryContainer = Color(0xFFF5DBCE)   // 主色容器背景 (浅色)
val OnPrimaryContainer = Color(0xFF3D1700) // 主色容器上的文字

// --- 次色 (Secondary - 蓝紫色) ---
val Secondary = Color(0xFF5E6AD2)          // 品牌次色
val OnSecondary = Color(0xFFFFFFFF)        // 次色上的文字/图标色
val SecondaryContainer = Color(0xFFE0E0FF) // 次色容器背景 (浅色)
val OnSecondaryContainer = Color(0xFF131A4D) // 次色容器上的文字

// --- 第三色 (Tertiary - 用于辅助强调) ---
val Tertiary = Color(0xFF4A6353)           // 辅助色 (深绿灰)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFCCE8D5)
val OnTertiaryContainer = Color(0xFF072014)

// --- 错误色 (Error) ---
val Error = Color(0xFFE5484D)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

// --- 中性色 (Neutral - 亮色模式) ---
val BackgroundLight = Color(0xFFFAFAFA)    // 应用背景
val OnBackgroundLight = Color(0xFF1A1A1A)  // 背景上的文字
val SurfaceLight = Color(0xFFFFFFFF)       // 卡片/表面
val OnSurfaceLight = Color(0xFF1A1A1A)     // 表面上的文字
val SurfaceVariantLight = Color(0xFFF3DDD3)// 表面变体
val OnSurfaceVariantLight = Color(0xFF53433D) // 表面变体上的文字
val OutlineLight = Color(0xFF85736C)       // 轮廓/边框
val OutlineVariantLight = Color(0xFFD8C2BB)

// --- 中性色 (Neutral - 暗色模式) ---
val PrimaryDark = Color(0xFFEFA088)        // 暗色模式主色 (提亮)
val OnPrimaryDark = Color(0xFF5D1F00)
val PrimaryContainerDark = Color(0xFF823800)
val OnPrimaryContainerDark = Color(0xFFFFDBCE)

val SecondaryDark = Color(0xFFBEC2FF)      // 暗色模式次色 (提亮)
val OnSecondaryDark = Color(0xFF1A2278)
val SecondaryContainerDark = Color(0xFF363FA0)
val OnSecondaryContainerDark = Color(0xFFE0E0FF)

val TertiaryDark = Color(0xFFB0CCAB)
val OnTertiaryDark = Color(0xFF1B3624)
val TertiaryContainerDark = Color(0xFF324D3B)
val OnTertiaryContainerDark = Color(0xFFCCE8D5)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF1A1A1A)     // 暗色模式背景
val OnBackgroundDark = Color(0xFFE5E5E5)   // 暗色模式背景文字
val SurfaceDark = Color(0xFF262626)        // 暗色模式表面
val OnSurfaceDark = Color(0xFFE5E5E5)      // 暗色模式表面文字
val SurfaceVariantDark = Color(0xFF53433D)
val OnSurfaceVariantDark = Color(0xFFD8C2BB)
val OutlineDark = Color(0xFFA08C85)
val OutlineVariantDark = Color(0xFF53433D)

// --- 附加功能色 ---
val Success = Color(0xFF2E7D32)            // 成功色
val Warning = Color(0xFFF9A825)            // 警告色
val Info = Color(0xFF5E6AD2)               // 信息色 (复用次色)

// --- 登录页面专用渐变色 ---
val LoginGradientStart = Color(0xFFD97757) // 渐变起始色
val LoginGradientEnd = Color(0xFFC4623D)   // 渐变结束色
