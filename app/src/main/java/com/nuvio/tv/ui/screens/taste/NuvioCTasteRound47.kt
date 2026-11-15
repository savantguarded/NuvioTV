package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 47 `taste_fit` / `taste_rotate`.
//
// taste_fit: Taste text is never cut off without a way to read it. Each line first steps down through a short list of
// sizes, wrapping onto the lines it's allowed; only if the smallest size still doesn't fit does it fall back to a
// marquee: on focus for things you can focus (Spotlight cards), on its own, slowly and with pauses, for things you
// can't (This week tiles). Cards and tiles keep fixed sizes: the text adapts to the card, never the other way round.
//
// taste_rotate: the sections under Spotlight (people, the poster rows, Recaps) come in a new order on each visit,
// per profile, and never lead with the same kind of row twice running. This week and Spotlight stay pinned on top.

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.FocusMarqueeText

/**
 * Text that fits its box: tries [styles] in order at up to [maxLines] lines; if even the last one overflows, shows one
 * line in the last style that marquees: while [focused] when [focused] is not null, else on its own (slow, repeating,
 * with a pause between passes).
 */
@Composable
internal fun TasteFit(
    text: String,
    styles: List<TextStyle>,
    maxLines: Int,
    focused: Boolean?,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    var step by remember(text, styles.size, maxLines) { mutableStateOf(0) }
    var marquee by remember(text, styles.size, maxLines) { mutableStateOf(false) }
    val last = styles.lastIndex
    if (marquee) {
        val style = styles[last].let { if (fontWeight != null) it.copy(fontWeight = fontWeight) else it }
        if (focused != null) {
            FocusMarqueeText(text = text, focused = focused, style = style, color = color, modifier = modifier)
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    initialDelayMillis = 2_000,
                    repeatDelayMillis = 3_000,
                    velocity = 28.dp
                )
            )
        }
        return
    }
    Text(
        text = text,
        style = styles[step],
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        modifier = modifier,
        onTextLayout = { r ->
            if (r.hasVisualOverflow) {
                if (step < last) step++ else marquee = true
            }
        }
    )
}

/** The kinds of section under Spotlight that rotate (a row's id starts with its kind). */
internal fun tasteSectionKind(id: String): String = id.substringBefore(':')

internal val TASTE_ROTATING_KINDS = listOf("people", "because", "person", "decade", "genre", "unfinished", "rewatch", "recaps")

/**
 * This visit's order of [kinds]: shuffled with [seed]; never the same first kind as [lastFirst], and never exactly
 * [lastOrder] again (when there's more than one kind).
 */
internal fun tasteRotateOrder(kinds: List<String>, seed: Long, lastFirst: String?, lastOrder: String?): List<String> {
    if (kinds.size < 2) return kinds
    val order = kinds.shuffled(java.util.Random(seed xor 0x0DE5L)).toMutableList()
    if (order.first() == lastFirst) java.util.Collections.swap(order, 0, 1 + java.util.Random(seed).nextInt(order.size - 1))
    if (order.joinToString(",") == lastOrder) java.util.Collections.rotate(order, 1)
    if (order.first() == lastFirst) java.util.Collections.swap(order, 0, 1)
    return order
}
