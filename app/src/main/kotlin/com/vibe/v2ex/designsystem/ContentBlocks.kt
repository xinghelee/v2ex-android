package com.vibe.v2ex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Renders parsed [ContentBlock]s. Text is selectable; links open via the default
 * uri handler (LinkAnnotation.Url → LocalUriHandler). Images shrink to the column
 * width but stickers stay at intrinsic size (ContentScale.Inside never upscales).
 */
@Composable
fun ContentBlocksView(
    blocks: List<ContentBlock>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val insetColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Paragraph -> Text(
                        text = block.text.withLinkColor(linkColor),
                        style = textStyle,
                    )
                    is ContentBlock.Code -> CodeBlock(block.code, insetColor)
                    is ContentBlock.Quote -> QuoteBlock(block.text.withLinkColor(linkColor), textStyle, insetColor)
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

@Composable
private fun CodeBlock(code: String, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun QuoteBlock(text: AnnotatedString, textStyle: TextStyle, background: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

@Composable
private fun ContentImage(url: String) {
    AsyncImage(
        model = url,
        contentDescription = "图片",
        contentScale = ContentScale.Inside,
        alignment = Alignment.TopStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
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
