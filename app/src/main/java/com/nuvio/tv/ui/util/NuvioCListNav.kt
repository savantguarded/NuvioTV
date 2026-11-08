package com.nuvio.tv.ui.util

// [fork] Nuvio C round 35 `sources_nav`: Up / Down in the sources lists (player panel and the details sources screen)
// step one card at a time by index instead of relying on Compose's focus search, which gives up when the next card
// isn't laid out yet (fast held keys, tall provider headers) and left focus stuck short of the top or bottom.
// The next card is scrolled in first when needed, then focused; held keys queue on the pending target so nothing
// is skipped or lost. Up on the first card is left to the card / list (chips or close button), as before.

import androidx.compose.foundation.gestures.scrollBy
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
                if (target == 0 && lazyOffset == 0) {
                    // Back at the top: the list sits fully at the top, padding and first header included.
                    state.scrollToItem(0)
                } else if (!fullyVisible(state, lazyIndex)) {
                    val composed = state.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex }
                    if (!composed) {
                        if (dir < 0) {
                            state.scrollToItem(lazyIndex)
                        } else {
                            // Coming from above: bring it in at the bottom edge, like a normal scroll.
                            state.scrollToItem(lazyIndex)
                            val info = state.layoutInfo
                            val size = info.visibleItemsInfo.firstOrNull { it.index == lazyIndex }?.size ?: 0
                            val room = info.viewportEndOffset - info.afterContentPadding - size - info.viewportStartOffset
                            if (room > 0) state.scrollBy(-room.toFloat())
                        }
                    }
                }
                repeat(4) {
                    withFrameNanos { }
                    val r = requester(target) ?: return@repeat
                    if (runCatching { r.requestFocus() }.getOrNull() == true) return@launch
                }
            } finally {
                if (pending == target) pending = null
            }
        }
        return true
    }

    private fun fullyVisible(state: LazyListState, lazyIndex: Int): Boolean {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == lazyIndex } ?: return false
        return item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
    }
}
