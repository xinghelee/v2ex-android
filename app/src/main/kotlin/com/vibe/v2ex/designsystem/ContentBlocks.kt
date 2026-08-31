package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale

/**
 * Renders parsed [ContentBlock]s. Text is selectable; links open via the default
 * uri handler (LinkAnnotation.Url → LocalUriHandler). Images shrink to the column
 * width but stickers stay at intrinsic size (ContentScale.Inside never upscales);
 * see [ContentImage] for how 长截图 are bounded.
 */
@Composable
fun ContentBlocksView(
    blocks: List<ContentBlock>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Paragraph -> Text(
                        text = block.text.withLinkColor(linkColor),
                        style = textStyle,
                    )
                    is ContentBlock.Code -> CodeBlock(block.code)
                    is ContentBlock.Quote -> QuoteBlock(block.text.withLinkColor(linkColor), textStyle)
                    is ContentBlock.ListBlock -> ListBlockView(block.items, textStyle, linkColor)
                    is ContentBlock.Image -> ContentImage(block.url)
                    ContentBlock.Rule -> HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    )
                }
            }
        }
    }
}

/** 代码块（设计稿）：#F7F7F9 / 深色 #2C2C2E 底，圆角 12，等宽 13sp，无边框。 */
@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = if (LocalV2Dark.current) V2Colors.CodeBgDark else V2Colors.CodeBgLight,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 21.sp,
            ),
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** 引用块（设计稿）：左竖线 2.5dp accent 35%，引文 muted，无底色。 */
@Composable
private fun QuoteBlock(text: AnnotatedString, textStyle: TextStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
        Text(
            text = text,
            style = textStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun ListBlockView(items: List<AnnotatedString>, textStyle: TextStyle, linkColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row {
                Text(text = "•", style = textStyle, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = item.withLinkColor(linkColor),
                    style = textStyle,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** 超过这个高度的图只在正文里给个预览，点按看原图 —— 否则一张长截图要滚十几屏。 */
private val MAX_INLINE_IMAGE_HEIGHT = 460.dp

/** 兜底列宽：正文列永远是有界的，这个分支只为不给解码器传 Infinity。 */
private val FALLBACK_IMAGE_WIDTH = 360.dp

/**
 * 正文图片。V2EX 帖子里的长截图动辄几千像素高，按原尺寸解码就是几十 MB 一张的
 * 位图 —— “内存不足崩掉”的主因。两道防线：
 *  - 解码尺寸按实际列宽 × 展示高度上限的两倍封顶。宽度必须取 [BoxWithConstraints]
 *    量到的列宽而不是屏宽，否则每张图都会比需要的多解码一圈。
 *  - 展示高度封顶，超出部分截掉并给出“点按看原图”。
 *  [Scale.FIT] 得显式写：展示用的是 [ContentScale.Crop]，而 Crop 推导出的
 *  Scale.FILL 会让长图按原尺寸解码，等于没限制。
 * 宽高比在降采样前后不变，所以拿解码后的 intrinsicSize 判断是否长图是准的。
 */
@Composable
private fun ContentImage(url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var aspect by remember(url) { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val clipped = aspect > 0f && maxWidth * aspect > MAX_INLINE_IMAGE_HEIGHT
        val request = remember(url, constraints.maxWidth) {
            val widthPx = constraints.maxWidth.takeIf { it in 1 until Constraints.Infinity }
                ?: with(density) { FALLBACK_IMAGE_WIDTH.roundToPx() }
            val heightPx = with(density) { MAX_INLINE_IMAGE_HEIGHT.roundToPx() } * 2
            ImageRequest.Builder(context)
                .data(url)
                .size(widthPx, heightPx)
                .scale(Scale.FIT)
                .precision(Precision.INEXACT)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = "图片",
            contentScale = if (clipped) ContentScale.Crop else ContentScale.Inside,
            alignment = Alignment.TopStart,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) aspect = size.height / size.width
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (clipped) {
                        Modifier.height(MAX_INLINE_IMAGE_HEIGHT)
                    } else {
                        Modifier.heightIn(min = 20.dp)
                    },
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable { uriHandler.openUri(url) },
        )
        if (clipped) {
            Text(
                text = "长图 · 点按看原图",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** Link color lives with the theme, not the parse — restyle annotation ranges at render time. */
private fun AnnotatedString.withLinkColor(color: Color): AnnotatedString {
    val links = getLinkAnnotations(0, length)
    if (links.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withLinkColor)
        links.forEach { addStyle(SpanStyle(color = color), it.start, it.end) }
    }
}
