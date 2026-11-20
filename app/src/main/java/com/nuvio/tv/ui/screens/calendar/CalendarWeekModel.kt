package com.nuvio.tv.ui.screens.calendar

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class CalendarFilter { ALL, SHOWS, FILMS;

    fun keeps(row: CalendarRow): Boolean = when (this) {
        ALL -> true
        SHOWS -> !row.isFilm
        FILMS -> row.isFilm
    }
}

/** Every dated release of each title in time order, for "Next from". */
fun calendarRowsByTitle(days: Map<LocalDate, List<CalendarRow>>): Map<String, List<CalendarRow>> =
    days.entries.sortedBy { it.key }
        .flatMap { (_, rows) -> rows }
        .groupBy { it.titleKey }

/** The next [max] releases of [row]'s title after it. */
fun calendarNextFrom(byTitle: Map<String, List<CalendarRow>>, row: CalendarRow, max: Int = 2): List<CalendarRow> {
    val rows = byTitle[row.titleKey].orEmpty()
    val date = row.date ?: return emptyList()
    val index = rows.indexOfFirst { it.key == row.key }
    val later = if (index >= 0) rows.drop(index + 1) else rows.filter { it.date != null && it.date > date }
    return later.filter { it.date != null && it.date >= date && it.key != row.key }.take(max)
}

enum class CalendarMosaicLayout { NONE, FULL, SPLIT, GRID }

/** How a month day shows its titles: one picture, two halves, or a 2 x 2 grid whose last tile may be "+N". */
@Immutable
data class CalendarMosaic(
    val layout: CalendarMosaicLayout,
    val tiles: List<CalendarRow>,
    val more: Int = 0
)

fun calendarMosaic(rows: List<CalendarRow>?, filter: CalendarFilter = CalendarFilter.ALL): CalendarMosaic {
    val titles = rows.orEmpty().filter(filter::keeps).distinctBy { it.titleKey }
    return when (titles.size) {
        0 -> CalendarMosaic(CalendarMosaicLayout.NONE, emptyList())
        1 -> CalendarMosaic(CalendarMosaicLayout.FULL, titles)
        2 -> CalendarMosaic(CalendarMosaicLayout.SPLIT, titles)
        3, 4 -> CalendarMosaic(CalendarMosaicLayout.GRID, titles)
        else -> CalendarMosaic(CalendarMosaicLayout.GRID, titles.take(3), more = titles.size - 3)
    }
}
