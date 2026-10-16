package com.nuvio.tv.ui.screens.detail

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C: details page sections without a whole-page rebuild (switch `detail_section_smooth`,
// 2026-10-17). Charles: a hitch moving hero -> episodes -> cast and back, with or without a trailer.
//
// Causes (official code):
// 1. Every episode focus wrote the last-focused episode into a snapshot map that the page body read
//    (to pick the episode Down from the season tabs / Up from the cast rows lands on), so each
//    focus change rebuilt the whole ~3,500-line page and every row under it.
// 2. The last D-pad key was saveable state used as a `remember` key: each change of direction
//    (Right along the episodes, then Down to the cast) rebuilt the page again.
// 3. Up into the hero / back to the episodes used animateScrollToItem (a different, spring-like
//    motion than every other section, and the hero is re-measured on the way).
// (The hero-scrolled-away flag feeding the backdrop now reaches only the backdrop, in both modes.)
//
// Fix: the episodes row gets one stable entry point (an invisible focus "trampoline") that hands
// focus to the right episode when it's reached, so the page no longer needs to know which episode
// that is; the last key lives in a plain holder; those two scrolls go by the exact distance with the
// page's own scroll motion.
object NuvioCDetailSmooth {
    val enabled: Boolean get() = NuvioCFeatures.DETAIL_SECTION_SMOOTH
}

/** Plain size holder (no recomposition when it changes). */
internal class NuvioCSizeHolder { var px: Int = 0 }

/**
 * Invisible, stable focus target for the episodes row. Focus that lands here is passed on at once to
 * [resolve] (last focused / next to watch / first episode), or to the next card on the right if that
 * episode isn't laid out.
 */
@Composable
internal fun NuvioCEpisodeEntry(requester: FocusRequester, resolve: () -> FocusRequester?) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .size(0.dp)
            .focusRequester(requester)
            .onFocusChanged { state ->
                if (!state.isFocused) return@onFocusChanged
                val moved = resolve()?.let { target -> runCatching { target.requestFocus() }.isSuccess } == true
                if (!moved) focusManager.moveFocus(FocusDirection.Right)
            }
            .focusable()
    )
}

/**
 * Scrolls the page back to the top (hero) by the exact distance with [spec]. False when the distance
 * can't be known (hero size unknown and the hero is more than one row away): caller falls back.
 */
internal suspend fun nuvioCScrollPageToTop(listState: LazyListState, heroSizePx: Int, spec: AnimationSpec<Float>): Boolean {
    val first = listState.firstVisibleItemIndex
    val offset = listState.firstVisibleItemScrollOffset
    val distance = when {
        first == 0 -> offset
        first == 1 && heroSizePx > 0 -> offset + heroSizePx + listState.layoutInfo.mainAxisItemSpacing
        else -> return false
    }
    if (distance > 0) listState.animateScrollBy(-distance.toFloat(), spec)
    return true
}

/** Brings [index] to the top of the page by the exact distance when it's laid out; false otherwise. */
internal suspend fun nuvioCScrollItemToTop(listState: LazyListState, index: Int, spec: AnimationSpec<Float>): Boolean {
    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val distance = item.offset // same target as animateScrollToItem(index): the item's top at the list top
    if (distance != 0) listState.animateScrollBy(distance.toFloat(), spec)
    return true
}
