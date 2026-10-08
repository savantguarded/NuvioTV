package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C skip-intro focus fix. Official bug: when the focused Skip Intro / Recap / Outro
// button goes away (pressed, dismissed with Back, or its 10 s timer ran out) nothing takes focus
// back, so the remote seems dead until a few presses land somewhere. Here, if the button had focus
// when it hid, focus goes to the player itself (controls closed) or to Play/Pause (controls open),
// unless the next-episode card is up (it takes focus on its own).
// Switch: NuvioCFeatures.SKIP_INTRO_FOCUS.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.nuvio.tv.NuvioCFeatures

internal class NuvioCSkipFocusState {
    var focused: Boolean = false
}

@Composable
internal fun rememberNuvioCSkipFocus(): NuvioCSkipFocusState = remember { NuvioCSkipFocusState() }

/** Put on the Skip Intro button's modifier: remembers whether it holds focus. */
internal fun Modifier.nuvioCSkipFocusTracker(state: NuvioCSkipFocusState): Modifier =
    if (!NuvioCFeatures.SKIP_INTRO_FOCUS) this else onFocusChanged { state.focused = it.hasFocus }

/**
 * Runs when the button's visibility flips. The button reports "hidden" while its exit animation is
 * still on screen (and still focused), so the focus move happens before the button is removed.
 */
@Composable
internal fun NuvioCSkipFocusEffect(
    state: NuvioCSkipFocusState,
    skipVisible: Boolean,
    controlsVisible: Boolean,
    nextEpisodeCardUp: Boolean,
    container: FocusRequester,
    playPause: FocusRequester
) {
    if (!NuvioCFeatures.SKIP_INTRO_FOCUS) return
    val currentControls by rememberUpdatedState(controlsVisible)
    val currentNextCard by rememberUpdatedState(nextEpisodeCardUp)
    LaunchedEffect(skipVisible) {
        if (skipVisible || !state.focused) return@LaunchedEffect
        state.focused = false
        if (currentNextCard) return@LaunchedEffect
        runCatching { (if (currentControls) playPause else container).requestFocus() }
    }
}
