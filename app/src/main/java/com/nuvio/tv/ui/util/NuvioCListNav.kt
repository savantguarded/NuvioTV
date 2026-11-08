package com.nuvio.tv.ui.util

// [fork] Nuvio C round 35 `sources_nav`: Up / Down in the sources lists (player panel and the details sources screen)
// step one card at a time by index instead of relying on Compose's focus search, which gives up when the next card
// isn't laid out yet (fast held keys, tall provider headers) and left focus stuck short of the top or bottom.
// Round 36: every scroll here is animated (round 35 snapped, so reaching the first card jumped). The next card is
// nudged into view with a smooth scroll only when it isn't laid out; otherwise focusing it lets Compose glide it in.
// On the first card the list glides the rest of the way to the very top. Held keys queue on the pending target.
// Up on the first card is left to the card / list (chips or close button), as before.

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class NuvioCListNav {
    private var pending: Int? = null
    private var job: Job? = null

    /** Forget a queued target (list changed or focus moved elsewhere). */
    fun reset() {
        job?.cancel()
        job = null
        pending = null
    }

    /**
     * Handles one Up ([dir] = -1) or Down ([dir] = 1) press. [focused] = index of the focused card among [count]
     * cards; [lazyOffset] = lazy items placed before the first card (e.g. a shared stream). Returns true when the
     * press was handled here; false leaves it to the default handling (Up on the first card).
     */
    fun step(
        scope: CoroutineScope,
        state: LazyListState,
        focused: Int,
        dir: Int,
        count: Int,
        lazyOffset: Int = 0,
        requester: (Int) -> FocusRequester?
    ): Boolean {
        val from = pending ?: focused
        if (from < 0 || count <= 0) return false
        val target = from + dir
        if (target < 0) {
            if (pending != null) return true // still travelling up; the next press lands on the first card first
            return false
        }
        if (target >= count) return true // bottom: stay put, don't let focus leave the list
        pending = target
        job?.cancel()
        job = scope.launch {
            try {
                val lazyIndex = target + lazyOffset
                if (!composed(state, lazyIndex)) {
                    // Not laid out yet: nudge it in smoothly, about one card's height, from the side it comes from.
                    val step = cardStep(state)
                    state.animateScrollBy(if (dir < 0) -step else step)
                }
                var focusedOk = false
                repeat(4) {
                    if (focusedOk) return@repeat
                    withFrameNanos { }
                    val r = requester(target) ?: return@repeat
                    focusedOk = runCatching { r.requestFocus() }.getOrNull() == true
                }
                if (target == 0 && lazyOffset == 0) {
                    // First card: glide the remaining few dp so the top padding and first header show too.
                    withFrameNanos { }
                    if (state.firstVisibleItemIndex != 0 || state.firstVisibleItemScrollOffset > 0) {
                        state.animateScrollToItem(0)
                    }
                }
            } finally {
                if (pending == target) pending = null
            }
        }
        return true
    }

    private fun composed(state: LazyListState, lazyIndex: Int): Boolean =
        state.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex }

    /** Average laid-out card height plus the gap between cards, in px. */
    private fun cardStep(state: LazyListState): Float {
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return 200f
        return items.map { it.size }.average().toFloat() + info.mainAxisItemSpacing
    }
}
