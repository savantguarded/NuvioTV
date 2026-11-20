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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
