package com.vibe.v2ex.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val NO_FONT_PADDING = PlatformTextStyle(includeFontPadding = false)
private val CENTER_TRIM = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    platformStyle = NO_FONT_PADDING,
    lineHeightStyle = CENTER_TRIM,
)

/**
 * Type scale tuned for a dense CJK reading app — mirrors the iOS app's SF sizing
 * (titles kerned slightly tight, 13sp muted metadata, generous body line height).
 */
val V2exTypography = Typography(
    // Large title ("V2EX" top bar)
    headlineLarge = style(28, 34, FontWeight.Bold, -0.3f),
    headlineMedium = style(24, 30, FontWeight.Bold, -0.3f),
    // Topic detail title
    titleLarge = style(21, 29, FontWeight.Bold, -0.2f),
    // Featured card title
    titleMedium = style(17, 24, FontWeight.SemiBold, -0.2f),
    titleSmall = style(15, 21, FontWeight.SemiBold),
    // Topic row title / topic body
    bodyLarge = style(16, 24),
    // Reply body
    bodyMedium = style(15, 23),
    // Metadata (author · time)
    bodySmall = style(13, 18),
    // Usernames in reply rows
    labelLarge = style(14, 19, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    // Node tag pills
    labelSmall = style(11, 14, FontWeight.Medium),
)
