package com.nuvio.tv.core.nuvioc

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// [fork] Nuvio C round 51 `featured_context_tags`: the line above the Featured carousel's logo. It says something true
// about the title today (it airs tonight, the whole season is out, it returns on a date), never a catalog rank: a rank
// is only meaningful in a ranked catalog and was wrong in a shuffled one. First rule that matches wins; no match = no
// line and the logo sits where it did. Pure logic over facts the carousel already has (TMDB's details answer, the
// Calendar's cache and the library), so no extra request is made.

internal data class NuvioCTagEpisode(
    val season: Int,
    val episode: Int,
    val date: LocalDate,
    /** Exact air time when the Calendar knows it. */
    val instantMs: Long? = null,
    val finale: Boolean = false,
    val premiere: Boolean = false
)

internal data class NuvioCTagFacts(
    val isSeries: Boolean,
    val returning: Boolean = false,
    val releaseDate: LocalDate? = null,
    val latestSeason: Int? = null,
    val latestSeasonAirDate: LocalDate? = null,
    val seasonEpisodes: Map<Int, Int> = emptyMap(),
    val next: NuvioCTagEpisode? = null,
    val last: NuvioCTagEpisode? = null,
    val inLibrary: Boolean = false
)

internal object NuvioCFeaturedTagRules {
    const val SOON_DAYS = 6L
    const val SEASON_OUT_DAYS = 14L
    const val PREMIERE_DAYS = 14L
    const val FILM_NEW_DAYS = 30L
    const val RETURN_MAX_DAYS = 240L

    fun tag(
        facts: NuvioCTagFacts,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale = Locale.getDefault()
    ): String? {
        val text = if (facts.isSeries) seriesTag(facts, today, zone, locale) else filmTag(facts, today)
        return (text ?: if (facts.inLibrary) "IN YOUR LIBRARY" else null)
    }

    private fun seriesTag(f: NuvioCTagFacts, today: LocalDate, zone: ZoneId, locale: Locale): String? {
        val next = f.next
        val last = f.last
        // 1. something airs today (the next episode, or the one that just moved to "last")
        val todayEp = listOfNotNull(next, last).firstOrNull { it.date == today }
        if (todayEp != null) {
            val what = when {
                todayEp.finale -> "SEASON FINALE"
                todayEp.premiere && todayEp.season > 1 -> "SEASON ${todayEp.season} PREMIERE"
                else -> "NEW EPISODE"
            }
            return "$what TODAY" + timeSuffix(todayEp, zone, locale)
        }
        // 2. a finale within the week
        if (next != null && next.finale && daysUntil(next.date, today) in 1..SOON_DAYS) {
            return "SEASON FINALE " + dayWord(next.date, today, locale) + timeSuffix(next, zone, locale)
        }
        // 3. a whole season released at once, in the last two weeks
        val sAir = f.latestSeasonAirDate
        val ls = f.latestSeason
        if (sAir != null && ls != null && last != null && last.season == ls) {
            val count = f.seasonEpisodes[ls] ?: 0
            val out = ChronoUnit.DAYS.between(sAir, today)
            val allAtOnce = count > 1 && last.episode >= count && ChronoUnit.DAYS.between(sAir, last.date) <= 1
            if (allAtOnce && out in 0..SEASON_OUT_DAYS) return "SEASON $ls · ALL $count EPISODES OUT"
        }
        // 4. a season premiere within two weeks
        if (next != null && next.episode == 1 && next.season > 1 && daysUntil(next.date, today) in 1..PREMIERE_DAYS) {
            return "SEASON ${next.season} PREMIERES " + dayWord(next.date, today, locale)
        }
        // 5. the next episode within the week
        if (next != null && daysUntil(next.date, today) in 1..SOON_DAYS) {
            return "NEW EPISODE " + dayWord(next.date, today, locale) + timeSuffix(next, zone, locale)
        }
        // 6. a confirmed return date further out
        if (next != null && f.returning && daysUntil(next.date, today) in (SOON_DAYS + 1)..RETURN_MAX_DAYS) {
            return (if (next.episode == 1 && next.season > 1) "SEASON ${next.season} RETURNS " else "RETURNS ") + dayWord(next.date, today, locale)
        }
        return null
    }

    private fun filmTag(f: NuvioCTagFacts, today: LocalDate): String? {
        val d = f.releaseDate ?: return null
        val days = ChronoUnit.DAYS.between(d, today)
        return if (days in 0..FILM_NEW_DAYS) "NEW RELEASE" else null
    }

    private fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** TOMORROW, a weekday within the week, or "MAR 14" beyond it. */
    internal fun dayWord(date: LocalDate, today: LocalDate, locale: Locale): String {
        val days = daysUntil(date, today)
        return when {
            days == 1L -> "TOMORROW"
            days in 2..SOON_DAYS -> weekday(date.dayOfWeek, locale)
            else -> date.format(DateTimeFormatter.ofPattern("MMM d", locale)).uppercase(locale)
        }
    }

    private fun weekday(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale).uppercase(locale)

    private fun timeSuffix(ep: NuvioCTagEpisode, zone: ZoneId, locale: Locale): String {
        val ms = ep.instantMs ?: return ""
        val time = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        return " · " + DateTimeFormatter.ofPattern("h:mm a", locale).format(time).uppercase(locale)
    }
}
