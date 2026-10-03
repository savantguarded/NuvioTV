package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: after a player panel closes (subtitles, audio, sources, episodes, speed…), focus
// goes back to the button that opened it instead of Play/Pause. PlayerScreen.kt only gets a few
// hook lines: ControlButton asks this for its FocusRequester and reports clicks; the official
// "panel closed" focus step calls restore(). Switch: NuvioCFeatures.FOCUS_RETURN.

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.delay

@Stable
internal class NuvioCFocusMemory {
    private val requesters = HashMap<String, FocusRequester>()
    private var lastKey: String? = null
    private var lastInMoreRow = false

    /** Kept current by PlayerScreen: is the "more" row of buttons open right now? */
    var moreRowOpen = false

    /** The requester a control button should use: its own if it has one, else one kept here. */
    fun requester(key: String, own: FocusRequester?): FocusRequester? {
        if (!NuvioCFeatures.FOCUS_RETURN) return own
        if (own != null) {
            requesters[key] = own
            return own
        }
        return requesters.getOrPut(key) { FocusRequester() }
    }

    fun onClick(key: String) {
        if (!NuvioCFeatures.FOCUS_RETURN) return
        lastKey = key
        lastInMoreRow = moreRowOpen
    }

    /** The controls went away on their own: next time they open, start on Play/Pause again. */
    fun forget() {
        lastKey = null
    }

    /**
     * Focus the button that opened the panel that just closed, else [fallback] (Play/Pause).
     * A button in the "more" row reopens that row first (opening a panel folds it away).
     */
    suspend fun restore(fallback: FocusRequester, reopenMoreRow: () -> Unit) {
        val target = lastKey?.let { requesters[it] }
        if (!NuvioCFeatures.FOCUS_RETURN || target == null) {
            fallback.requestFocus()
            return
        }
        if (lastInMoreRow && !moreRowOpen) {
            reopenMoreRow()
            delay(250) // the row slides in
        }
        if (!target.requestFocusAfterFrames(frames = 0)) {
            fallback.requestFocus()
        }
    }
}

internal val LocalNuvioCFocusMemory = staticCompositionLocalOf<NuvioCFocusMemory?> { null }
