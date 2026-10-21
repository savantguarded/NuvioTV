package com.nuvio.tv.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C round 17 `calendar_tab_focus`: the List / Week / Month buttons are easy to reach.
// - Back from anywhere in the view jumps to the button of the view you're in; Back there leaves as before.
// - Focus arriving on the buttons from below (Up) always lands on the current view's button.
// - Down from the buttons goes back to the card you left (focus restorer on the view).
// Kept in its own file so ysosrs123's calendar files only carry three hook lines.

/** On the row of view buttons. [selected] = the current view's button, [content] = the view below. */
internal fun Modifier.nuvioCCalendarTabs(selected: FocusRequester, content: FocusRequester): Modifier =
    if (!NuvioCFeatures.CALENDAR_TAB_FOCUS) this else this
        .focusProperties { enter = { selected } }
        .focusGroup()
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                runCatching { content.requestFocus() }
                true
            } else {
                false
            }
        }

/** On the box holding the List / Week / Month view. */
internal fun Modifier.nuvioCCalendarContent(content: FocusRequester, onFocus: (Boolean) -> Unit): Modifier =
    if (!NuvioCFeatures.CALENDAR_TAB_FOCUS) this else this
        .onFocusChanged { onFocus(it.hasFocus) }
        .focusRequester(content)
        .focusRestorer()
        .focusGroup()

/** Back while a card has focus = go to the view buttons. */
@Composable
internal fun NuvioCCalendarBackToTabs(contentFocused: Boolean, busy: Boolean, selected: FocusRequester) {
    BackHandler(enabled = NuvioCFeatures.CALENDAR_TAB_FOCUS && contentFocused && !busy) {
        runCatching { selected.requestFocus() }
    }
}
