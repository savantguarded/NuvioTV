package com.nuvio.tv.ui.screens.calendar

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.launch

/** Round 68 Calendar List view sizes (board 4). */
internal object NuvioCCalendar68 {
    val panelArt = 170.dp
    val lineGap = 6.dp
    val aboveButtons = 10.dp
    val aboveNext = 12.dp
    val whenSize = 13.sp
    val titleSize = 20.sp
    val metaSize = 13.sp
    val synopsisSize = 14.sp
    val nextHeaderSize = 12.sp
    val nextTitleSize = 14.sp
    val nextLineSize = 12.sp
    /** Room kept clear around a focused item when its list or card scrolls it into view: 2 dp ring plus the focus zoom. */
    val focusRoom = 10.dp

    /** Synopsis lines: 6 with a "Next from" list under it, 9 without. */
    fun synopsisLines(hasNext: Boolean): Int = if (hasNext) 6 else 9
}

/**
 * When this item (or a child) takes focus, scrolls its list or card so [room] stays visible on every side, so the
 * focus ring and zoom are never cut at the top or bottom edge. Runs one frame after the default bring-into-view.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.nuvioCFocusRoomInView(room: Dp = NuvioCCalendar68.focusRoom): Modifier =
    if (!NuvioCFeatures.CALENDAR_V68) this else composed {
        val requester = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        val px = with(LocalDensity.current) { room.toPx() }
        val size = remember { intArrayOf(0, 0) }
        this
            .bringIntoViewRequester(requester)
            .onSizeChanged { s: IntSize -> size[0] = s.width; size[1] = s.height }
            .onFocusChanged { state ->
                if (state.hasFocus) scope.launch {
                    withFrameNanos { }
                    requester.bringIntoView(Rect(-px, -px, size[0] + px, size[1] + px))
                }
            }
    }

/** Right from a List row goes to the side card's first button (the row has no bell slot any more). */
internal fun Modifier.nuvioCRightToPanel(panel: FocusRequester?): Modifier =
    if (!NuvioCFeatures.CALENDAR_V68 || panel == null) this else onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.keyCode != AndroidKeyEvent.KEYCODE_DPAD_RIGHT) return@onPreviewKeyEvent false
        if (native.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
        runCatching { panel.requestFocus() }.isSuccess
    }
