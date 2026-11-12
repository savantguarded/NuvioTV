package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 39 `taste_page`: the Taste page's sentences. Written from the numbers by plain rules (no AI),
// so they are always true for the data on screen, and hedged when there's little of it. English only (the app runs
// in English on Charles's TVs); kept here in one place so the voice stays consistent.

import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

internal object NuvioCTasteCopy {

    fun empty(): String = "Your taste takes shape as you watch. A few titles in, this page starts to fill in."

    /** The one line under the title. */
    fun summary(s: NuvioCTasteStats): String {
        if (s.empty) return empty()
        val genre = s.genres.firstOrNull()?.name
        if (!s.showNumbers) {
            return if (genre != null) "Early days, but you're leaning towards ${plural(genre)}." else "Early days. A few more titles and your taste starts to show."
        }
        val parts = mutableListOf<String>()
        if (genre != null) parts += "${genre}-heavy"
        s.topDecade?.let { parts += "${decade(it)}-leaning" }
        val critic = s.vsCrowd?.let {
            when {
                it <= -0.3 -> "tougher critic than the crowd"
                it >= 0.3 -> "kinder than the crowd"
                else -> "in step with the crowd"
            }
        }
        val line = when {
            parts.isEmpty() && critic != null -> critic.replaceFirstChar { it.uppercase() }
            parts.isEmpty() -> "A wide-ranging watcher"
            critic != null -> parts.joinToString(", ") + ", " + critic
            s.topDecade != null && parts.size == 1 -> parts.first() + ", with a soft spot for the ${decade(s.topDecade)}"
            else -> parts.joinToString(", ")
        }
        return line.replaceFirstChar { it.uppercase() } + "."
    }

    // ---- [fork] round 40 taste_v2: statements only, no instructions ----

    /** The hero line: the summary for a full profile, a plain count for a light one. */
    fun pageSummary(s: NuvioCTasteStats): String {
        if (s.empty) return "Nothing watched on this profile yet."
        if (!s.showNumbers) {
            val genre = s.genres.firstOrNull()?.name
            val count = if (s.titles == 1) "One title in." else "${numberWord(s.titles)} titles in."
            return if (genre != null) "$count $genre leads so far." else count
        }
        return summary(s)
    }

    /** "October was mostly crime." / "October leaned crime, with sci-fi close behind." */
    fun monthSummary(s: NuvioCTasteStats, month: java.time.YearMonth): String {
        val name = month.month.getDisplayName(TextStyle.FULL, Locale.US)
        if (s.empty) return "Nothing watched in $name ${month.year}."
        val first = s.genres.getOrNull(0) ?: return "$name: ${s.titles} titles."
        val second = s.genres.getOrNull(1)
        return when {
            first.percent >= 35 || second == null -> "$name was mostly ${plural(first.name)}."
            second.percent * 10 >= first.percent * 8 -> "$name leaned ${plural(first.name)}, with ${plural(second.name)} close behind."
            else -> "$name leaned ${plural(first.name)}, then ${plural(second.name)}."
        }
    }

    private fun numberWord(n: Int): String = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine").getOrElse(n) { n.toString() }

    /** Under the ratings chart (or the "What you pick" figure without ratings). */
    fun ratingLine(s: NuvioCTasteStats): String? {
        val vs = s.vsCrowd ?: return null
        val base = "You rate ${fmt(abs(vs))} ${if (vs >= 0) "above" else "below"} the crowd on average"
        val g = s.genreCritic ?: return "$base."
        val gDiff = g.second
        return "$base, but ${fmt(abs(gDiff))} ${if (gDiff >= 0) "above" else "below"} it on ${plural(g.first)}."
    }

    fun pickLine(s: NuvioCTasteStats): String? {
        val avg = s.crowdAverage ?: return null
        val cmp = when {
            avg >= CROWD_NORM + 0.2 -> ", above the ${fmt(CROWD_NORM)} norm"
            avg <= CROWD_NORM - 0.2 -> ", below the ${fmt(CROWD_NORM)} norm"
            else -> ", right around the norm"
        }
        return "You pick titles the crowd rates ${fmt(avg)} on average$cmp."
    }

    fun habitLine(s: NuvioCTasteStats): String? {
        val day = s.peakDay
        val hour = s.peakHour
        return when {
            day != null && hour != null -> "${dayName(day)}s are your biggest day, mostly ${hourPhrase(hour)}."
            day != null -> "${dayName(day)}s are your biggest day."
            hour != null -> "Most of your watching happens ${hourPhrase(hour)}."
            else -> null
        }
    }

    fun finishLine(s: NuvioCTasteStats): String? = s.finishedPercent?.let { "You finish $it% of the shows you start." }

    fun peopleLine(s: NuvioCTasteStats): String? {
        val top = s.people.firstOrNull() ?: return null
        val name = top.person.name.substringAfterLast(' ')
        return if (top.director) "No one shows up in your history more than $name, behind the camera ${top.count} times."
        else "$name turns up in ${top.count} of your titles, more than anyone else."
    }

    fun rangeLabel(r: NuvioCTasteRange): String = when (r) {
        NuvioCTasteRange.ALL -> "All time"
        NuvioCTasteRange.YEAR -> "This year"
        NuvioCTasteRange.DAYS_90 -> "Last 90 days"
    }

    /** Footer for thin data: what appears when, said once. */
    fun growingLine(s: NuvioCTasteStats): String? = when {
        s.empty -> null
        !s.showRatings && s.anyRatings -> "Your ratings chart appears at 25 ratings. Ratings you make in Nuvio C count too."
        !s.showRatings -> "Ratings you make in Nuvio C count here, even without a tracker."
        !s.showHabits || !s.showPeople -> "Habits and people appear as your history grows."
        else -> null
    }

    fun localLine(): String = "Based on what's been watched on this TV."

    const val CROWD_NORM = 6.6

    /** 1990 -> "90s", 2010 -> "2010s" (the short form only reads well last century). */
    internal fun decade(start: Int): String = if (start >= 2000) "${start}s" else "${start % 100}".padStart(2, '0') + "s"
    private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun hourPhrase(h: Int): String = when (h) {
        in 5..11 -> "in the morning"
        in 12..16 -> "in the afternoon"
        in 17..20 -> "in the evening"
        in 21..23 -> "after ${if (h == 21) 9 else if (h == 22) 10 else 11} pm"
        else -> "late at night"
    }

    private fun plural(genre: String): String = when (genre.lowercase()) {
        "comedy" -> "comedies"
        "drama" -> "dramas"
        "thriller" -> "thrillers"
        "western" -> "westerns"
        "documentary" -> "documentaries"
        "romance" -> "romances"
        "mystery" -> "mysteries"
        "animation" -> "animation"
        "horror" -> "horror"
        "sci-fi" -> "sci-fi"
        "crime" -> "crime"
        "action" -> "action"
        "war" -> "war films"
        "history" -> "history"
        "family" -> "family films"
        "music" -> "music films"
        else -> genre.lowercase()
    }
}
