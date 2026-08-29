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
    size: Float,
    lineHeight: Float,
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
 * 字阶 — 逐值对齐设计稿（iOS 26 排版）：
 * 大标题 34/-0.9、话题标题 23/-0.5、精选卡 21/-0.4、列表行 16/-0.3、
 * iOS 设置行 17/-0.43、正文 16×1.62、回复 15×1.55、元信息 12。
 */
val V2exTypography = Typography(
    // 大标题（首页 V2EX / 节点 / 通知 / 我的）
    headlineLarge = style(34f, 41f, FontWeight.Bold, -0.9f),
    headlineMedium = style(24f, 30f, FontWeight.Bold, -0.5f),
    // 话题详情标题 23/700 行高 1.28
    titleLarge = style(23f, 29.5f, FontWeight.Bold, -0.5f),
    // 精选卡标题 21/600
    titleMedium = style(21f, 27.5f, FontWeight.SemiBold, -0.4f),
    // iOS 设置/列表行 17/-0.43
    titleSmall = style(17f, 22f, FontWeight.Normal, -0.43f),
    // 话题正文 16 行高 1.62
    bodyLarge = style(16f, 26f),
    // 回复正文 15 行高 1.55
    bodyMedium = style(15f, 23f),
    // 次要说明 13
    bodySmall = style(13f, 18f),
    // 用户名 / 行标题强调 15/600
    labelLarge = style(15f, 20f, FontWeight.SemiBold),
    // 元信息 12
    labelMedium = style(12f, 16f, FontWeight.Medium),
    // 小标签（节点 pill、楼主徽章）
    labelSmall = style(12f, 15f, FontWeight.SemiBold),
    // 列表行话题标题 16/500/-0.3 行高 1.32 —— M3 没有现成槽位，占用 displaySmall
    displaySmall = style(16f, 21f, FontWeight.Medium, -0.3f),
)

/** 列表行话题标题的语义别名（displaySmall 槽位）。 */
val Typography.topicRowTitle: TextStyle get() = displaySmall
