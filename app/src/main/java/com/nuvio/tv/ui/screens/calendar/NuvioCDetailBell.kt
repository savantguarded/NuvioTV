package com.nuvio.tv.ui.screens.calendar

// [fork] Nuvio C round 38 `detail_bell`: the Calendar bell on the details page, next to Play.
// Turning it on follows the title through the Calendar's own bell list: a belled title is followed even when it is in
// no library or list (followCalendarTitles adds bell-only titles with the BELL origin), so it gets its dates fetched,
// shows in the Calendar and the Upcoming row, and gives the usual reminder card / corner message once it is out.
// Nothing new is stored: the button only writes the same bell keys the Calendar's own bell writes, so turning it off
// on either screen turns it off on both. A title you hid from the Calendar is unhidden when you ring its bell.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.calendar.CalendarIds
import com.nuvio.tv.core.calendar.CalendarRefreshReason
import com.nuvio.tv.core.calendar.CalendarRefresher
import com.nuvio.tv.core.calendar.CalendarSettingsDataStore
import com.nuvio.tv.core.calendar.CalendarTitleType
import com.nuvio.tv.core.calendar.calendarKeyMatches
import com.nuvio.tv.core.calendar.calendarTitleKey
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the bell is for: the title's type and the ids the Calendar matches on. */
data class NuvioCBellTarget(val type: CalendarTitleType, val ids: CalendarIds) {
    val key: String? get() = calendarTitleKey(type, ids)
}

/** Movie or show with an IMDb or TMDB id, else null (no bell). */
internal fun Meta.nuvioCBellTarget(): NuvioCBellTarget? {
    val isShow = type == ContentType.SERIES || type == ContentType.TV ||
        apiType.equals("series", ignoreCase = true) || apiType.equals("tv", ignoreCase = true)
    val isMovie = type == ContentType.MOVIE || apiType.equals("movie", ignoreCase = true)
    if (!isShow && !isMovie) return null
    val imdb = imdbId?.takeIf { it.startsWith("tt") } ?: id.takeIf { it.startsWith("tt") }?.substringBefore(":")
    val fromId = CalendarIds.fromContentId(id)
    val ids = CalendarIds(imdb = imdb ?: fromId.imdb, tmdb = fromId.tmdb, tvdb = fromId.tvdb)
    if (ids.isEmpty) return null
    return NuvioCBellTarget(if (isShow) CalendarTitleType.SERIES else CalendarTitleType.MOVIE, ids)
}

@HiltViewModel
class NuvioCDetailBellViewModel @Inject constructor(
    private val settingsStore: CalendarSettingsDataStore,
    private val refresher: CalendarRefresher
) : ViewModel() {
    val bells: StateFlow<Set<String>> = settingsStore.settings
        .map { it?.bellOn.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun isOn(target: NuvioCBellTarget?, bells: Set<String>): Boolean =
        target != null && bells.any { calendarKeyMatches(it, target.type, target.ids) }

    fun toggle(target: NuvioCBellTarget) {
        val key = target.key ?: return
        val turnOn = !isOn(target, bells.value)
        viewModelScope.launch {
            // Off removes every bell key naming this title (the Calendar may have stored it under another id).
            settingsStore.setBell(key, turnOn)
            if (turnOn) {
                val hidden = settingsStore.settings.value?.hiddenTitleKeys.orEmpty()
                hidden.filter { calendarKeyMatches(it, target.type, target.ids) }
                    .forEach { settingsStore.setTitleHidden(it, hidden = false) }
                // A plain refresh: the new bell title joins the followed list and is fetched first (bells go first);
                // no service calendars (MDBList / TMDB) are asked again for it.
                refresher.requestRefresh(CalendarRefreshReason.CALENDAR_OPENED)
            }
        }
    }
}

data class NuvioCBellHandle(val available: Boolean, val on: Boolean, val toggle: () -> Unit)

@Composable
fun rememberNuvioCDetailBell(meta: Meta): NuvioCBellHandle {
    if (!NuvioCFeatures.DETAIL_BELL || !NuvioCFeatures.LIBRARY_CALENDAR || !NuvioCFeatures.CALENDAR_BELLS) {
        return NuvioCBellHandle(false, false) {}
    }
    val target = remember(meta.id, meta.imdbId, meta.type, meta.apiType) { meta.nuvioCBellTarget() }
        ?: return NuvioCBellHandle(false, false) {}
    val viewModel: NuvioCDetailBellViewModel = hiltViewModel()
    val bells by viewModel.bells.collectAsState()
    return NuvioCBellHandle(available = true, on = viewModel.isOn(target, bells)) { viewModel.toggle(target) }
}
