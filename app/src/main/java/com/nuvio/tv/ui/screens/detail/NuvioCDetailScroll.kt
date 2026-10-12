package com.nuvio.tv.ui.screens.detail

import android.view.KeyEvent
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

internal fun nuvioCScrollDetailToEnd(
    listState: LazyListState,
    lastKey: Int,
    restoringCompany: Boolean,
    fastScrolling: Boolean,
    scope: CoroutineScope
) {
    val vertical = lastKey == KeyEvent.KEYCODE_DPAD_DOWN || lastKey == KeyEvent.KEYCODE_DPAD_UP
    if (!vertical || restoringCompany || fastScrolling) return
    if (!listState.canScrollForward) return
    val lastIndex = listState.layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scope.launch {
        // Asking for the last item at the top clamps at the end of the page, so this lands exactly
        // at the bottom whatever the rows' heights are.
        listState.animateScrollToItem(lastIndex)
    }
}
