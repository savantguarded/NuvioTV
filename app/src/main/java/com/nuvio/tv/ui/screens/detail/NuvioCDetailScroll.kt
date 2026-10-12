package com.nuvio.tv.ui.screens.detail

import android.view.KeyEvent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// [fork] Nuvio C: details page scrolls to its end in one move (switch `detail_scroll_end`).
//
// Official, near the bottom of the page: when 200 px or less are left, the Network / Production
// row blocks the page's normal scroll and a second, slower nudge then scrolls the rest (and a
// "pin" can scroll it back when you move up again). That stop-then-nudge reads as the page being
// stuck. Here, moving up or down onto those rows scrolls the page straight to its end in one normal
// animation; the rows' own focus, Left / Right along the row, holding Down (fast scroll) and the
// return from a company page are left exactly as official.
//
// 2026-10-12 (with `detail_focus_stable`): the move is an eased scroll by the exact distance left,
// at the page's own row speed. It was animateScrollToItem(last), which springs (fast over a long
// distance) or jumps when the last row isn't laid out yet.

internal fun nuvioCScrollDetailToEnd(
    listState: LazyListState,
    lastKey: Int,
    restoringCompany: Boolean,
    fastScrolling: Boolean,
    scope: CoroutineScope,
    spec: AnimationSpec<Float>? = null
) {
    val vertical = lastKey == KeyEvent.KEYCODE_DPAD_DOWN || lastKey == KeyEvent.KEYCODE_DPAD_UP
    if (!vertical || restoringCompany || fastScrolling) return
    if (!listState.canScrollForward) return
    val lastIndex = listState.layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scope.launch {
        if (!com.nuvio.tv.NuvioCFeatures.DETAIL_FOCUS_STABLE || spec == null) {
            // Asking for the last item at the top clamps at the end of the page.
            listState.animateScrollToItem(lastIndex)
            return@launch
        }
        val distance = nuvioCDistanceToEnd(listState)
        if (distance > 1f) listState.animateScrollBy(distance, spec)
    }
}

/** Pixels left to the end of the page (estimated from the visible rows if the last one isn't laid out). */
internal fun nuvioCDistanceToEnd(listState: LazyListState): Float {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    val last = visible.lastOrNull() ?: return 0f
    val tail = (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).toFloat()
    val missing = info.totalItemsCount - 1 - last.index
    if (missing <= 0) return tail.coerceAtLeast(0f)
    val average = visible.sumOf { it.size } / visible.size.toFloat() + info.mainAxisItemSpacing
    return (tail + missing * average).coerceAtLeast(0f)
}
