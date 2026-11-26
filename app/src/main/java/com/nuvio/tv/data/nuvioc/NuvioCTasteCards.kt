package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 65 TASTE_CARDS_OPEN: what each Taste card opens. Three page types, all built from data already
// on the TV (the profile's history, the cached title facts and the profile's own ratings; only posters load):
//  - BREAKDOWN (T1): groups on the left (genres, decades, finished / ongoing...), the group's titles on the right;
//  - TIMELINE (T2): bars per day (a month), per month (a year) or per year (all time), or per hour of the day; the
//    focused bar's numbers and titles below;
//  - RANKED (T3): one ranked list with value columns (runtime, release gap, you vs TMDB).
// Strictly per profile: the repository only builds a page for the active profile's inputs. Lists newest first,
// capped at 200.

import com.nuvio.tv.domain.model.WatchedItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class NuvioCTastePageKind { BREAKDOWN, TIMELINE, RANKED }

enum class NuvioCTasteCard(val title: String, val kind: NuvioCTastePageKind) {
    WATCH_TIME("Watch time", NuvioCTastePageKind.TIMELINE),
    TITLES("Titles", NuvioCTastePageKind.BREAKDOWN),
    EPISODES("Episodes", NuvioCTastePageKind.BREAKDOWN),
    ACTIVE_DAYS("Active days", NuvioCTastePageKind.TIMELINE),
    BIGGEST_DAY("Biggest day", NuvioCTastePageKind.TIMELINE),
    SHOWS("Shows", NuvioCTastePageKind.BREAKDOWN),
    NEW_RELEASES("New releases", NuvioCTastePageKind.RANKED),
    VIEWING_HOURS("Viewing hours", NuvioCTastePageKind.TIMELINE),
    COMPLETION("Completion", NuvioCTastePageKind.BREAKDOWN),
    RATINGS("Ratings", NuvioCTastePageKind.BREAKDOWN),
    GENRES("Genres", NuvioCTastePageKind.BREAKDOWN),
    DECADES("Decades", NuvioCTastePageKind.BREAKDOWN),
    STUDIOS("Studios & networks", NuvioCTastePageKind.BREAKDOWN),
    LANGUAGES("Languages", NuvioCTastePageKind.BREAKDOWN),
    COUNTRIES("Countries", NuvioCTastePageKind.BREAKDOWN),
    AVG_FILM("Avg film", NuvioCTastePageKind.RANKED),
    AVG_EPISODE("Avg episode", NuvioCTastePageKind.RANKED),
    VS_TMDB("You vs TMDB", NuvioCTastePageKind.RANKED),
    YOUR_YEAR("Your year", NuvioCTastePageKind.BREAKDOWN),
    HOURS_BY_MONTH("Hours by month", NuvioCTastePageKind.TIMELINE),
    NEW_SHOWS("New shows", NuvioCTastePageKind.BREAKDOWN),
    NEW_DIRECTORS("New directors", NuvioCTastePageKind.BREAKDOWN)
}

/** One title row: [name] then [sub] (details line), optional right-hand [value]; [you] / [crowd] for vs TMDB. */
data class NuvioCTasteItem(
    val name: String,
    val sub: String,
    val poster: String?,
    val id: String?,
    val type: String?,
    val value: String? = null,
    val you: Int? = null,
    val crowd: Double? = null
)

data class NuvioCTasteGroup(val label: String, val value: String, val items: List<NuvioCTasteItem>)

data class NuvioCTasteBar(val tick: String, val heading: String, val minutes: Int, val ledger: List<Pair<String, String>>, val items: List<NuvioCTasteItem>)

data class NuvioCTasteCardPage(
    val card: NuvioCTasteCard,
    val title: String,
    val summary: String,
    val leftTitle: String = "",
    val groups: List<NuvioCTasteGroup> = emptyList(),
    val focusGroup: Int = 0,
    val barsTitle: String = "",
    val bars: List<NuvioCTasteBar> = emptyList(),
    val focusBar: Int = 0,
    val columns: List<String> = emptyList(),
    val ranked: List<NuvioCTasteItem> = emptyList()
) {
    val kind: NuvioCTastePageKind get() = card.kind
    val empty: Boolean get() = groups.isEmpty() && bars.isEmpty() && ranked.isEmpty()
}

internal const val TASTE_PAGE_CAP = 200

private fun usable65(w: WatchedItem) = w.contentId.startsWith("tt") || w.contentId.startsWith("tmdb:")
private fun isShow65(w: WatchedItem) = w.season != null || w.contentType.equals("series", true) || w.contentType.equals("tv", true)
private fun mins65(w: WatchedItem, t: NuvioCTasteTitle?) = t?.runtime?.takeIf { it > 0 } ?: if (isShow65(w)) 40 else 110

internal fun tasteHm65(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h hr"
        else -> "$h hr $m min"
    }
}

private fun m3(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.US).take(3)
internal fun tasteDay65(d: LocalDate) = "${d.dayOfMonth} ${m3(d)}"
private fun weekdayDay(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).take(3) + " " + tasteDay65(d)

/** A title's details line ("Film · 2024 · 19 Nov" / "Show · 6 eps · 21 Nov"). */
private class Agg(val key: String) {
    var last = Long.MIN_VALUE
    var first = Long.MAX_VALUE
    var minutes = 0
    val eps = LinkedHashSet<Pair<Int, Int>>()
    var plays = 0
    var sample: WatchedItem? = null
}

private class Ctx(
    val history: List<WatchedItem>,
    val titles: Map<String, NuvioCTasteTitle>,
    val scores: Map<String, Int>,
    val window: LongRange?,
    val zone: ZoneId
) {
    val hist = history.filter { usable65(it) && (window == null || it.watchedAt in window) }
    val aggs: Map<String, Agg> = LinkedHashMap<String, Agg>().also { map ->
        hist.forEach { w ->
            val k = tasteKeyOf(w)
            val a = map.getOrPut(k) { Agg(k) }
            if (w.watchedAt > a.last) { a.last = w.watchedAt; a.sample = w }
            if (w.watchedAt < a.first) a.first = w.watchedAt
            a.minutes += mins65(w, titles[k])
            a.plays++
            if (w.season != null) a.eps += (w.season to (w.episode ?: 0))
        }
    }
    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun name(k: String) = titles[k]?.name?.takeIf { it.isNotBlank() } ?: aggs[k]?.sample?.title ?: k
    fun isShow(k: String) = k.startsWith("tv:")
    fun idType(k: String): Pair<String?, String> {
        val t = titles[k]
        val raw = k.substringAfter(':')
        val id = t?.imdbId ?: t?.tmdbId?.let { "tmdb:$it" } ?: raw
        return id to (if (isShow(k)) "series" else "movie")
    }
    fun poster(k: String) = titles[k]?.poster?.replace("/w342/", "/w154/") ?: aggs[k]?.sample?.poster
    fun sub(k: String): String {
        val a = aggs[k]
        val year = titles[k]?.year
        val day = a?.last?.takeIf { it != Long.MIN_VALUE }?.let { tasteDay65(date(it)) }
        return if (isShow(k)) listOfNotNull("Show", a?.eps?.size?.takeIf { it > 0 }?.let { "$it eps" }, day).joinToString(" · ")
        else listOfNotNull("Film", year?.toString(), day).joinToString(" · ")
    }
    fun item(k: String, value: String? = null, sub: String = sub(k)): NuvioCTasteItem {
        val (id, type) = idType(k)
        return NuvioCTasteItem(name(k), sub, poster(k), id, type, value)
    }
    /** Keys newest first, capped. */
    fun newest(keys: Collection<String>) = keys.sortedByDescending { aggs[it]?.last ?: Long.MIN_VALUE }.take(TASTE_PAGE_CAP)
    fun score(k: String) = tasteScoreFor(k, titles, scores)?.takeIf { it in 1..10 }
    val totalMinutes get() = aggs.values.sumOf { it.minutes }
}

private fun groupBy(c: Ctx, label: (String) -> List<String>, top: Int, order: Comparator<Pair<String, List<String>>>? = null): List<NuvioCTasteGroup> {
    val map = LinkedHashMap<String, MutableList<String>>()
    c.aggs.keys.forEach { k -> label(k).forEach { l -> map.getOrPut(l) { ArrayList() }.add(k) } }
    val entries = map.entries.map { it.key to it.value.toList() }
    val sorted = if (order != null) entries.sortedWith(order) else entries.sortedByDescending { it.second.size }
    return sorted.take(top).map { (l, ks) ->
        val minutes = ks.sumOf { c.aggs[it]?.minutes ?: 0 }
        NuvioCTasteGroup(l, "${ks.size} · " + (if (minutes >= 60) "${minutes / 60} hr" else tasteHm65(minutes)), c.newest(ks).map { c.item(it) })
    }
}

/**
 * Pure: the page [card] opens over [history] in [window] (null = all time). [hint] picks the group / bar to open on
 * (a genre name, a date "yyyy-MM-dd", a score). Built for one profile's inputs only.
 */
internal fun computeTasteCardPage(
    card: NuvioCTasteCard,
    hint: String?,
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    scores: Map<String, Int>,
    window: LongRange?,
    zone: ZoneId,
    now: Long = System.currentTimeMillis()
): NuvioCTasteCardPage {
    val c = Ctx(history, titles, scores, window, zone)
    val k = card
    fun page(summary: String, leftTitle: String, groups: List<NuvioCTasteGroup>) = NuvioCTasteCardPage(
        k, k.title, summary, leftTitle, groups, focusGroup = groups.indexOfFirst { it.label == hint }.coerceAtLeast(0)
    )
    val titleCount = c.aggs.size
    return when (k) {
        NuvioCTasteCard.TITLES, NuvioCTasteCard.YOUR_YEAR -> {
            val films = c.aggs.keys.filter { !c.isShow(it) }
            val shows = c.aggs.keys.filter { c.isShow(it) }
            val eps = c.aggs.values.sumOf { it.eps.size }
            page("$titleCount titles · ${tasteHm65(c.totalMinutes)}", "TYPE", listOfNotNull(
                films.takeIf { it.isNotEmpty() }?.let { NuvioCTasteGroup("Films", "${it.size}", c.newest(it).map(c::item)) },
                shows.takeIf { it.isNotEmpty() }?.let { NuvioCTasteGroup("Shows", "${it.size} · $eps eps", c.newest(it).map(c::item)) }
            ))
        }
        NuvioCTasteCard.EPISODES -> {
            val shows = c.aggs.values.filter { it.eps.isNotEmpty() }.sortedByDescending { it.eps.size }.take(TASTE_PAGE_CAP)
            val groups = shows.map { a ->
                val watches = c.hist.filter { tasteKeyOf(it) == a.key && it.season != null }.sortedByDescending { it.watchedAt }
                    .distinctBy { it.season to it.episode }.take(TASTE_PAGE_CAP)
                NuvioCTasteGroup(c.name(a.key), "${a.eps.size} eps", watches.map { w ->
                    c.item(a.key, sub = "S${w.season} E${w.episode ?: 0} · ${weekdayDay(c.date(w.watchedAt))}").copy(name = c.name(a.key) + " · S${w.season} E${w.episode ?: 0}")
                })
            }
            page("${shows.sumOf { it.eps.size }} episodes · ${shows.size} shows", "SHOWS", groups)
        }
        NuvioCTasteCard.SHOWS, NuvioCTasteCard.COMPLETION -> {
            // progress over all history, shows taken from the window
            val allEps = HashMap<String, MutableSet<Pair<Int, Int>>>()
            history.filter { usable65(it) && it.season != null }.forEach { allEps.getOrPut(tasteKeyOf(it)) { HashSet() }.add(it.season!! to (it.episode ?: 0)) }
            val showKeys = c.aggs.keys.filter { c.isShow(it) }
            fun seen(s: String) = allEps[s]?.size ?: 0
            fun total(s: String) = titles[s]?.episodes?.takeIf { it > 0 }
            fun state(s: String): String {
                val t = titles[s]
                val tot = total(s) ?: return "Ongoing"
                return when {
                    seen(s) >= tot && t?.ended == true -> "Finished"
                    seen(s) >= tot -> "Caught up"
                    else -> "Ongoing"
                }
            }
            fun pct(s: String) = total(s)?.let { (seen(s) * 100 / it).coerceAtMost(100) }
            fun showItem(s: String) = c.item(s, value = if (k == NuvioCTasteCard.COMPLETION) pct(s)?.let { "$it%" } else null,
                sub = listOfNotNull("${seen(s)}${total(s)?.let { " / $it" } ?: ""} eps", c.aggs[s]?.last?.let { tasteDay65(c.date(it)) }).joinToString(" · "))
            val groups = listOf("Finished", "Ongoing", "Caught up").mapNotNull { g ->
                val ks = showKeys.filter { state(it) == g }
                if (ks.isEmpty()) null else NuvioCTasteGroup(g, "${ks.size}",
                    (if (k == NuvioCTasteCard.COMPLETION) ks.sortedByDescending { pct(it) ?: -1 }.take(TASTE_PAGE_CAP) else c.newest(ks)).map(::showItem))
            }
            page("${showKeys.size} shows", "SHOWS", groups)
        }
        NuvioCTasteCard.RATINGS -> {
            val rated = c.aggs.keys.mapNotNull { key -> c.score(key)?.let { key to it } }
            val groups = (10 downTo 1).mapNotNull { s ->
                val ks = rated.filter { it.second == s }.map { it.first }
                if (ks.isEmpty()) null else NuvioCTasteGroup("$s / 10", "${ks.size}", c.newest(ks).map { c.item(it, value = "$s") })
            }
            page("${rated.size} rated" + (rated.takeIf { it.isNotEmpty() }?.let { " · average %.1f".format(Locale.US, it.map { p -> p.second }.average()) } ?: ""), "SCORES", groups)
        }
        NuvioCTasteCard.GENRES -> page("$titleCount titles · ${tasteHm65(c.totalMinutes)}", "GENRES", groupBy(c, { titles[it]?.genres.orEmpty() }, 12))
        NuvioCTasteCard.DECADES -> page("$titleCount titles", "DECADES",
            groupBy(c, { key -> listOfNotNull(titles[key]?.year?.let { "${it / 10 * 10}s" }) }, 20, compareByDescending { it.first }))
        NuvioCTasteCard.STUDIOS -> page("$titleCount titles", "STUDIOS & NETWORKS", groupBy(c, { titles[it]?.studios.orEmpty() }, 20))
        NuvioCTasteCard.LANGUAGES -> page("$titleCount titles", "LANGUAGES", groupBy(c, { key ->
            listOfNotNull(titles[key]?.lang?.let { l -> if (l.length <= 3) Locale.forLanguageTag(l).getDisplayLanguage(Locale.US).ifBlank { l } else l })
        }, 20))
        NuvioCTasteCard.COUNTRIES -> page("$titleCount titles", "COUNTRIES", groupBy(c, { key ->
            titles[key]?.countries.orEmpty().map { cc -> if (cc.length == 2) Locale("", cc).getDisplayCountry(Locale.US).ifBlank { cc } else cc }
        }, 20))
        NuvioCTasteCard.NEW_SHOWS -> {
            val firstEver = HashMap<String, Long>()
            history.filter(::usable65).forEach { w -> val key = tasteKeyOf(w); if (w.watchedAt < (firstEver[key] ?: Long.MAX_VALUE)) firstEver[key] = w.watchedAt }
            val ks = c.aggs.keys.filter { c.isShow(it) && (window == null || (firstEver[it] ?: Long.MAX_VALUE) in window) }
            page("${ks.size} new shows", "NEW SHOWS", if (ks.isEmpty()) emptyList() else listOf(NuvioCTasteGroup("New shows", "${ks.size}", c.newest(ks).map(c::item))))
        }
        NuvioCTasteCard.NEW_DIRECTORS -> {
            val firstFilm = HashMap<Int, Long>()
            history.filter { usable65(it) && !isShow65(it) }.forEach { w ->
                titles[tasteKeyOf(w)]?.directors.orEmpty().forEach { d -> if (w.watchedAt < (firstFilm[d.id] ?: Long.MAX_VALUE)) firstFilm[d.id] = w.watchedAt }
            }
            val byDirector = LinkedHashMap<NuvioCTastePerson, MutableList<String>>()
            c.aggs.keys.filter { !c.isShow(it) }.forEach { key ->
                titles[key]?.directors.orEmpty().filter { d -> window == null || (firstFilm[d.id] ?: Long.MAX_VALUE) in window }
                    .forEach { d -> byDirector.getOrPut(d) { ArrayList() }.add(key) }
            }
            val groups = byDirector.entries.sortedByDescending { it.value.size }.take(TASTE_PAGE_CAP)
                .map { (d, ks) -> NuvioCTasteGroup(d.name, "${ks.size}", c.newest(ks).map(c::item)) }
            page("${groups.size} new directors", "DIRECTORS", groups)
        }
        NuvioCTasteCard.WATCH_TIME, NuvioCTasteCard.ACTIVE_DAYS, NuvioCTasteCard.BIGGEST_DAY, NuvioCTasteCard.HOURS_BY_MONTH ->
            timeline(c, k, hint, window, zone, now)
        NuvioCTasteCard.VIEWING_HOURS -> {
            val byHour = (0 until 24).map { h -> c.hist.filter { Instant.ofEpochMilli(it.watchedAt).atZone(zone).hour == h } }
            val v66 = com.nuvio.tv.NuvioCFeatures.TASTE_V66 // [fork] round 66: 12-hour clock, a tick every 3 h
            val bars = byHour.mapIndexed { h, ws -> if (v66) bar(c, nuvioCHourTick(h), nuvioCHour12(h) + " – " + nuvioCHour12((h + 1) % 24), ws) else bar(c, "%02d".format(h), "%02d:00 – %02d:59".format(h, h), ws) }
            val peak = bars.indices.maxByOrNull { bars[it].minutes } ?: 0
            NuvioCTasteCardPage(k, k.title, if (v66) "Peak ${nuvioCHour12(peak)} · uses finish time" else "Peak %02d:00 · uses finish time".format(peak), barsTitle = "BY HOUR OF THE DAY", bars = bars,
                focusBar = hint?.toIntOrNull()?.coerceIn(0, 23) ?: peak)
        }
        NuvioCTasteCard.NEW_RELEASES -> {
            val rows = c.aggs.values.mapNotNull { a ->
                val y = titles[a.key]?.year ?: return@mapNotNull null
                val gap = (c.date(a.first).year - y).coerceAtLeast(0)
                Triple(a.key, gap, y)
            }.sortedWith(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { c.aggs[it.first]?.first ?: 0 }).take(TASTE_PAGE_CAP)
            val fresh = rows.count { it.second <= 1 }
            NuvioCTasteCardPage(k, k.title, "${rows.size} titles · ${if (rows.isEmpty()) 0 else fresh * 100 / rows.size}% within a year of release",
                columns = listOf("TITLE", "RELEASED", "WATCHED"),
                ranked = rows.map { (key, gap, y) ->
                    c.item(key, value = if (gap == 0) "Same year" else "$gap yr later").copy(sub = "${if (c.isShow(key)) "Show" else "Film"} · $y")
                })
        }
        NuvioCTasteCard.AVG_FILM -> {
            val films = c.aggs.keys.filter { !c.isShow(it) && (titles[it]?.runtime ?: 0) > 0 }.sortedByDescending { titles[it]!!.runtime }.take(TASTE_PAGE_CAP)
            val avg = films.takeIf { it.isNotEmpty() }?.map { titles[it]!!.runtime!! }?.average()?.toInt()
            NuvioCTasteCardPage(k, k.title, "${films.size} films" + (avg?.let { " · average ${tasteHm65(it)}" } ?: ""), columns = listOf("FILM", "RUNTIME"),
                ranked = films.map { c.item(it, value = tasteHm65(titles[it]!!.runtime!!)) })
        }
        NuvioCTasteCard.AVG_EPISODE -> {
            val shows = c.aggs.keys.filter { c.isShow(it) && (titles[it]?.runtime ?: 0) > 0 }.sortedByDescending { titles[it]!!.runtime }.take(TASTE_PAGE_CAP)
            val avg = shows.takeIf { it.isNotEmpty() }?.map { titles[it]!!.runtime!! }?.average()?.toInt()
            NuvioCTasteCardPage(k, k.title, "${shows.size} shows" + (avg?.let { " · average ${tasteHm65(it)}" } ?: ""), columns = listOf("SHOW", "EPISODE"),
                ranked = shows.map { c.item(it, value = tasteHm65(titles[it]!!.runtime!!)) })
        }
        NuvioCTasteCard.VS_TMDB -> {
            val rows = c.aggs.keys.mapNotNull { key ->
                val you = c.score(key) ?: return@mapNotNull null
                val crowd = titles[key]?.vote?.takeIf { it > 0 } ?: return@mapNotNull null
                Triple(key, you, crowd)
            }.sortedByDescending { kotlin.math.abs(it.second - it.third) }.take(TASTE_PAGE_CAP)
            val avg = rows.takeIf { it.isNotEmpty() }?.map { it.second - it.third }?.average()
            NuvioCTasteCardPage(k, k.title, "${rows.size} rated" + (avg?.let { " · you rate %+.1f %s on average".format(Locale.US, it, if (it >= 0) "higher" else "lower") } ?: ""),
                columns = listOf("TITLE", "YOU", "TMDB", "GAP"),
                ranked = rows.map { (key, you, crowd) -> c.item(key).copy(you = you, crowd = crowd) })
        }
    }
}

private fun bar(c: Ctx, tick: String, heading: String, ws: List<WatchedItem>): NuvioCTasteBar {
    val byKey = ws.groupBy(::tasteKeyOf)
    val minutes = ws.sumOf { mins65(it, c.titles[tasteKeyOf(it)]) }
    val busiest = ws.groupingBy { Instant.ofEpochMilli(it.watchedAt).atZone(c.zone).hour }.eachCount().maxByOrNull { it.value }?.key
    val items = byKey.entries.sortedByDescending { e -> e.value.maxOf { it.watchedAt } }.take(TASTE_PAGE_CAP).map { (key, lines) ->
        val eps = lines.filter { it.season != null }.sortedWith(compareBy({ it.season }, { it.episode }))
        val m = lines.sumOf { mins65(it, c.titles[key]) }
        val sub = if (eps.isNotEmpty()) {
            val a = eps.first(); val b = eps.last()
            "${eps.size} episode${if (eps.size == 1) "" else "s"} · S${a.season} E${a.episode ?: 0}" +
                if (eps.size > 1) (if (a.season == b.season) "–E${b.episode ?: 0}" else "–S${b.season} E${b.episode ?: 0}") else ""
        } else "Film" + (c.titles[key]?.year?.let { " · $it" } ?: "")
        c.item(key, value = tasteHm65(m), sub = sub)
    }
    return NuvioCTasteBar(tick, heading, minutes, listOf(
        "Watched" to tasteHm65(minutes),
        "Titles" to "${byKey.size}",
        "Episodes" to "${ws.count { it.season != null }}",
        "Busiest hour" to (busiest?.let { if (com.nuvio.tv.NuvioCFeatures.TASTE_V66) nuvioCHour12(it) else "%02d:00".format(it) } ?: "–")
    ), items)
}

private fun timeline(c: Ctx, card: NuvioCTasteCard, hint: String?, window: LongRange?, zone: ZoneId, now: Long): NuvioCTasteCardPage {
    val days = window?.let { (minOf(it.last, now) - it.first) / 86_400_000L } ?: Long.MAX_VALUE
    val bars: List<NuvioCTasteBar>
    val title: String
    val hintDate = hint?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var focus: Int
    if (window != null && days <= 31) {
        val start = c.date(window.first)
        val end = c.date(minOf(window.last, now))
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        val byDate = c.hist.groupBy { c.date(it.watchedAt) }
        bars = dates.map { d -> bar(c, "${d.dayOfMonth}", weekdayDay(d).uppercase(), byDate[d].orEmpty()) }
        title = "BY DAY"
        focus = hintDate?.let { dates.indexOf(it) }?.takeIf { it >= 0 } ?: -1
    } else if (window != null && days <= 366) {
        val start = c.date(window.first)
        val months = (0 until 12).map { start.withDayOfMonth(1).plusMonths(it.toLong()) }
        val byMonth = c.hist.groupBy { c.date(it.watchedAt).withDayOfMonth(1) }
        bars = months.map { m -> bar(c, m3(m).take(1), "${m3(m).uppercase()} ${m.year}", byMonth[m].orEmpty()) }
        title = "BY MONTH"
        focus = hintDate?.let { h -> months.indexOf(h.withDayOfMonth(1)) }?.takeIf { it >= 0 } ?: -1
    } else {
        val byYear = c.hist.groupBy { c.date(it.watchedAt).year }
        val years = byYear.keys.takeIf { it.isNotEmpty() }?.let { (it.min()..it.max()).toList() }.orEmpty()
        bars = years.map { y -> bar(c, "'" + "%02d".format(y % 100), "$y", byYear[y].orEmpty()) }
        title = "BY YEAR"
        focus = hintDate?.let { years.indexOf(it.year) }?.takeIf { it >= 0 } ?: -1
    }
    if (focus < 0) focus = when (card) {
        NuvioCTasteCard.BIGGEST_DAY -> bars.indices.maxByOrNull { bars[it].minutes } ?: 0
        else -> bars.indices.lastOrNull { bars[it].minutes > 0 } ?: 0
    }
    // Active days / best run (days only)
    val active = bars.count { it.minutes > 0 }
    var best = 0; var run = 0
    bars.forEach { if (it.minutes > 0) { run++; best = maxOf(best, run) } else run = 0 }
    val total = bars.sumOf { it.minutes }
    val summary = when (card) {
        NuvioCTasteCard.ACTIVE_DAYS -> "$active active of ${bars.size}" + (if (title == "BY DAY") " · best run $best" else "")
        else -> tasteHm65(total) + (if (title == "BY DAY") " · $active active days · best run $best" else "")
    }
    return NuvioCTasteCardPage(card, card.title, summary, barsTitle = title, bars = bars, focusBar = focus.coerceIn(0, (bars.size - 1).coerceAtLeast(0)))
}

/** [fork] round 66 taste_v66: an hour of the day in 12-hour time, "12am", "9pm" (round 66b; "12 AM" before). */
fun nuvioCHour12(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val twelve = if (h % 12 == 0) 12 else h % 12
    return if (com.nuvio.tv.NuvioCFeatures.TASTE_HOURS_PM) "$twelve${if (h < 12) "am" else "pm"}" else "$twelve ${if (h < 12) "AM" else "PM"}" // round 66b: 9pm
}

/** [fork] round 66: chart tick under an hour bar, every 3 hours ("12a", "3a" ... "9p"), blank otherwise. */
fun nuvioCHourTick(hour: Int): String {
    if (hour % 3 != 0) return ""
    val twelve = if (hour % 12 == 0) 12 else hour % 12
    return "$twelve${if (hour < 12) "a" else "p"}${if (com.nuvio.tv.NuvioCFeatures.TASTE_HOURS_PM) "m" else ""}" // round 66b: 9pm
}
