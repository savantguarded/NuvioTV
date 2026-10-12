package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C focus room (switch `focus_room`).
//
// A lazy list clips its items to its own bounds, so a focused card's ring (and its small focus
// zoom) is cut off when the card touches the list edge. The fix gives the list a little inner
// padding and, at the same time, makes the list that much bigger on each side and shifts it back,
// so the items stay exactly where they were and only the ring gets room to draw.
//
// Use both halves together:
//   LazyColumn(
//       contentPadding = nuvioCFocusRoomPadding(room, official padding…),
//       modifier = Modifier.nuvioCFocusRoom(room)…
//   )
// With the switch off both are no-ops (official padding, official bounds).

/** Room on each side for a 2 dp focus ring plus a 1.01x focus zoom. */
val NUVIO_C_FOCUS_ROOM: Dp = 6.dp

private fun room(dp: Dp): Dp = if (NuvioCFeatures.FOCUS_ROOM) dp else 0.dp

/** Official padding plus the focus room on the given sides. */
fun nuvioCFocusRoomPadding(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    officialStart: Dp = 0.dp,
    officialEnd: Dp = 0.dp,
    officialTop: Dp = 0.dp,
    officialBottom: Dp = 0.dp
): PaddingValues = PaddingValues(
    start = officialStart + room(horizontal),
    end = officialEnd + room(horizontal),
    top = officialTop + room(top),
    bottom = officialBottom + room(bottom)
)

/**
 * Grows the list by [horizontal] on the left and right and by [top] / [bottom], then places it
 * back by the same amount, so the extra padding added by [nuvioCFocusRoomPadding] doesn't move
 * any item. Its reported size is unchanged, so the surrounding layout doesn't move either.
 */
fun Modifier.nuvioCFocusRoom(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
): Modifier {
    if (!NuvioCFeatures.FOCUS_ROOM) return this
    return this.layout { measurable, constraints ->
        val side = horizontal.roundToPx()
        val topPx = top.roundToPx()
        val bottomPx = bottom.roundToPx()
        val extraW = side * 2
        val extraH = topPx + bottomPx
        val grown = Constraints(
            minWidth = if (constraints.hasBoundedWidth) constraints.minWidth + extraW else constraints.minWidth,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extraW else Constraints.Infinity,
            minHeight = if (constraints.hasBoundedHeight) constraints.minHeight + extraH else constraints.minHeight,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extraH else Constraints.Infinity
        )
        val placeable = measurable.measure(grown)
        val width = (placeable.width - extraW).coerceAtLeast(0)
        val height = (placeable.height - extraH).coerceAtLeast(0)
        layout(width, height) {
            placeable.place(-side, -topPx)
        }
    }
}
