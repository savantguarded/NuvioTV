package com.nuvio.tv.data.nuvioc

import com.nuvio.tv.domain.model.WatchedItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// [fork] Nuvio C round 52 `taste_tabs`: the numbers the four Taste tabs show beyond NuvioCTasteStats (when you watch
// by hour, studios, the facts line, streaks, last 7 / 30 days, and Wrapped for any year). Pure functions over the
// active profile's own history and the title facts already cached on the TV: nothing is fetched, nothing is shared
// between profiles.

data class NuvioCTasteFacts(
    val hours: Int,
    val films: Int,
    val episodes: Int,
    val shows: Int,
    val rewatches: Int,
    val plays: Int,
    val filmMinutes: Int,
    val showMinutes: Int,
    /** Plays per hour of the day, 0..23. */
    val byHour: List<Int>,
    /** Plays per weekday, Monday first. */
    val byWeekday: List<Int>,
    val studios: List<Pair<String, Int>>,
    val avgFilmMinutes: Int?,
    val longestFilm: Pair<String, Int>?,
    val firstWatch: Pair<String, LocalDate>?,
    val latestWatch: Pair<String, LocalDate>?,
    val currentStreak: Int,
    val longestStreak: Int,
    val longestStreakEnd: LocalDate?,
    val biggestDay: Pair<LocalDate, Int>?,
    val last7Minutes: Int,
    val prev7Minutes: Int,
    val last30Minutes: Int,
    val prev30Minutes: Int
) {
    val empty: Boolean get() = plays == 0
}

data class NuvioCTasteWrapped(
    val year: Int,
    val hours: Int,
    val episodes: Int,
    val films: Int,
    val shows: Int,
    val biggestDay: LocalDate?,
    val biggestDayTitles: Int,
    val biggestDayMinutes: Int,
    val longestStreak: Int,
    val streakFrom: LocalDate?,
    val streakTo: LocalDate?,
    /** 0 morning, 1 afternoon, 2 evening, 3 late at night. */
    val peakPart: Int?,
    val firstTitle: String?,
    val firstDate: LocalDate?,
    /** name, episodes, minutes */
    val topShows: List<Triple<String, Int, Int>>,
    /** genre, minutes */
    val topGenres: List<Pair<String, Int>>,
    /** minutes per month, January first */
    val byMonth: List<Int>
)

private fun usableLine(w: WatchedItem) = w.contentId.startsWith("tt") || w.contentId.startsWith("tmdb:")
private fun isShowLine(w: WatchedItem) = w.season != null || w.contentType.equals("series", true) || w.contentType.equals("tv", true)
private fun minutesOf(w: WatchedItem, titles: Map<String, NuvioCTasteTitle>): Int =
    titles[tasteKeyOf(w)]?.runtime ?: if (isShowLine(w)) 40 else 110
private fun nameOf(w: WatchedItem, titles: Map<String, NuvioCTasteTitle>): String =
    titles[tasteKeyOf(w)]?.name?.takeIf { it.isNotBlank() } ?: w.title

/** Days in a row with at least one play, the longest run and the run that reaches today (or yesterday). */
internal fun tasteStreaks(days: Set<LocalDate>, today: LocalDate): Triple<Int, Int, LocalDate?> {
    if (days.isEmpty()) return Triple(0, 0, null)
    var longest = 0
    var longestEnd: LocalDate? = null
    var run = 0
    var prev: LocalDate? = null
    days.sorted().forEach { d ->
        run = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) run + 1 else 1
        if (run > longest) { longest = run; longestEnd = d }
        prev = d
    }
    var current = 0
    var cursor = if (today in days) today else today.minusDays(1)
    while (cursor in days) { current++; cursor = cursor.minusDays(1) }
    return Triple(current, longest, longestEnd)
}

internal fun computeTasteFacts(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    window: LongRange,
    nowMs: Long,
    zone: ZoneId
): NuvioCTasteFacts {
    val all = history.filter(::usableLine)
    val lines = all.filter { it.watchedAt in window }
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    fun dayOf(w: WatchedItem) = Instant.ofEpochMilli(w.watchedAt).atZone(zone).toLocalDate()
    val filmLines = lines.filter { !isShowLine(it) }
    val showLines = lines.filter(::isShowLine)
    val filmKeys = filmLines.groupBy(::tasteKeyOf)
    val showKeys = showLines.map(::tasteKeyOf).toSet()
    val filmMinutes = filmLines.sumOf { minutesOf(it, titles) }
    val showMinutes = showLines.sumOf { minutesOf(it, titles) }
    val byHour = IntArray(24)
    val byWeekday = IntArray(7)
    lines.forEach { w ->
        val t = Instant.ofEpochMilli(w.watchedAt).atZone(zone)
        byHour[t.hour]++
        byWeekday[t.dayOfWeek.value - 1]++
    }
    val studios = HashMap<String, Int>()
    lines.map(::tasteKeyOf).toSet().forEach { k -> titles[k]?.studios?.distinct()?.forEach { s -> studios[s] = (studios[s] ?: 0) + 1 } }
    val filmRuntimes = filmKeys.keys.mapNotNull { k -> titles[k]?.runtime?.takeIf { it > 0 }?.let { k to it } }
    val longest = filmRuntimes.maxByOrNull { it.second }?.let { (k, m) -> (titles[k]?.name ?: k) to m }
    val first = lines.minByOrNull { it.watchedAt }?.let { nameOf(it, titles) to dayOf(it) }
    val latest = lines.maxByOrNull { it.watchedAt }?.let { nameOf(it, titles) to dayOf(it) }
    val days = lines.map(::dayOf).toSet()
    val (current, longestRun, longestEnd) = tasteStreaks(all.map(::dayOf).toSet(), today)
    val inWindowStreak = tasteStreaks(days, today)
    val perDay = lines.groupBy(::dayOf).mapValues { (_, l) -> l.sumOf { minutesOf(it, titles) } }
    val biggest = perDay.maxByOrNull { it.value }?.let { it.key to it.value }
    fun minutesBetween(fromDaysAgo: Long, toDaysAgo: Long): Int {
        val from = nowMs - fromDaysAgo * DAY
        val to = nowMs - toDaysAgo * DAY
        return all.filter { it.watchedAt in from until to }.sumOf { minutesOf(it, titles) }
    }
    return NuvioCTasteFacts(
        hours = (filmMinutes + showMinutes) / 60,
        films = filmKeys.size,
        episodes = showLines.count { it.season != null },
        shows = showKeys.size,
        rewatches = filmKeys.values.sumOf { (it.size - 1).coerceAtLeast(0) },
        plays = lines.size,
        filmMinutes = filmMinutes,
        showMinutes = showMinutes,
        byHour = byHour.toList(),
        byWeekday = byWeekday.toList(),
        studios = studios.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
        avgFilmMinutes = filmRuntimes.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt(),
        longestFilm = longest,
        firstWatch = first,
        latestWatch = latest,
        currentStreak = current,
        longestStreak = if (window.first == Long.MIN_VALUE) longestRun else inWindowStreak.second,
        longestStreakEnd = if (window.first == Long.MIN_VALUE) longestEnd else inWindowStreak.third,
        biggestDay = biggest,
        last7Minutes = minutesBetween(7, 0),
        prev7Minutes = minutesBetween(14, 7),
        last30Minutes = minutesBetween(30, 0),
        prev30Minutes = minutesBetween(60, 30)
    )
}

/** Years with at least [minTitles] plays, newest first. */
internal fun tasteWrappedYears(history: List<WatchedItem>, zone: ZoneId, minTitles: Int = 10): List<Int> =
    history.filter(::usableLine)
        .groupBy { Instant.ofEpochMilli(it.watchedAt).atZone(zone).year }
        .filter { it.value.size >= minTitles }
        .keys.sortedDescending()

internal fun computeTasteWrapped(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    year: Int,
    zone: ZoneId
): NuvioCTasteWrapped? {
    val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val lines = history.filter { usableLine(it) && it.watchedAt in start..end }
    if (lines.isEmpty()) return null
    fun at(w: WatchedItem) = Instant.ofEpochMilli(w.watchedAt).atZone(zone)
    val films = lines.filter { !isShowLine(it) }
    val eps = lines.filter(::isShowLine)
    val minutes = lines.sumOf { minutesOf(it, titles) }
    val perDay = lines.groupBy { at(it).toLocalDate() }
    val biggest = perDay.maxByOrNull { (_, l) -> l.sumOf { minutesOf(it, titles) } }
    // the longest run of days in a row inside the year
    var bestLen = 0
    var bestFrom: LocalDate? = null
    var bestTo: LocalDate? = null
    var runFrom: LocalDate? = null
    var prev: LocalDate? = null
    perDay.keys.sorted().forEach { d ->
        if (prev == null || ChronoUnit.DAYS.between(prev, d) != 1L) runFrom = d
        val len = ChronoUnit.DAYS.between(runFrom, d).toInt() + 1
        if (len > bestLen) { bestLen = len; bestFrom = runFrom; bestTo = d }
        prev = d
    }
    val parts = IntArray(4)
    lines.forEach { w ->
        val h = at(w).hour
        parts[when (h) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }]++
    }
    val first = lines.minByOrNull { it.watchedAt }
    val topShows = eps.groupBy(::tasteKeyOf).map { (_, l) ->
        Triple(nameOf(l.first(), titles), l.count { it.season != null }, l.sumOf { minutesOf(it, titles) })
    }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third }).take(5)
    val genreMinutes = HashMap<String, Int>()
    lines.forEach { w -> titles[tasteKeyOf(w)]?.genres?.distinct()?.forEach { g -> genreMinutes[g] = (genreMinutes[g] ?: 0) + minutesOf(w, titles) } }
    val byMonth = IntArray(12)
    lines.forEach { byMonth[at(it).monthValue - 1] += minutesOf(it, titles) }
    return NuvioCTasteWrapped(
        year = year,
        hours = minutes / 60,
        episodes = eps.count { it.season != null },
        films = films.map(::tasteKeyOf).toSet().size,
        shows = eps.map(::tasteKeyOf).toSet().size,
        biggestDay = biggest?.key,
        biggestDayTitles = biggest?.value?.map(::tasteKeyOf)?.toSet()?.size ?: 0,
        biggestDayMinutes = biggest?.value?.sumOf { minutesOf(it, titles) } ?: 0,
        longestStreak = bestLen,
        streakFrom = bestFrom,
        streakTo = bestTo,
        peakPart = parts.indices.maxByOrNull { parts[it] }?.takeIf { parts[it] > 0 },
        firstTitle = first?.let { nameOf(it, titles) },
        firstDate = first?.let { at(it).toLocalDate() },
        topShows = topShows,
        topGenres = genreMinutes.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
        byMonth = byMonth.toList()
    )
}

/** Monday-first weekday of the highest count, for "Mondays most of all". */
internal fun tastePeakWeekday(byWeekday: List<Int>): DayOfWeek? =
    byWeekday.indices.maxByOrNull { byWeekday[it] }?.takeIf { byWeekday[it] > 0 }?.let { DayOfWeek.of(it + 1) }

private const val DAY = 24L * 60 * 60 * 1000
