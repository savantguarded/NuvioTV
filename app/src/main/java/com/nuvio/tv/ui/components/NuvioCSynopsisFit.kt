package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C home synopsis fit (switch `home_synopsis_fit`). Up to 6 lines (round 6,
// 2026-10-12: was 5, with a 6th only at the widest width), and only as wide as it needs to be:
//  1. official width (42% of the space between the side paddings) if it fits in 6 lines,
//  2. else our width (to the second landscape poster),
//  3. else our width + 80 dp, ellipsis after the 6th line.
// Lines are broken for even length (no lone last word). The block stays bottom-anchored with its
// top clearance; NuvioCHeroStack keeps the logo at least [MIN_LOGO_HEIGHT] tall, so text that
// doesn't fit the height ellipsizes instead of squeezing the logo away.

data class NuvioCSynopsisFitResult(val width: Dp, val lines: Int)

object NuvioCSynopsisFit {
    const val OFFICIAL_FRACTION = 0.42f
    val EXTRA_WIDTH: Dp = 80.dp
    val MIN_LOGO_HEIGHT: Dp = 48.dp
    const val MAX_LINES = 6

    /** Widths to try, narrowest first, never past [available]. */
    fun widths(available: Dp, ours: Dp): List<Dp> {
        val official = available * OFFICIAL_FRACTION
        return listOf(official, ours, ours + EXTRA_WIDTH)
            .map { it.coerceAtMost(available) }
            .distinct()
            .sorted()
    }

    /** Pure choice from a "does it fit in n lines at width w" test (unit-tested). */
    fun choose(widths: List<Dp>, fits: (Dp, Int) -> Boolean): NuvioCSynopsisFitResult {
        for (w in widths) if (fits(w, MAX_LINES)) return NuvioCSynopsisFitResult(w, MAX_LINES)
        return NuvioCSynopsisFitResult(widths.last(), MAX_LINES)
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
