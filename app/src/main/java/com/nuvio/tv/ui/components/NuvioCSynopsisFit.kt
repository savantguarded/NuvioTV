package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.drawText
import com.nuvio.tv.NuvioCFeatures
import kotlin.math.max

// [fork] Nuvio C home synopsis fit (switch `home_synopsis_fit`). Up to 6 lines and only as wide as
// it needs to be:
//  1. official width (42% of the space between the side paddings) if it all fits,
//  2. else our width (to the second landscape poster),
//  3. else our width + 80 dp (the widest step), ellipsis only there, after the last line.
// Round 7 (2026-10-13): the width with the fewest lines wins (see pickIndex), not the narrowest.
// Round 6b (2026-10-12): decided while laying out, against the height really left for the text
// ("6, or fewer if 6 can't fit"). The first version only checked 6 lines, so it could pick a narrow
// width that the height then cut at 4-5 lines with the wider steps unused.
// Lines are broken for even length (no lone last word). The block stays bottom-anchored with its
// top clearance; NuvioCHeroStack keeps the logo at least [MIN_LOGO_HEIGHT] tall, so text that
// doesn't fit the height ellipsizes instead of squeezing the logo away.

data class NuvioCSynopsisFitResult(val width: Dp, val lines: Int)

object NuvioCSynopsisFit {
    const val OFFICIAL_FRACTION = 0.42f
    val EXTRA_WIDTH: Dp = 80.dp
    val MIN_LOGO_HEIGHT: Dp = 48.dp
    const val MAX_LINES = 5 // round 7 (Charles 2026-10-13): was 6

    /** Widths to try, narrowest first, never past [available]. */
    fun widths(available: Dp, ours: Dp): List<Dp> {
        val official = available * OFFICIAL_FRACTION
        return listOf(official, ours, ours + EXTRA_WIDTH)
            .map { it.coerceAtMost(available) }
            .distinct()
            .sorted()
    }

    /** Lines the text may use: 6, or fewer when only that many fit in [maxHeightPx] (unit-tested). */
    fun linesFor(maxHeightPx: Int, lineHeightPx: Float, hasBoundedHeight: Boolean): Int {
        if (!hasBoundedHeight || lineHeightPx <= 0f) return MAX_LINES
        return (maxHeightPx / lineHeightPx + 0.01f).toInt().coerceIn(1, MAX_LINES)
    }

    /** Text that fits in this many lines at the narrowest width stays narrow (round 7). */
    const val SHORT_LINES = 2

    /**
     * Round 7 (2026-10-13, Charles: "three lines could have handled it, but it's squished into five,
     * and the logo comes out small"): the narrowest width that showed everything won, even when a
     * wider step needed 2 lines fewer, and every extra line came out of the logo's height.
     * Now the width with the FEWEST lines wins; among equal counts the narrowest. Text that already
     * fits in [SHORT_LINES] lines at a narrower width keeps it (a short synopsis doesn't stretch).
     * [lineCounts] = lines used at each width (narrowest first), null where the text didn't fit.
     * Returns the index to use; the widest (last) when nothing fits (unit-tested).
     */
    fun pickIndex(lineCounts: List<Int?>): Int {
        if (lineCounts.isEmpty()) return 0
        val fewest = lineCounts.filterNotNull().minOrNull() ?: return lineCounts.lastIndex
        val target = max(fewest, SHORT_LINES)
        return lineCounts.indexOfFirst { it != null && it <= target }
    }

    /** Pure choice from a "does it fit in n lines at width w" test (unit-tested). */
    fun choose(widths: List<Dp>, lines: Int = MAX_LINES, fits: (Dp, Int) -> Boolean): NuvioCSynopsisFitResult {
        for (w in widths) if (fits(w, lines)) return NuvioCSynopsisFitResult(w, lines)
        return NuvioCSynopsisFitResult(widths.last(), lines)
    }
}

@Composable
fun rememberNuvioCSynopsisFit(text: String, style: TextStyle, widths: List<Dp>?): NuvioCSynopsisFitResult? {
    if (!NuvioCFeatures.HOME_SYNOPSIS_FIT || widths.isNullOrEmpty()) return null
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current
    return remember(text, style, widths, density) {
        NuvioCSynopsisFit.choose(widths) { w, lines ->
            val px = with(density) { w.roundToPx() }.coerceAtLeast(1)
            !measurer.measure(text, style, maxLines = lines, constraints = Constraints(maxWidth = px)).hasVisualOverflow
        }
    }
}

/**
 * The home synopsis, sized while laying out: the line count comes from the height the hero leaves it
 * (up to 6), the width from [widths] (narrowest that shows it all, else the widest with an ellipsis).
 * Measured with a cached TextMeasurer once per layout change, drawn from that one result.
 */
@Composable
fun NuvioCFitSynopsis(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    widths: List<Dp>,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val result = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null, androidx.compose.runtime.referentialEqualityPolicy()) }
    androidx.compose.ui.layout.Layout(
        modifier = modifier.drawBehind {
            result.value?.let { drawText(it, color = color) }
        }
    ) { _, constraints ->
        val lineHeightPx = if (style.lineHeight.isSp) style.lineHeight.toPx() else style.fontSize.toPx() * 1.4f
        val lines = NuvioCSynopsisFit.linesFor(constraints.maxHeight, lineHeightPx, constraints.hasBoundedHeight)
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        // Round 7: measure every step (cached TextMeasurer, at most 3 widths) and take the one with
        // the fewest lines (NuvioCSynopsisFit.pickIndex), so the logo above keeps its height.
        val measured = widths.map { w ->
            val px = w.roundToPx().coerceIn(1, maxW)
            measurer.measure(
                text, style, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                maxLines = lines, constraints = Constraints(maxWidth = px)
            )
        }
        val r = if (measured.isEmpty()) null
        else measured[NuvioCSynopsisFit.pickIndex(measured.map { if (it.hasVisualOverflow) null else it.lineCount })]
        result.value = r
        val width = r?.size?.width ?: 0
        val height = (r?.size?.height ?: 0).coerceAtMost(if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
        layout(width, height) {}
    }
}
