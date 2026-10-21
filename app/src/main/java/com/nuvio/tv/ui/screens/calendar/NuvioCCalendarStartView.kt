package com.nuvio.tv.ui.screens.calendar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [fork] Nuvio C calendar_start_view: which view the Calendar opens in. */
enum class NuvioCCalendarStart { LAST_USED, LIST, WEEK, MONTH }

/**
 * [fork] Nuvio C calendar_start_view: the "Start in" choice and the last view used, kept in a small file of their own
 * so ysosrs123's calendar settings stay as he wrote them. Default: open in the last view used (Week the first time).
 */
interface NuvioCCalendarViewPrefs {
    val start: StateFlow<NuvioCCalendarStart>
    fun setStart(start: NuvioCCalendarStart)
    fun lastUsed(): CalendarViewMode
    fun rememberUsed(mode: CalendarViewMode)
}

/** The view to open in: the fixed choice, or the last one used. */
fun NuvioCCalendarViewPrefs.openingMode(): CalendarViewMode = when (start.value) {
    NuvioCCalendarStart.LAST_USED -> lastUsed()
    NuvioCCalendarStart.LIST -> CalendarViewMode.LIST
    NuvioCCalendarStart.WEEK -> CalendarViewMode.WEEK
    NuvioCCalendarStart.MONTH -> CalendarViewMode.MONTH
}

@Singleton
class NuvioCCalendarViewStore @Inject constructor(
    @param:ApplicationContext context: Context
) : NuvioCCalendarViewPrefs {
    private val prefs by lazy { context.getSharedPreferences("nuvio_c_calendar_view", Context.MODE_PRIVATE) }

    private val _start by lazy {
        MutableStateFlow(
            prefs.getString(KEY_START, null)?.let { runCatching { NuvioCCalendarStart.valueOf(it) }.getOrNull() }
                ?: NuvioCCalendarStart.LAST_USED
        )
    }
    override val start: StateFlow<NuvioCCalendarStart> get() = _start.asStateFlow()

    override fun setStart(start: NuvioCCalendarStart) {
        _start.value = start
        prefs.edit().putString(KEY_START, start.name).apply()
    }

    override fun lastUsed(): CalendarViewMode =
        prefs.getString(KEY_LAST, null)?.let { runCatching { CalendarViewMode.valueOf(it) }.getOrNull() } ?: CalendarViewMode.WEEK

    override fun rememberUsed(mode: CalendarViewMode) {
        if (prefs.getString(KEY_LAST, null) != mode.name) prefs.edit().putString(KEY_LAST, mode.name).apply()
    }

    private companion object {
        const val KEY_START = "start"
        const val KEY_LAST = "last"
    }
}
