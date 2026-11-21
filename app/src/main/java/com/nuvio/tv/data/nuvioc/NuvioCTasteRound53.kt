package com.nuvio.tv.data.nuvioc

import com.nuvio.tv.domain.model.WatchedItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// [fork] Nuvio C round 53 `taste_tabs_v2`: the numbers behind the reworked Overview, Stats and Wrapped tabs. Pure
// functions over the active profile's own history, scores and the title facts already on the TV: nothing is fetched,
// nothing is shared between profiles. Every figure here is one the app can actually read (no rewatch counts: Nuvio
// can't tell a rewatch from a re-sync).

/** [fork] round 53b: everything one Taste scope (all time / a year / a month) shows. */
data class NuvioCTasteScopeData(
    val window: LongRange,
    val stats: NuvioCTasteStats,
    val overview: NuvioCTasteOverview,
    val facts: NuvioCTasteFacts,
    val strip: NuvioCTasteStatsStrip
)

data class NuvioCTasteLine(val name: String, val sub: String, val date: LocalDate, /** [fork] round 57: thumbnail */ val poster: String? = null, /** [fork] round 57: opens the title */ val id: String? = null, val type: String? = null)

data class NuvioCTasteOverview(
    val minutes: Int,
    /** Same-length period just before the window (null for All time or when nothing was watched then). */
    val prevMinutes: Int?,
    /** "2025", "October": what the comparison is against. */
    val prevLabel: String?,
    val titles: Int,
    val films: Int,
    val shows: Int,
    val episodes: Int,
    val daysWatched: Int,
    val daysInRange: Int,
    val longestStreak: Int,
    /** name, episodes, minutes, last watched */
    val mostShow: NuvioCTasteMostShow?,
    val biggestDay: NuvioCTasteBigDay?,
    val finishedShows: Int,
    val inProgressShows: Int,
    val upToDateShows: Int,
    /** Share (0..100) of titles watched within about a year of release, null without years. */
    val freshPercent: Int?,
    val oldest: Pair<String, Int>?,
    val recentFinished: List<NuvioCTasteLine>,
    val binges: List<NuvioCTasteLine>
) {
    val empty: Boolean get() = titles == 0
}

data class NuvioCTasteMostShow(val name: String, val episodes: Int, val minutes: Int, val last: LocalDate, /** [fork] round 57 */ val poster: String? = null, val id: String? = null)
data class NuvioCTasteBigDay(val date: LocalDate, val minutes: Int, val topTitle: String?, val topCount: Int, val topIsShow: Boolean)

data class NuvioCTasteStatsStrip(
    /** language name, share 0..100, top three */
    val languages: List<Pair<String, Int>>,
    val countries: Int,
    val topCountries: List<String>,
    val avgFilmMinutes: Int?,
    val longestFilm: Pair<String, Int>?,
    val avgEpisodeMinutes: Int?
)

data class NuvioCTasteYear(
    val year: Int,
    val hours: Int,
    val titles: Int,
    /** name, sub line, date of last watch in the year */
    val topShows: List<NuvioCTasteLine>,
    val topFilms: List<NuvioCTasteLine>,
    val byMonth: List<Int>,
    val firstTitle: Pair<String, LocalDate>?,
    val lastTitle: Pair<String, LocalDate>?,
    val newShows: Int,
    val newDirectors: Int,
    /** year -> hours, newest first, for the Year dropdown */
    val hoursByYear: List<Pair<Int, Int>>,
    // [fork] round 53b: more of the year
    val films: Int = 0,
    val episodes: Int = 0,
    val biggestDay: LocalDate? = null,
    val biggestDayMinutes: Int = 0,
    val longestStreak: Int = 0,
    val topGenre: String? = null,
    val topFace: Pair<String, Int>? = null,
    /** 0 mornings, 1 afternoons, 2 evenings, 3 late nights */
    val peakPart: Int? = null,
    val busiestMonth: Int? = null,
    val rated: Int = 0,
    val avgRating: Double? = null
)

private fun usable53(w: WatchedItem) = w.contentId.startsWith("tt") || w.contentId.startsWith("tmdb:")
private fun isShow53(w: WatchedItem) = w.season != null || w.contentType.equals("series", true) || w.contentType.equals("tv", true)
private fun mins53(w: WatchedItem, titles: Map<String, NuvioCTasteTitle>): Int =
    titles[tasteKeyOf(w)]?.runtime?.takeIf { it > 0 } ?: if (isShow53(w)) 40 else 110
/** [fork] round 57 taste_v3: the title's poster for row thumbnails. */
private fun poster53(w: WatchedItem, titles: Map<String, NuvioCTasteTitle>): String? =
    titles[tasteKeyOf(w)]?.poster?.takeIf { it.isNotBlank() } ?: w.poster?.takeIf { it.isNotBlank() }
/** [fork] round 57 taste_v3: the id and type that open the title's details. */
private fun id53(w: WatchedItem): String = w.contentId.trim().let { if (it.startsWith("tmdb:")) it else it.substringBefore(':') }
private fun type53(w: WatchedItem): String = if (isShow53(w)) "series" else "movie"

private fun name53(w: WatchedItem, titles: Map<String, NuvioCTasteTitle>): String =
    titles[tasteKeyOf(w)]?.name?.takeIf { it.isNotBlank() } ?: w.title

/** The window just before [window] with the same length, and how to name it. Null for All time. */
internal fun tastePreviousWindow(window: LongRange, zone: ZoneId, nowMs: Long): Pair<LongRange, String>? {
    if (window.first == Long.MIN_VALUE) return null
    val start = Instant.ofEpochMilli(window.first).atZone(zone).toLocalDate()
    val endMs = minOf(window.last, nowMs)
    return if (start.dayOfYear == 1 && Instant.ofEpochMilli(endMs).atZone(zone).year == start.year &&
        window.last >= nowMs - 1000) {
        // This year so far vs the same days last year
        val from = start.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = Instant.ofEpochMilli(endMs).atZone(zone).minusYears(1).toInstant().toEpochMilli()
        (from..to) to (start.year - 1).toString()
    } else if (start.dayOfMonth == 1) {
        val prev = java.time.YearMonth.from(start).minusMonths(1)
        tasteMonthWindow(prev, zone) to prev.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
    } else {
        val len = endMs - window.first
        ((window.first - len)..window.first - 1) to "the period before"
    }
}

internal fun computeTasteOverview(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    window: LongRange,
    nowMs: Long,
    zone: ZoneId
): NuvioCTasteOverview {
    val all = history.filter(::usable53)
    val lines = all.filter { it.watchedAt in window }
    fun day(w: WatchedItem) = Instant.ofEpochMilli(w.watchedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val minutes = lines.sumOf { mins53(it, titles) }
    val prev = tastePreviousWindow(window, zone, nowMs)
    val prevMinutes = prev?.let { (w, _) -> all.filter { it.watchedAt in w }.sumOf { mins53(it, titles) } }?.takeIf { it > 0 }
    val films = lines.filter { !isShow53(it) }
    val eps = lines.filter(::isShow53)
    val filmKeys = films.map(::tasteKeyOf).toSet()
    val showKeys = eps.map(::tasteKeyOf).toSet()
    val days = lines.map(::day).toSet()
    val firstDay = (if (window.first == Long.MIN_VALUE) all.minOfOrNull { it.watchedAt } else window.first)
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val lastDay = minOf(today, Instant.ofEpochMilli(minOf(window.last, nowMs)).atZone(zone).toLocalDate())
    val daysInRange = firstDay?.let { (ChronoUnit.DAYS.between(it, lastDay) + 1).toInt().coerceAtLeast(1) } ?: 0
    val streak = tasteStreaks(days, today).second

    // most watched show: episodes, then minutes
    val byShow = eps.groupBy(::tasteKeyOf)
    val most = byShow.maxWithOrNull(compareBy<Map.Entry<String, List<WatchedItem>>> { e -> e.value.count { it.season != null } }
        .thenBy { e -> e.value.sumOf { mins53(it, titles) } })
        ?.let { (_, l) -> NuvioCTasteMostShow(name53(l.first(), titles), l.count { it.season != null }, l.sumOf { mins53(it, titles) }, day(l.maxBy { it.watchedAt }), poster53(l.first(), titles), id53(l.first())) }

    // biggest day and what filled it
    val perDay = lines.groupBy(::day)
    val big = perDay.maxByOrNull { (_, l) -> l.sumOf { mins53(it, titles) } }?.let { (d, l) ->
        val top = l.groupBy(::tasteKeyOf).maxByOrNull { it.value.size }
        NuvioCTasteBigDay(d, l.sumOf { mins53(it, titles) }, top?.value?.first()?.let { name53(it, titles) }, top?.value?.size ?: 0,
            top?.value?.first()?.let(::isShow53) ?: false)
    }

    // shows: finished (ended, every episode seen), up to date (running, every episode seen), in progress (the rest)
    var finished = 0; var upToDate = 0; var inProgress = 0
    val finishedLines = ArrayList<NuvioCTasteLine>()
    val allByShow = all.filter(::isShow53).groupBy(::tasteKeyOf)
    showKeys.forEach { k ->
        val seen = allByShow[k].orEmpty().filter { it.season != null && it.season > 0 }.map { it.season to it.episode }.toSet().size
        val t = titles[k]
        val total = t?.episodes
        when {
            total == null || total <= 0 -> inProgress++
            seen >= total && t!!.ended -> {
                finished++
                byShow[k]?.maxByOrNull { it.watchedAt }?.let { finishedLines += NuvioCTasteLine(name53(it, titles), "$total eps", day(it), poster53(it, titles), id53(it), type53(it)) }
            }
            seen >= total -> upToDate++
            else -> inProgress++
        }
    }
    films.groupBy(::tasteKeyOf).forEach { (k, l) ->
        val last = l.maxBy { it.watchedAt }
        finishedLines += NuvioCTasteLine(name53(last, titles), (titles[k]?.year?.let { "Film · $it" } ?: "Film"), day(last), poster53(last, titles), id53(last), type53(last))
    }

    // fresh vs catalogue: watched within the release year or the year after
    val dated = lines.groupBy(::tasteKeyOf).mapNotNull { (k, l) -> titles[k]?.year?.let { y -> y to day(l.minBy { it.watchedAt }).year } }
    val fresh = dated.takeIf { it.isNotEmpty() }?.let { d -> d.count { (y, w) -> w - y <= 1 } * 100 / d.size }
    val oldest = lines.map(::tasteKeyOf).toSet().mapNotNull { k -> titles[k]?.let { t -> t.year?.let { t.name.ifBlank { null }?.to(it) } } }
        .minByOrNull { it.second }

    // binges: each show's best day, most episodes first
    val binges = eps.filter { it.season != null }.groupBy { tasteKeyOf(it) to day(it) }
        .map { (kd, l) -> Triple(kd.first, kd.second, l) }
        .groupBy { it.first }
        .mapNotNull { (_, days) -> days.maxWithOrNull(compareBy<Triple<String, LocalDate, List<WatchedItem>>> { it.third.size }.thenBy { it.second }) }
        .filter { it.third.size >= 2 }
        .sortedWith(compareByDescending<Triple<String, LocalDate, List<WatchedItem>>> { it.third.size }.thenByDescending { it.second })
        .take(5)
        .map { (_, d, l) -> NuvioCTasteLine(name53(l.first(), titles), "${l.size} eps", d, poster53(l.first(), titles), id53(l.first()), type53(l.first())) }

    return NuvioCTasteOverview(
        minutes = minutes,
        prevMinutes = prevMinutes,
        prevLabel = prev?.second,
        titles = filmKeys.size + showKeys.size,
        films = filmKeys.size,
        shows = showKeys.size,
        episodes = eps.count { it.season != null },
        daysWatched = days.size,
        daysInRange = daysInRange,
        longestStreak = streak,
        mostShow = most,
        biggestDay = big,
        finishedShows = finished,
        inProgressShows = inProgress,
        upToDateShows = upToDate,
        freshPercent = fresh,
        oldest = oldest,
        recentFinished = finishedLines.sortedByDescending { it.date }.take(5),
        binges = binges
    )
}

private val LANG_NAMES = mapOf(
    "en" to "English", "ko" to "Korean", "ja" to "Japanese", "es" to "Spanish", "fr" to "French", "de" to "German",
    "it" to "Italian", "hi" to "Hindi", "zh" to "Chinese", "cn" to "Cantonese", "pt" to "Portuguese", "sv" to "Swedish",
    "da" to "Danish", "no" to "Norwegian", "tr" to "Turkish", "ru" to "Russian", "th" to "Thai", "yo" to "Yoruba",
    "pl" to "Polish", "nl" to "Dutch", "ar" to "Arabic", "fi" to "Finnish", "ta" to "Tamil", "te" to "Telugu", "id" to "Indonesian"
)

internal fun tasteLanguageName(code: String): String =
    LANG_NAMES[code.lowercase()] ?: runCatching { java.util.Locale(code).getDisplayLanguage(java.util.Locale.US) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code

internal fun computeTasteStatsStrip(history: List<WatchedItem>, titles: Map<String, NuvioCTasteTitle>, window: LongRange): NuvioCTasteStatsStrip {
    val keys = history.filter { usable53(it) && it.watchedAt in window }.map(::tasteKeyOf).toSet()
    val facts = keys.mapNotNull { titles[it] }
    val langs = facts.mapNotNull { it.lang?.takeIf { l -> l.isNotBlank() } }
    val langShare = langs.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) 5 else 3) // [fork] round 58: Languages card shows five
        .map { tasteLanguageName(it.key) to (it.value * 100 / langs.size.coerceAtLeast(1)) }
    val countries = facts.flatMap { it.countries.distinct() }
    val topCountries = countries.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    val films = facts.filter { !it.isShow && (it.runtime ?: 0) > 0 }
    val shows = facts.filter { it.isShow && (it.runtime ?: 0) > 0 }
    return NuvioCTasteStatsStrip(
        languages = langShare,
        countries = countries.toSet().size,
        topCountries = topCountries,
        avgFilmMinutes = films.takeIf { it.isNotEmpty() }?.map { it.runtime!! }?.average()?.toInt(),
        longestFilm = films.maxByOrNull { it.runtime!! }?.let { it.name to it.runtime!! },
        avgEpisodeMinutes = shows.takeIf { it.isNotEmpty() }?.map { it.runtime!! }?.average()?.toInt()
    )
}

internal fun computeTasteYear(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    scores: Map<String, Int>,
    year: Int,
    zone: ZoneId,
    years: List<Int>
): NuvioCTasteYear? {
    val all = history.filter(::usable53)
    fun at(w: WatchedItem) = Instant.ofEpochMilli(w.watchedAt).atZone(zone)
    val lines = all.filter { at(it).year == year }
    if (lines.isEmpty()) return null
    val minutes = lines.sumOf { mins53(it, titles) }
    val eps = lines.filter(::isShow53)
    val films = lines.filter { !isShow53(it) }
    val topShows = eps.groupBy(::tasteKeyOf).map { (_, l) ->
        val n = l.count { it.season != null }
        Triple(l, n, l.sumOf { mins53(it, titles) })
    }.sortedWith(compareByDescending<Triple<List<WatchedItem>, Int, Int>> { it.second }.thenByDescending { it.third }).take(5)
        .map { (l, n, m) -> NuvioCTasteLine(name53(l.first(), titles), "$n eps · ${m / 60}h", at(l.maxBy { it.watchedAt }).toLocalDate(), poster53(l.first(), titles), id53(l.first()), type53(l.first())) }
    val topFilms = films.groupBy(::tasteKeyOf).map { (k, l) -> Triple(k, l.maxBy { it.watchedAt }, tasteScoreFor(k, titles, scores)) }
        .sortedWith(compareByDescending<Triple<String, WatchedItem, Int?>> { it.third ?: -1 }.thenByDescending { it.second.watchedAt })
        .take(5)
        .map { (_, w, s) ->
            val d = at(w).toLocalDate()
            NuvioCTasteLine(name53(w, titles), (s?.let { "$it/10 · " } ?: "") + d.month.nuvioC3() + " ${d.dayOfMonth}", d, poster53(w, titles), id53(w), type53(w))
        }
    val byMonth = IntArray(12)
    lines.forEach { byMonth[at(it).monthValue - 1] += mins53(it, titles) }
    val first = lines.minBy { it.watchedAt }
    val last = lines.maxBy { it.watchedAt }
    val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    val before = all.filter { it.watchedAt < start }
    val seenShows = before.filter(::isShow53).map(::tasteKeyOf).toSet()
    val newShows = eps.map(::tasteKeyOf).toSet().count { it !in seenShows }
    val seenDirectors = before.map(::tasteKeyOf).toSet().flatMap { titles[it]?.directors.orEmpty().map { d -> d.id } }.toSet()
    val newDirectors = lines.map(::tasteKeyOf).toSet().flatMap { titles[it]?.directors.orEmpty().map { d -> d.id } }.toSet().count { it !in seenDirectors }
    val hoursByYear = years.map { y -> y to all.filter { at(it).year == y }.sumOf { mins53(it, titles) } / 60 }
    val keys = lines.map(::tasteKeyOf).toSet()
    val perDay = lines.groupBy { at(it).toLocalDate() }.mapValues { (_, l) -> l.sumOf { mins53(it, titles) } }
    val big = perDay.maxByOrNull { it.value }
    val streak = tasteStreaks(perDay.keys, LocalDate.of(year, 12, 31)).second
    val genre = keys.flatMap { titles[it]?.genres.orEmpty().distinct() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val face = keys.flatMap { titles[it]?.cast.orEmpty().take(6).map { p -> p.name } }.groupingBy { it }.eachCount()
        .filter { it.value >= 2 }.maxByOrNull { it.value }?.let { it.key to it.value }
    val parts = IntArray(4)
    lines.forEach { w -> parts[when (at(w).hour) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }]++ }
    val yearScores = keys.mapNotNull { tasteScoreFor(it, titles, scores) }
    return NuvioCTasteYear(
        films = films.map(::tasteKeyOf).toSet().size,
        episodes = eps.count { it.season != null },
        biggestDay = big?.key,
        biggestDayMinutes = big?.value ?: 0,
        longestStreak = streak,
        topGenre = genre,
        topFace = face,
        peakPart = parts.indices.maxByOrNull { parts[it] }?.takeIf { parts[it] > 0 },
        busiestMonth = byMonth.indices.maxByOrNull { byMonth[it] }?.takeIf { byMonth[it] > 0 },
        rated = yearScores.size,
        avgRating = yearScores.takeIf { it.size >= 3 }?.average(),
        year = year,
        hours = minutes / 60,
        titles = keys.size,
        topShows = topShows,
        topFilms = topFilms,
        byMonth = byMonth.toList(),
        firstTitle = name53(first, titles) to at(first).toLocalDate(),
        lastTitle = name53(last, titles) to at(last).toLocalDate(),
        newShows = newShows,
        newDirectors = newDirectors,
        hoursByYear = hoursByYear
    )
}
