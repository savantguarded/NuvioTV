package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.calendar.CalendarComingUpItem
import com.nuvio.tv.core.calendar.CalendarComingUpWhen
import com.nuvio.tv.core.calendar.CalendarEntry
import com.nuvio.tv.core.calendar.CalendarIds
import com.nuvio.tv.core.calendar.CalendarTitleRecord
import com.nuvio.tv.core.calendar.CalendarTitleType
import com.nuvio.tv.ui.screens.calendar.CalendarFormats
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** [fork] Nuvio C: Calendar cards in the Upcoming row read like official's (In N Days up to 7, then the date). */
class NuvioCInDaysLabelTest {
    private val formats = CalendarFormats.plain(Locale.UK, is24Hour = false)
    private val text = CalendarComingUpText(
        newLabel = "New", today = "Today", todayAt = "Today %1\$s", tonightAt = "Tonight %1\$s", tomorrow = "Tomorrow",
        cinema = "In cinemas", digital = "Digital", inDays = { "In $it Days" }
    )
    private val today = LocalDate.of(2026, 10, 21)
    private val show = CalendarTitleRecord(key = "series:imdb:tt1", type = CalendarTitleType.SERIES, ids = CalendarIds(imdb = "tt1"), name = "Show")

    private fun label(whenKind: CalendarComingUpWhen, days: Long, time: LocalTime? = LocalTime.of(21, 0)) =
        calendarComingUpLabel(CalendarComingUpItem(show, CalendarEntry(season = 1, episode = 2), whenKind, today.plusDays(days), time), text, formats, today)

    @Test fun likeOfficial() {
        assertEquals("Tonight ${formats.time(LocalTime.of(21, 0))}", label(CalendarComingUpWhen.TONIGHT, 0))
        assertEquals("Tomorrow", label(CalendarComingUpWhen.TOMORROW, 1))
        assertEquals("In 2 Days", label(CalendarComingUpWhen.WEEKDAY, 2))
        assertEquals("In 6 Days", label(CalendarComingUpWhen.WEEKDAY, 6))
        assertEquals("In 7 Days", label(CalendarComingUpWhen.DATE, 7))
        assertEquals(formats.shortDate(today.plusDays(8)), label(CalendarComingUpWhen.DATE, 8))
        assertEquals("New", label(CalendarComingUpWhen.NEW, -2))
    }

    @Test fun switchedOffKeepsWeekdays() {
        val plain = text.copy(inDays = null)
        val item = CalendarComingUpItem(show, CalendarEntry(season = 1, episode = 2), CalendarComingUpWhen.WEEKDAY, today.plusDays(3), null)
        assertEquals(formats.weekday(today.plusDays(3).dayOfWeek), calendarComingUpLabel(item, plain, formats, today))
    }
}
