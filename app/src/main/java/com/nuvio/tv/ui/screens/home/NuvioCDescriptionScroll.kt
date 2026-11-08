package com.nuvio.tv.ui.screens.home

// [fork] Nuvio C round 34 `desc_auto_scroll`: official's "Auto-scroll Descriptions" (beta.12, Settings > Layout)
// reaching the fork's own synopsis code. The fork keeps its line counts and widths; only when the text still
// doesn't fit does it scroll, using official's AutoScrollingText (3 s wait, 12 dp/s, 3 passes, edge fades).
// Featured: while the first pass runs, the carousel's idle clock is kept fresh, so the page pill stays empty and
// the slide doesn't change mid-read; the full 10 s starts once the pass ends.

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.components.AutoScrollingText
import kotlinx.coroutines.delay

/** [enabled] = official's toggle; [holding] = a Featured synopsis is in its first scroll pass. */
internal class NuvioCDescScroll(val enabled: Boolean, val holding: MutableState<Boolean>?)

internal val LocalNuvioCDescScroll = compositionLocalOf<NuvioCDescScroll?> { null }

internal fun nuvioCDescScrollOn(scroll: NuvioCDescScroll?): Boolean =
    NuvioCFeatures.DESC_AUTO_SCROLL && scroll?.enabled == true

/**
 * Synopsis with up to [maxLines] lines that also respects the height it's given: the visible lines are the
 * smaller of the two. Scrolls only when the text is longer than that and the toggle is on.
 */
@Composable
internal fun NuvioCScrollingSynopsis(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
    minLines: Int = 1
) {
    val scroll = LocalNuvioCDescScroll.current
    BoxWithConstraints(modifier) {
        val linePx = with(LocalDensity.current) {
            if (style.lineHeight.isSp) style.lineHeight.toPx() else style.fontSize.toPx() * 1.4f
        }
        val fit = if (constraints.hasBoundedHeight && linePx > 0f) (constraints.maxHeight / linePx).toInt() else maxLines
        val lines = fit.coerceIn(minLines, maxLines.coerceAtLeast(minLines))
        if (nuvioCDescScrollOn(scroll)) {
            AutoScrollingText(
                text = text,
                style = style,
                color = color,
                maxLines = lines,
                autoScrollEnabled = true,
                onScrollHoldChanged = { hold -> scroll?.holding?.value = hold }
            )
        } else {
            Text(text = text, style = style, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Keeps the Featured carousel's idle clock fresh while a synopsis is in its first scroll pass. */
@Composable
internal fun NuvioCDescScrollHoldEffect(touch: () -> Unit) {
    val holding = LocalNuvioCDescScroll.current?.holding ?: return
    val hold = holding.value
    LaunchedEffect(hold) {
        if (!hold) return@LaunchedEffect
        while (true) {
            touch()
            delay(400)
        }
    }
    LaunchedEffect(hold) { if (!hold) touch() }
}
