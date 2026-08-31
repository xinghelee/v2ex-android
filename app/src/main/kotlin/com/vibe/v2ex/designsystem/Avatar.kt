package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Current iOS uses one signal colour for fallback identities instead of a
 * screenful of unrelated hues. The seed remains in the signature so call sites
 * stay descriptive and can still pass the identity they are representing. */
@Composable
fun identityColor(@Suppress("UNUSED_PARAMETER") seed: String): Color = MaterialTheme.colorScheme.primary

/**
 * 身份方块（设计稿的 avatar 形态）：圆角≈尺寸×0.29 的方形。
 * 有头像图时同形状裁切；无图时显示用户名前 2 字符小写，并使用主题计算出的高对比前景色。
 */
@Composable
fun Avatar(username: String, url: String?, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.29f)
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(identityColor(username.ifBlank { "?" })),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = username.take(2).lowercase().ifBlank { "?" },
                color = MaterialTheme.colorScheme.onPrimary,
                style = TextStyle(
                    fontSize = (size.value * 0.35f).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
