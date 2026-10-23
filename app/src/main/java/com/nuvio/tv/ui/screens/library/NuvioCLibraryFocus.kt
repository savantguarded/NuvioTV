package com.nuvio.tv.ui.screens.library

import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C round 26 `library_list_first`: a new list (or genre, year, watched filter) is a new set of posters,
// so the grid goes to the top and focuses the first one, the way official already handles a sort change
// (LibraryScreen's sortSelectionVersion effect). Without it the grid kept the old list's scroll and focus went
// back to a poster remembered from that list, which in the new one could be the second or a later poster.

internal fun LibraryUiState.nuvioCJumpToFirst(changed: Boolean): LibraryUiState =
    if (NuvioCFeatures.LIBRARY_LIST_FIRST && changed) copy(sortSelectionVersion = sortSelectionVersion + 1L) else this
