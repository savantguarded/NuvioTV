package com.nuvio.tv.ui.util

import androidx.compose.ui.Modifier
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C (switch `detail_row_throttle`, 2026-10-15): official's D-pad repeat gate (one step
// per 80 ms while a direction is held), which Home, collections, Search and See all already use,
// also on the details-page rows (cast, more like this, collection, trailers, companies) and the cast
// page filmography rows. Without it a held Left queued more focus moves and scroll animations than
// the TV could draw, and the row stalled. Single presses are untouched.
fun Modifier.nuvioCRowThrottle(): Modifier =
    if (NuvioCFeatures.DETAIL_ROW_THROTTLE) this.dpadRepeatThrottle() else this
