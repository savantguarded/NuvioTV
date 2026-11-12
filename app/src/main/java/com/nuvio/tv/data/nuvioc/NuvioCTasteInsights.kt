package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 40 `taste_insights` / `taste_month`: the Taste page's spotlight cards and monthly recaps.
//
// Everything here is pure (history + cached title facts + scores in, cards out) so it is unit-tested and costs no
// requests. Each card appears only when there is enough behind it to be true; a light profile simply gets fewer.
// The page shows six per visit (pickTasteSpotlight), redrawn on every open, with a few cards favoured at the times
// they fit (late-night viewing after 9 pm, busiest day at weekends).

import com.nuvio.tv.domain.model.WatchedItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface NuvioCTasteVisual {
    /** One bar split into parts (fractions of 1), with a label at each end. */
    data class Split(val parts: List<Float>, val start: String? = null, val end: String? = null) : NuvioCTasteVisual
    /** Small columns; [highlight] is drawn in the accent. */
    data class Bars(val values: List<Int>, val highlight: Int, val start: String? = null, val end: String? = null) : NuvioCTasteVisual
    /** Two marks on a 0 to 10 line: you and the crowd. */
    data class Versus(val you: Double, val crowd: Double, val start: String? = null, val end: String? = null) : NuvioCTasteVisual
    /** [done] of [total] segments. */
    data class Steps(val done: Int, val total: Int) : NuvioCTasteVisual
}

sealed interface NuvioCTasteAction {
    data class Spin(val genre: String?, val decade: Int?) : NuvioCTasteAction
    data class Person(val id: Int, val name: String, val director: Boolean) : NuvioCTasteAction
    data class Title(val id: String, val type: String) : NuvioCTasteAction
    /** [fork] round 42 taste_recap_panel: open that month's view ("2026-10"). */
    data class Month(val ym: String) : NuvioCTasteAction
}

data class NuvioCTasteInsight(
    val id: String,
    val kicker: String,
    val value: String,
    val line: String? = null,
    val visual: NuvioCTasteVisual? = null,
    val action: NuvioCTasteAction? = null,
    /** Hours of the day when this card is favoured (null = any time). */
    val hours: Set<Int>? = null,
    val weekend: Boolean = false,
    /** [fork] round 41 taste_card_titles: the titles this card counted (taste keys), for its titles panel. */
    val keys: List<String> = emptyList()
)

data class NuvioCTasteRecap(val month: YearMonth, val titles: Int, val hours: Int, val topGenre: String?)

/** A film collection's parts in release order (TMDB id, name), fetched once per collection. */
data class NuvioCTasteCollection(val id: Int, val name: String, val parts: List<Pair<Int, String>>)

internal fun tasteKeyOf(w: WatchedItem): String {
    val show = w.season != null || w.contentType.equals("series", true) || w.contentType.equals("tv", true)
    val raw = w.contentId.trim().let { if (it.startsWith("tmdb:")) it else it.substringBefore(':') }
    return (if (show) "tv:" else "movie:") + raw
}

private fun usable(w: WatchedItem) = w.contentId.startsWith("tt") || w.contentId.startsWith("tmdb:")

internal fun tasteScoreFor(k: String, titles: Map<String, NuvioCTasteTitle>, scores: Map<String, Int>): Int? {
    val t = titles[k]
    val prefix = if (k.startsWith("tv:")) "show:" else "movie:"
    val raw = k.substringAfter(':')
    return scores[prefix + raw] ?: t?.imdbId?.let { scores[prefix + it] } ?: t?.tmdbId?.let { scores[prefix + "tmdb" + it] }
}

private fun pct(n: Int, of: Int) = if (of <= 0) 0 else (n * 100.0 / of).roundToInt()
private fun one(v: Double) = String.format(Locale.US, "%.1f", v)
private val NF = java.text.NumberFormat.getIntegerInstance(Locale.US)

internal fun tasteCountryName(code: String): String = when (code.uppercase(Locale.US)) {
    "US" -> "US"
    "GB" -> "UK"
    "KR" -> "Korea"
    else -> Locale("", code).getDisplayCountry(Locale.US).ifBlank { code }
}

internal fun tasteDecadeLabel(start: Int): String = if (start >= 2000) "${start}s" else "${start % 100}".padStart(2, '0') + "s"

internal fun pluralGenre(g: String): String = when (g.lowercase()) {
    "comedy" -> "comedies"; "drama" -> "dramas"; "thriller" -> "thrillers"; "western" -> "westerns"
    "documentary" -> "documentaries"; "romance" -> "romances"; "mystery" -> "mysteries"; "war" -> "war films"
    "family" -> "family films"; "music" -> "music films"; "horror" -> "horror"; "animation" -> "animation"
    else -> g.lowercase()
}

private fun minutesLabel(m: Int): String = if (m % 60 == 0) "${m / 60}h" else "${m / 60}h${(m % 60).toString().padStart(2, '0')}"

/**
 * Every spotlight card that has enough behind it for this window. [window] = the history considered (a range, a
 * month or all of it); [allTime] = whether trend cards that compare periods may appear.
 */
internal fun computeTasteInsights(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    scores: Map<String, Int>,
    window: LongRange,
    nowMs: Long,
    zone: ZoneId,
    allTime: Boolean,
    collections: Map<Int, NuvioCTasteCollection> = emptyMap()
): List<NuvioCTasteInsight> {
    val lines = history.filter { usable(it) && it.watchedAt in window }
    if (lines.isEmpty()) return emptyList()
    val byTitle = lines.groupBy(::tasteKeyOf)
    val keys = byTitle.keys
    val movieKeys = keys.filter { it.startsWith("movie:") }
    val showKeys = keys.filter { it.startsWith("tv:") }
    val out = ArrayList<NuvioCTasteInsight>()
    val genreRank = keys.flatMap { titles[it]?.genres?.distinct().orEmpty() }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }

    // 1. film / series balance by watch time
    if (movieKeys.isNotEmpty() && showKeys.isNotEmpty() && keys.size >= 10) {
        val filmMin = movieKeys.sumOf { titles[it]?.runtime ?: 110 }
        val showMin = showKeys.sumOf { k -> byTitle.getValue(k).count { it.season != null } * (titles[k]?.runtime ?: 40) }
        val total = (filmMin + showMin).coerceAtLeast(1)
        val f = pct(filmMin, total)
        out += NuvioCTasteInsight(
            "balance", "Films and series",
            when { f >= 60 -> "Mostly films by watch time"; f <= 40 -> "Mostly series by watch time"; else -> "An even split by watch time" },
            visual = NuvioCTasteVisual.Split(listOf(f / 100f), "Films $f%", "Series ${100 - f}%"),
            keys = keys.toList()
        )
    }

    // 2. you vs the crowd
    val rated = keys.mapNotNull { k -> tasteScoreFor(k, titles, scores)?.let { s -> titles[k]?.vote?.let { v -> Triple(k, s.toDouble(), v) } } }
    if (rated.size >= 10) {
        val you = rated.map { it.second }.average()
        val crowd = rated.map { it.third }.average()
        val diff = you - crowd
        val perGenre = genreRank.take(8).mapNotNull { g ->
            val d = rated.filter { g in titles[it.first]?.genres.orEmpty() }.map { it.second - it.third }
            if (d.size >= 5) g to d.average() else null
        }
        val harsh = perGenre.minByOrNull { it.second }?.takeIf { it.second < diff - 0.3 }
        val kind = perGenre.maxByOrNull { it.second }?.takeIf { it.second > diff + 0.3 && it != harsh }
        out += NuvioCTasteInsight(
            "crowd", "You and the crowd",
            when {
                diff <= -0.3 -> "${one(abs(diff))} below TMDB on average"
                diff >= 0.3 -> "${one(diff)} above TMDB on average"
                else -> "In step with TMDB"
            },
            visual = NuvioCTasteVisual.Versus(you, crowd, harsh?.let { "Harshest on ${pluralGenre(it.first)}" }, kind?.let { "Kindest on ${pluralGenre(it.first)}" }),
            keys = rated.map { it.first }
        )
    }

    // 3. eras
    val decadeCounts = keys.mapNotNull { titles[it]?.year }.filter { it >= 1950 }.groupingBy { it - it % 10 }.eachCount()
    if (decadeCounts.size >= 2 && keys.size >= 10) {
        val ordered = decadeCounts.entries.sortedByDescending { it.value }
        val first = ordered[0].key
        val second = ordered.getOrNull(1)?.key
        val span = (decadeCounts.keys.min()..decadeCounts.keys.max() step 10).toList()
        out += NuvioCTasteInsight(
            "eras", "Your eras",
            if (second != null && ordered[1].value * 3 >= ordered[0].value) "The ${tasteDecadeLabel(first)}, then the ${tasteDecadeLabel(second)}"
            else "Mostly the ${tasteDecadeLabel(first)}",
            visual = NuvioCTasteVisual.Bars(span.map { decadeCounts[it] ?: 0 }, span.indexOf(first), tasteDecadeLabel(span.first()), tasteDecadeLabel(span.last())),
            action = NuvioCTasteAction.Spin(genreRank.firstOrNull(), first),
            keys = keys.filter { k -> titles[k]?.year?.let { it - it % 10 == first } == true }
        )
    }

    // 4. binge depth
    val episodeLines = lines.filter { it.season != null }
    if (episodeLines.size >= 20) {
        val sittings = episodeLines.groupBy { tasteKeyOf(it) to Instant.ofEpochMilli(it.watchedAt).atZone(zone).toLocalDate() }
        val avg = sittings.values.map { it.size }.average()
        val longest = sittings.maxByOrNull { it.value.size }
        val name = longest?.let { titles[it.key.first]?.name }?.takeIf { it.isNotBlank() }
        out += NuvioCTasteInsight(
            "binge", "Binge depth", "${one(avg)} episodes a sitting",
            line = if (longest != null && name != null && longest.value.size >= 3) "Longest: ${longest.value.size} episodes of $name in a day" else null,
            action = longest?.let { l -> titles[l.key.first]?.let { NuvioCTasteAction.Title(it.imdbId ?: "tmdb:${it.tmdbId}", "series") } },
            weekend = true,
            keys = episodeLines.map(::tasteKeyOf).distinct()
        )
    }

    // 5. countries
    val countryTitles = keys.mapNotNull { titles[it]?.countries?.firstOrNull() }
    if (countryTitles.size >= 10) {
        val counts = countryTitles.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
        out += NuvioCTasteInsight(
            "countries", "Where it's from",
            counts.joinToString(" · ") { "${tasteCountryName(it.key)} ${pct(it.value, countryTitles.size)}%" },
            visual = NuvioCTasteVisual.Split(counts.map { it.value.toFloat() / countryTitles.size }),
            keys = counts.map { it.key }.toSet().let { top -> keys.filter { titles[it]?.countries?.firstOrNull() in top } }
        )
    }

    // 6. runtime sweet spot (films)
    val runtimes = movieKeys.mapNotNull { titles[it]?.runtime }.filter { it in 40..300 }
    if (runtimes.size >= 10) {
        val edges = listOf(0, 90, 110, 130, 150, 999)
        val buckets = (0 until 5).map { i -> runtimes.count { it >= edges[i] && it < edges[i + 1] } }
        val top = buckets.indices.maxBy { buckets[it] }
        val value = when (top) {
            0 -> "Films under 1h30"
            4 -> "Films over 2h30"
            else -> "Films of ${minutesLabel(edges[top])} to ${minutesLabel(edges[top + 1])}"
        }
        out += NuvioCTasteInsight("runtime", "Runtime sweet spot", value,
            visual = NuvioCTasteVisual.Bars(buckets, top, "Short", "Long"),
            keys = movieKeys.filter { k -> titles[k]?.runtime?.let { it in 40..300 && it >= edges[top] && it < edges[top + 1] } == true })
    }

    // 7. busiest day
    val stamps = lines.map { Instant.ofEpochMilli(it.watchedAt).atZone(zone) }
    if (stamps.size >= 20) {
        val perDay = DayOfWeek.entries.map { d -> stamps.count { it.dayOfWeek == d } }
        val top = perDay.indices.maxBy { perDay[it] }
        val day = DayOfWeek.entries[top].getDisplayName(TextStyle.FULL, Locale.US)
        out += NuvioCTasteInsight("day", "Busiest day", "$day, ${pct(perDay[top], stamps.size)}% of your viewing",
            visual = NuvioCTasteVisual.Bars(perDay, top, "Mon", "Sun"), weekend = true,
            keys = lines.filter { Instant.ofEpochMilli(it.watchedAt).atZone(zone).dayOfWeek == DayOfWeek.entries[top] }.map(::tasteKeyOf).distinct())

        // 8. late nights
        val late = stamps.count { it.hour >= 22 || it.hour < 4 }
        val share = pct(late, stamps.size)
        if (share >= 15) {
            val lateGenre = lines.filter { Instant.ofEpochMilli(it.watchedAt).atZone(zone).let { z -> z.hour >= 22 || z.hour < 4 } }
                .flatMap { titles[tasteKeyOf(it)]?.genres.orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            out += NuvioCTasteInsight("late", "After dark", "$share% of your viewing starts after 10 pm",
                line = lateGenre?.let { "Mostly ${pluralGenre(it)}" },
                action = lateGenre?.let { NuvioCTasteAction.Spin(it, null) },
                hours = setOf(21, 22, 23, 0, 1, 2, 3),
                keys = lines.filter { Instant.ofEpochMilli(it.watchedAt).atZone(zone).let { z -> z.hour >= 22 || z.hour < 4 } }.map(::tasteKeyOf).distinct())
        }
    }

    // 9. rewatches (films seen on more than one day)
    val rewatched = movieKeys.mapNotNull { k ->
        val days = byTitle.getValue(k).map { Instant.ofEpochMilli(it.watchedAt).atZone(zone).toLocalDate() }.toSet().size
        if (days >= 2) k to days else null
    }
    if (rewatched.size >= 3) {
        val most = rewatched.maxBy { it.second }
        val t = titles[most.first]
        out += NuvioCTasteInsight("rewatch", "Rewatches", "${rewatched.size} films seen more than once",
            line = t?.name?.takeIf { it.isNotBlank() }?.let { "Most often: $it, ${most.second} times" },
            action = t?.let { NuvioCTasteAction.Title(it.imdbId ?: "tmdb:${it.tmdbId}", "movie") },
            keys = rewatched.sortedByDescending { it.second }.map { it.first })
    }

    // 10. mainstream score
    val votes = keys.mapNotNull { k -> titles[k]?.let { it.votes to it.isShow } }.filter { it.first > 0 }
    if (votes.size >= 10) {
        val wide = votes.count { (v, show) -> v >= if (show) 1_000 else 5_000 }
        val median = votes.map { it.first }.sorted()[votes.size / 2]
        val share = pct(wide, votes.size)
        out += NuvioCTasteInsight("mainstream", "Mainstream score", "$share% widely known titles",
            line = "A typical pick has ${NF.format(median)} TMDB ratings",
            visual = NuvioCTasteVisual.Split(listOf(share / 100f), "Widely known", "Lesser known"),
            keys = keys.filter { k -> titles[k]?.let { t -> t.votes >= if (t.isShow) 1_000 else 5_000 } == true })
    }

    // 11. on the rise (last 90 days against the year before)
    if (allTime) {
        val cut = nowMs - 90L * DAY
        val before = history.filter { usable(it) && it.watchedAt in (cut - 365L * DAY) until cut }.map(::tasteKeyOf).toSet()
        val recent = history.filter { usable(it) && it.watchedAt in cut..nowMs }.map(::tasteKeyOf).toSet()
        if (recent.size >= 10 && before.size >= 20) {
            fun share(set: Set<String>): Map<String, Int> {
                val c = set.flatMap { titles[it]?.genres?.distinct().orEmpty() }.groupingBy { it }.eachCount()
                return c.mapValues { pct(it.value, set.size) }
            }
            val now = share(recent)
            val then = share(before)
            val rise = now.map { (g, p) -> g to (p - (then[g] ?: 0)) }.maxByOrNull { it.second }
            if (rise != null && rise.second >= 5) {
                val was = then[rise.first] ?: 0
                val isNow = now.getValue(rise.first)
                out += NuvioCTasteInsight("rise", "On the rise", "${rise.first} is up ${rise.second} points lately",
                    line = "$was% to $isNow% of your titles",
                    visual = NuvioCTasteVisual.Split(listOf(isNow / 100f)),
                    action = NuvioCTasteAction.Spin(rise.first, null),
                    keys = recent.filter { rise.first in titles[it]?.genres.orEmpty() })
            }
        }
    }

    // 12. this month so far
    if (allTime) {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val month = history.filter { usable(it) && it.watchedAt in start..nowMs }.map(::tasteKeyOf).toSet()
        if (month.size >= 3) {
            val g = month.flatMap { titles[it]?.genres?.distinct().orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            out += NuvioCTasteInsight("month", today.month.getDisplayName(TextStyle.FULL, Locale.US) + " so far",
                "${month.size} titles so far", line = g?.let { "Mostly ${pluralGenre(it)}" },
                action = g?.let { NuvioCTasteAction.Spin(it, null) },
                keys = month.toList())
        }
    }

    // 13. languages
    val langs = keys.mapNotNull { titles[it]?.lang }
    if (langs.size >= 10) {
        val other = langs.filter { it != "en" }
        val share = pct(other.size, langs.size)
        if (share >= 5) {
            val top = other.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2)
                .map { Locale(it.key).getDisplayLanguage(Locale.US).ifBlank { it.key } }
            out += NuvioCTasteInsight("language", "Languages", "$share% not in English",
                line = when (top.size) { 0 -> null; 1 -> "${top[0]} leads"; else -> "${top[0]} leads, then ${top[1]}" },
                visual = NuvioCTasteVisual.Split(listOf(share / 100f), "Other languages", "English"),
                keys = keys.filter { titles[it]?.lang?.let { l -> l != "en" } == true })
        }
    }

    // 14. studio / network
    val studio = keys.flatMap { titles[it]?.studios.orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }
    if (studio != null && studio.value >= 3) {
        out += NuvioCTasteInsight("studio", "Studio or network", "${studio.value} titles from ${studio.key}", line = "More than any other",
            keys = keys.filter { studio.key in titles[it]?.studios.orEmpty() })
    }

    // 15-17. people
    val castCount = HashMap<Int, Pair<NuvioCTastePerson, Int>>()
    val dirCount = HashMap<Int, Pair<NuvioCTastePerson, Int>>()
    keys.forEach { k ->
        titles[k]?.cast?.forEach { p -> castCount[p.id] = p to ((castCount[p.id]?.second ?: 0) + 1) }
        titles[k]?.directors?.forEach { p -> dirCount[p.id] = p to ((dirCount[p.id]?.second ?: 0) + 1) }
    }
    dirCount.values.maxByOrNull { it.second }?.takeIf { it.second >= 3 }?.let { (p, n) ->
        out += NuvioCTasteInsight("director", "Director loyalty", "$n ${p.name} titles", line = "More than any other director",
            action = NuvioCTasteAction.Person(p.id, p.name, true),
            keys = keys.filter { k -> titles[k]?.directors.orEmpty().any { it.id == p.id } })
    }
    castCount.values.maxByOrNull { it.second }?.takeIf { it.second >= 3 }?.let { (p, n) ->
        out += NuvioCTasteInsight("actor", "Most watched", p.name, line = "In $n of your titles",
            action = NuvioCTasteAction.Person(p.id, p.name, false),
            keys = keys.filter { k -> titles[k]?.cast.orEmpty().any { it.id == p.id } })
    }
    if (allTime) {
        val cut = nowMs - 60L * DAY
        val recentKeys = history.filter { usable(it) && it.watchedAt >= cut }.map(::tasteKeyOf).toSet()
        val olderKeys = history.filter { usable(it) && it.watchedAt < cut }.map(::tasteKeyOf).toSet() - recentKeys
        val seenBefore = olderKeys.flatMap { titles[it]?.cast.orEmpty().map { p -> p.id } }.toSet()
        val fresh = recentKeys.flatMap { titles[it]?.cast.orEmpty() }.groupBy { it.id }
            .filter { it.key !in seenBefore && it.value.size >= 2 }.maxByOrNull { it.value.size }
        fresh?.let { (_, list) ->
            val p = list.first()
            out += NuvioCTasteInsight("discovery", "New to you", p.name, line = "${list.size} titles in the last two months",
                action = NuvioCTasteAction.Person(p.id, p.name, false),
                keys = recentKeys.filter { k -> titles[k]?.cast.orEmpty().any { it.id == p.id } })
        }
    }

    // 18. franchise progress
    val seenParts = movieKeys.mapNotNull { titles[it] }.filter { it.collectionId != null }.groupBy { it.collectionId!! }
    seenParts.entries.filter { it.value.size >= 2 }.sortedByDescending { it.value.size }.firstNotNullOfOrNull { (id, seen) ->
        val c = collections[id] ?: return@firstNotNullOfOrNull null
        if (c.parts.size < 2) return@firstNotNullOfOrNull null
        val seenIds = seen.map { it.tmdbId }.toSet()
        val done = c.parts.count { it.first in seenIds }.coerceAtLeast(seen.size).coerceAtMost(c.parts.size)
        val next = c.parts.firstOrNull { it.first !in seenIds }
        val name = c.name.removeSuffix(" Collection").trim()
        NuvioCTasteInsight("franchise", "Franchise progress",
            if (done >= c.parts.size) "All ${c.parts.size} $name films" else "$done of ${c.parts.size} $name films",
            line = next?.let { "Next: ${it.second}" },
            visual = NuvioCTasteVisual.Steps(done, c.parts.size),
            action = next?.let { NuvioCTasteAction.Title("tmdb:${it.first}", "movie") },
            keys = seen.map { it.key })
    }?.let { out += it }

    // 19. longest streak
    val daySet = lines.map { Instant.ofEpochMilli(it.watchedAt).atZone(zone).toLocalDate() }.toSortedSet()
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    var streakEnd: LocalDate? = null
    for (d in daySet) {
        run = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) run + 1 else 1
        if (run > longest) { longest = run; streakEnd = d }
        prev = d
    }
    if (longest >= 3) {
        val end = streakEnd!!
        val start = end.minusDays(longest - 1L)
        out += NuvioCTasteInsight("streak", "Longest streak", "$longest days in a row", line = "Something watched every day",
            keys = lines.filter { Instant.ofEpochMilli(it.watchedAt).atZone(zone).toLocalDate().let { d -> !d.isBefore(start) && !d.isAfter(end) } }
                .map(::tasteKeyOf).distinct())
    }

    // 20. oldest film
    movieKeys.mapNotNull { titles[it] }.filter { it.year != null && it.name.isNotBlank() }.minByOrNull { it.year!! }
        ?.takeIf { it.year!! < 1980 && movieKeys.size >= 10 }?.let { t ->
            out += NuvioCTasteInsight("oldest", "Oldest film", "${t.name} (${t.year})",
                action = NuvioCTasteAction.Title(t.imdbId ?: "tmdb:${t.tmdbId}", "movie"),
                keys = movieKeys.filter { k -> titles[k]?.year?.let { it < 1980 } == true })
        }

    // 21. how you rate
    val windowScores = keys.mapNotNull { tasteScoreFor(it, titles, scores) }.filter { it in 1..10 }
    val ratingSource = if (allTime && scores.size >= windowScores.size) scores.values.filter { it in 1..10 } else windowScores
    if (ratingSource.size >= 25) {
        val bins = (1..10).map { s -> ratingSource.count { it == s } }
        val top = bins.indices.maxBy { bins[it] }
        val stars = (top + 1) / 2.0
        out += NuvioCTasteInsight("ratings", "How you rate",
            "Most often ${if (stars % 1.0 == 0.0) stars.toInt().toString() else one(stars)} stars",
            line = "${NF.format(ratingSource.size)} ratings",
            visual = NuvioCTasteVisual.Bars(bins, top, "½", "5"),
            keys = keys.filter { tasteScoreFor(it, titles, scores) == top + 1 })
    }

    // 22. milestone title
    if (allTime) {
        val firsts = history.filter(::usable).groupBy(::tasteKeyOf).mapValues { e -> e.value.minOf { it.watchedAt } }
            .entries.sortedBy { it.value }
        val step = if (firsts.size >= 1000) 500 else 100
        val n = firsts.size / step * step
        if (n >= 100) {
            val (k, at) = firsts[n - 1]
            val t = titles[k]
            if (t != null && t.name.isNotBlank()) {
                val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                out += NuvioCTasteInsight("milestone", "Milestone", "Title ${NF.format(n)}: ${t.name}",
                    line = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${date.year}",
                    action = NuvioCTasteAction.Title(t.imdbId ?: "tmdb:${t.tmdbId}", if (t.isShow) "series" else "movie"),
                    keys = firsts.subList((n - 10).coerceAtLeast(0), (n + 10).coerceAtMost(firsts.size)).map { it.key })
            }
        }
    }
    return out
}

/**
 * Six cards for this visit, drawn at random with [seed]; cards that suit the hour (or a weekend) count three times.
 * The same seed always gives the same six, so the page doesn't reshuffle while it's open.
 */
internal fun pickTasteSpotlight(all: List<NuvioCTasteInsight>, seed: Long, hour: Int, weekend: Boolean, count: Int = 6): List<NuvioCTasteInsight> {
    if (all.size <= count) return all
    val random = java.util.Random(seed)
    val pool = all.toMutableList()
    val picked = ArrayList<NuvioCTasteInsight>()
    while (picked.size < count && pool.isNotEmpty()) {
        val weights = pool.map { if ((it.hours?.contains(hour) == true) || (it.weekend && weekend)) 3 else 1 }
        var r = random.nextInt(weights.sum())
        val i = weights.indices.first { r -= weights[it]; r < 0 }
        picked += pool.removeAt(i)
    }
    return picked
}

/** Past months with at least [minTitles] titles, newest first (the current month is left out until it ends). */
internal fun computeTasteRecaps(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    nowMs: Long,
    zone: ZoneId,
    minTitles: Int = 10,
    max: Int = 18
): List<NuvioCTasteRecap> {
    val thisMonth = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
    return history.filter(::usable)
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.watchedAt).atZone(zone)) }
        .filterKeys { it.isBefore(thisMonth) }
        .mapNotNull { (month, lines) ->
            val byTitle = lines.groupBy(::tasteKeyOf)
            if (byTitle.size < minTitles) return@mapNotNull null
            val minutes = byTitle.entries.sumOf { (k, v) ->
                if (k.startsWith("movie:")) titles[k]?.runtime ?: 110 else v.count { it.season != null } * (titles[k]?.runtime ?: 40)
            }
            val genre = byTitle.keys.flatMap { titles[it]?.genres?.distinct().orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            NuvioCTasteRecap(month, byTitle.size, (minutes / 60.0).roundToInt(), genre)
        }
        .sortedByDescending { it.month }
        .take(max)
}

/** The epoch-ms range of a calendar month in [zone]. */
internal fun tasteMonthWindow(month: YearMonth, zone: ZoneId): LongRange {
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start..end
}

/** Months that have any history, oldest first (for stepping with Left / Right). */
internal fun tasteHistoryMonths(history: List<WatchedItem>, zone: ZoneId): List<YearMonth> =
    history.filter(::usable).map { YearMonth.from(Instant.ofEpochMilli(it.watchedAt).atZone(zone)) }.toSortedSet().toList()

private const val DAY = 24L * 60 * 60 * 1000
