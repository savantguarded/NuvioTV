package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 46 `taste_v3`: the Taste page's This week tiles, fresher Spotlight picks and plainer copy.
// Everything here is pure (tested in NuvioCRound46Test) and reads only the active profile's kept inputs; nothing
// is fetched.

import com.nuvio.tv.domain.model.WatchedItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** This week's numbers for the tiles. Null fields hide their tile. */
data class NuvioCTasteWeek(
    /** Minutes watched since Monday 00:00. */
    val minutes: Int,
    /** Average minutes over the previous 8 weeks up to the same point in the week; null without 2 weeks of history. */
    val usualMinutes: Int?,
    /** Average star rating (0.5 to 5) of titles rated this month, and last month; null when none. */
    val avgThisMonth: Double? = null,
    val avgLastMonth: Double? = null,
    val ratedThisMonth: Int = 0
)

/** The next new episode in the Calendar (air time known or a date). */
data class NuvioCTasteNextAir(val atMs: Long, val timeKnown: Boolean, val show: String, val slot: String?)

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** Minutes for one history line: a film's runtime once per film, an episode's runtime per episode. */
private fun minutesOf(lines: List<WatchedItem>, titles: Map<String, NuvioCTasteTitle>): Int {
    val films = HashSet<String>()
    val episodes = HashSet<String>()
    var total = 0
    lines.forEach { w ->
        val key = tasteKeyOf(w)
        if (w.season != null) {
            if (episodes.add("$key:${w.season}:${w.episode}")) total += titles[key]?.runtime ?: 40
        } else if (films.add(key)) {
            total += titles[key]?.runtime ?: 110
        }
    }
    return total
}

internal fun computeTasteWeek(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    scores: Map<String, Int>,
    ratedDates: Map<String, Long>,
    nowMs: Long,
    zone: ZoneId
): NuvioCTasteWeek {
    val usable = history.filter { it.contentId.startsWith("tt") || it.contentId.startsWith("tmdb:") }
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
    val elapsed = nowMs - weekStart
    val thisWeek = minutesOf(usable.filter { it.watchedAt in weekStart..nowMs }, titles)
    val first = usable.minOfOrNull { it.watchedAt }
    val past = (1..8).mapNotNull { back ->
        val start = weekStart - back * WEEK_MS
        if (first == null || first > start + WEEK_MS) return@mapNotNull null
        minutesOf(usable.filter { it.watchedAt in start..(start + elapsed) }, titles)
    }
    val usual = if (past.size >= 2) past.average().roundToInt() else null

    // ratings: rated date keys look like "movie:tt123" / "show:tt123"; scores are 1..10 by the same keys
    val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val lastMonthStart = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun avg(from: Long, to: Long): Pair<Double?, Int> {
        val s = ratedDates.filter { (_, at) -> at in from until to }.keys.mapNotNull { scores[it] }
        return (if (s.isEmpty()) null else s.average() / 2.0) to s.size
    }
    val (thisAvg, thisCount) = avg(monthStart, Long.MAX_VALUE)
    val (lastAvg, _) = avg(lastMonthStart, monthStart)
    return NuvioCTasteWeek(thisWeek, usual, thisAvg, lastAvg, thisCount)
}

/** "6 h 40 m", "45 m", "0 m". */
internal fun tasteDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m m"
        m == 0 -> "$h h"
        else -> "$h h $m m"
    }
}

/** The line under the hours tile: compared with the same point of a usual week. */
internal fun tasteWeekCompare(week: NuvioCTasteWeek): String? {
    val usual = week.usualMinutes ?: return null
    val diff = week.minutes - usual
    return when {
        kotlin.math.abs(diff) < 30 -> "About usual for this point in the week"
        diff > 0 -> "${tasteDuration(diff)} more than usual"
        else -> "${tasteDuration(-diff)} less than usual"
    }
}

/** "Tonight 9:00 pm", "Tomorrow 8:00 pm", "Thu 9:00 pm", "Thu" (date only). */
internal fun tasteAirLabel(next: NuvioCTasteNextAir, nowMs: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(next.atMs).atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), at.toLocalDate())
    val day = when (days) {
        0L -> if (next.timeKnown && at.hour >= 17) "Tonight" else "Today"
        1L -> "Tomorrow"
        in 2L..6L -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        else -> "${at.dayOfMonth} ${at.month.getDisplayName(TextStyle.SHORT, Locale.US)}"
    }
    if (!next.timeKnown) return day
    val h = at.hour % 12
    val time = "${if (h == 0) 12 else h}:${at.minute.toString().padStart(2, '0')} ${if (at.hour < 12) "am" else "pm"}"
    return "$day $time"
}

/** The rating tile's line: "up from 3.8 last month", "down from 4.2 last month", "same as last month". */
internal fun tasteRatingCompare(week: NuvioCTasteWeek): String? {
    val now = week.avgThisMonth ?: return null
    val before = week.avgLastMonth ?: return "${week.ratedThisMonth} rated this month"
    val d = now - before
    return when {
        kotlin.math.abs(d) < 0.05 -> "Same as last month"
        d > 0 -> "Up from ${oneDecimal(before)} last month"
        else -> "Down from ${oneDecimal(before)} last month"
    }
}

internal fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)

/** Plainer card names (round 46). Values stay as computed. */
internal fun tasteV3Kicker(card: NuvioCTasteInsight): String = when (card.id) {
    "balance" -> "Films or series"
    "crowd" -> "You vs the crowd"
    "eras" -> "Your eras"
    "binge" -> "Binge pace"
    "countries" -> "Around the world"
    "runtime" -> "Sweet spot"
    "day" -> "Your big day"
    "late" -> "Night owl"
    "rewatch" -> "Comfort rewatches"
    "mainstream" -> "Hits or hidden gems"
    "rise" -> "On the rise"
    "language" -> "Beyond English"
    "studio" -> "Studio you trust"
    "director" -> "You keep coming back"
    "actor" -> "Familiar face"
    "discovery" -> "New favourite"
    "franchise" -> "Franchise progress"
    "streak" -> "Longest streak"
    "oldest" -> "Oldest film"
    "ratings" -> "How you rate"
    "milestone" -> "Milestone"
    else -> card.kicker
}

/** Kickers renamed and up to three small posters (w154) from the titles each card counted. */
internal fun tasteV3Dress(cards: List<NuvioCTasteInsight>, titles: Map<String, NuvioCTasteTitle>): List<NuvioCTasteInsight> =
    cards.map { c ->
        c.copy(
            kicker = tasteV3Kicker(c),
            posters = c.keys.asSequence().mapNotNull { titles[it]?.poster }.distinct().take(3)
                .map { it.replace("/w342/", "/w154/") }.toList()
        )
    }

/**
 * Six cards for this visit. Cards shown last visit are left out while enough others exist (else they count a
 * fifth), cards whose value changed since they were last shown count double, and cards that suit the hour or a
 * weekend count three times. Same seed, same six.
 */
internal fun pickTasteSpotlightV3(
    all: List<NuvioCTasteInsight>,
    seed: Long,
    hour: Int,
    weekend: Boolean,
    lastShown: Set<String>,
    changed: Set<String>,
    count: Int = 6
): List<NuvioCTasteInsight> {
    if (all.size <= count) return all
    val fresh = all.filter { it.id !in lastShown }
    val pool = (if (fresh.size >= count) fresh else all).toMutableList()
    val random = java.util.Random(seed)
    val picked = ArrayList<NuvioCTasteInsight>()
    while (picked.size < count && pool.isNotEmpty()) {
        val weights = pool.map {
            var w = 10
            if ((it.hours?.contains(hour) == true) || (it.weekend && weekend)) w *= 3
            if (it.id in changed) w *= 2
            if (it.id in lastShown) w = (w / 5).coerceAtLeast(1)
            w
        }
        var r = random.nextInt(weights.sum())
        val i = weights.indices.first { r -= weights[it]; r < 0 }
        picked += pool.removeAt(i)
    }
    return picked
}

/** "Because you gave X 5 stars" (score 10) / "4.5 stars" (9); null below 9. */
internal fun tasteBecauseTitle(name: String, score: Int?): String? = when (score) {
    10 -> "Because you gave $name 5 stars"
    9 -> "Because you gave $name 4.5 stars"
    else -> null
}

@Suppress("unused")
private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
