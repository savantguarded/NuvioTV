package com.nuvio.tv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// [fork] Nuvio C: details tagline, style A (Charles 2026-10-14). Italic in the app font, 14/19 sp at 88%
// white, curly quotes at 45% with the opening one hanging in the left margin, so the words line up with
// the synopsis below. Same width as the synopsis block, two lines at most, balanced line breaks.
// Quotes TMDB already put around the text are stripped first, so they are never doubled.

private const val OPEN = "“"
private const val CLOSE = "”"
private val QUOTE_CHARS = charArrayOf('"', '\'', '“', '”', '‘', '’', '«', '»')

/** Trims whitespace and any quote marks wrapped around the whole tagline. */
internal fun nuvioCTaglineText(raw: String): String? =
    raw.trim().trim(*QUOTE_CHARS).trim().takeIf { it.isNotEmpty() }

/** [fork] round 33: lines the tagline may take (the Featured hero provides 1). */
internal val LocalNuvioCTaglineLines = androidx.compose.runtime.staticCompositionLocalOf { 2 }

@Composable
internal fun NuvioCTagline(
    tagline: String,
    widthFraction: Float,
    modifier: Modifier = Modifier
) {
    val text = remember(tagline) { nuvioCTaglineText(tagline) } ?: return
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        lineBreak = LineBreak.Heading // balanced: no single word left on line two
    )
    val quoteColor = Color.White.copy(alpha = 0.45f)
    val measurer = rememberTextMeasurer()
    val quoteWidthPx = remember(style, measurer) { measurer.measure(OPEN, style).size.width }
    val restIndent = with(androidx.compose.ui.platform.LocalDensity.current) { quoteWidthPx.toSp() }
    val annotated = remember(text, quoteColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = quoteColor)) { append(OPEN) }
            append(text)
            withStyle(SpanStyle(color = quoteColor)) { append(CLOSE) }
        }
    }
    Text(
        text = annotated,
        style = style.copy(textIndent = TextIndent(restLine = restIndent)), // line two starts on the words, not the quote
        color = Color.White.copy(alpha = 0.88f),
        maxLines = LocalNuvioCTaglineLines.current,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.nuvioCHangingQuote(widthFraction, quoteWidthPx)
    )
}

/**
 * Lays the text out [quotePx] wider and [quotePx] further left than the synopsis column, so the
 * opening quote sits in the margin and the words start on the synopsis edge. The second line is
 * indented by the quote's width (TextIndent.restLine), so it lines up with the words too.
 */
private fun Modifier.nuvioCHangingQuote(fraction: Float, quotePx: Int): Modifier = layout { measurable, constraints ->
    val column = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).toInt() else constraints.minWidth
    val width = column + quotePx
    val placeable = measurable.measure(Constraints(minWidth = 0, maxWidth = width.coerceAtLeast(0)))
    layout(column, placeable.height) { placeable.place(-quotePx, 0) }
}
