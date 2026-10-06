package com.nuvio.tv.ui.screens.cast

// [fork] Nuvio C cast page: Back on a filmography row works like official's home rows. Focus past
// the first title: Back scrolls the row to the start and focuses the first poster. Already on the
// first poster (or focus outside the row): Back does what official does (close the biography or
// leave the page). Switch: NuvioCFeatures.CAST_BACK_TO_FIRST.

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.launch

internal class NuvioCBackToFirstState {
    var rowHasFocus by mutableStateOf(false)
    var focusedIndex by mutableIntStateOf(0)

    /** Goes on the row; tracks whether focus is inside it. */
    fun rowModifier(): Modifier = Modifier.onFocusChanged { rowHasFocus = it.hasFocus }
}

@Composable
internal fun NuvioCBackToFirst(
    state: NuvioCBackToFirstState,
    listState: LazyListState,
    firstItemFocusRequester: FocusRequester
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = NuvioCFeatures.CAST_BACK_TO_FIRST && state.rowHasFocus && state.focusedIndex > 0) {
        state.focusedIndex = 0
        scope.launch {
            listState.scrollToItem(0, 0)
            runCatching { firstItemFocusRequester.requestFocusAfterFrames() }
        }
    }
}
