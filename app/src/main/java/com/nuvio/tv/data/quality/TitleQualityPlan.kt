package com.nuvio.tv.data.quality

// [fork] Nuvio C round 60 `detail_quality_line`: what the badge line does for a title, decided before the first
// frame, in this order: no usable ID -> no line; not released yet -> NOT YET RELEASED (no call); otherwise a
// lookup (the cache is checked by the caller). Pure, for JVM tests.

import java.time.LocalDate

/** The line as drawn. */
sealed interface QualityLineState {
    /** No line at all (no usable IMDb ID; known before the first frame). */
    data object Absent : QualityLineState
    /** Dashed ghost pills while looking up. */
    data object Checking : QualityLineState
    /** Round 64: [editions] are edition labels seen in any release (at most two, priority order). */
    data class Found(val labels: List<String>, val editions: List<String> = emptyList()) : QualityLineState
    /** One dim pill: NOT YET RELEASED, NOT YET ON DIGITAL or QUALITY UNAVAILABLE. */
    data class Dim(val label: String) : QualityLineState

    companion object {
        const val NOT_YET_RELEASED = "NOT YET RELEASED"
        const val NOT_YET_ON_DIGITAL = "NOT YET ON DIGITAL"
        const val UNAVAILABLE = "QUALITY UNAVAILABLE"
    }
}

/** The bits of a title the plan reads (kept apart from Meta so tests need no app model). */
data class QualityTitle(
    val id: String,
    val imdbId: String?,
    val isSeries: Boolean,
    val released: String?,
    val episodes: List<QualityEpisode> = emptyList(),
    /** Round 64: the title's name, taken out of release names before looking for editions. */
    val name: String? = null
)

data class QualityEpisode(val season: Int?, val episode: Int?, val released: String?)

sealed interface QualityPlan {
    data object NoLine : QualityPlan
    data object NotYetReleased : QualityPlan
    /** IMDb ID still to come from TMDB ([tmdbId]); the line shows Checking meanwhile. */
    data class NeedsImdb(val tmdbId: Int) : QualityPlan
    data class Lookup(val cacheKey: String, val targets: List<QualityTarget>, val year: Int?, val releaseDate: LocalDate?) : QualityPlan
}

object TitleQualityPlanner {
    private val TT = Regex("^tt\\d+")

    fun imdbOf(t: QualityTitle): String? =
        t.imdbId?.trim()?.let { TT.find(it)?.value } ?: TT.find(t.id.trim())?.value

    /** A `tmdb:123` id, whose IMDb ID can be looked up. */
    fun tmdbOf(t: QualityTitle): Int? =
        Regex("^tmdb:(\\d+)").find(t.id.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun date(raw: String?): LocalDate? = raw?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Aired episodes, latest first (season 0 specials left out). */
    fun airedEpisodes(t: QualityTitle, today: LocalDate): List<QualityEpisode> =
        t.episodes.filter { (it.season ?: 0) > 0 && it.episode != null && date(it.released)?.let { d -> !d.isAfter(today) } == true }
            .distinctBy { it.season to it.episode }
            .sortedWith(compareByDescending<QualityEpisode> { it.season }.thenByDescending { it.episode })

    fun plan(t: QualityTitle, today: LocalDate, theatrical: String? = null, imdbOverride: String? = null): QualityPlan {
        val imdb = imdbOverride ?: imdbOf(t)
        if (imdb == null) return tmdbOf(t)?.let { QualityPlan.NeedsImdb(it) } ?: QualityPlan.NoLine
        if (!t.isSeries) {
            val release = date(theatrical) ?: date(t.released)
            if (release != null && release.isAfter(today)) return QualityPlan.NotYetReleased
            return QualityPlan.Lookup("m:$imdb", listOf(QualityTarget(imdb)), release?.year ?: yearOf(t.released), release)
        }
        val aired = airedEpisodes(t, today)
        if (aired.isEmpty()) {
            // episodes listed but none aired yet: not released; no episode list at all: nothing to ask
            return if (t.episodes.any { (it.season ?: 0) > 0 }) QualityPlan.NotYetReleased
            else QualityPlan.Lookup("s:$imdb:none", emptyList(), null, null)
        }
        val latest = aired.first()
        return QualityPlan.Lookup(
            "s:$imdb:${latest.season}:${latest.episode}",
            aired.take(2).map { QualityTarget(imdb, it.season, it.episode) },
            null,
            date(latest.released)
        )
    }

    private fun yearOf(raw: String?): Int? = raw?.let { Regex("(19|20)\\d\\d").find(it)?.value?.toIntOrNull() }

    /** Label when nothing usable turned up. NOT YET ON DIGITAL only for a recent film past its cinema date. */
    fun emptyLabel(
        lookup: QualityLookup, isSeries: Boolean, theatrical: String?, homeRelease: String?, today: LocalDate
    ): String {
        if (lookup !is QualityLookup.NothingUsable || isSeries) return QualityLineState.UNAVAILABLE
        val cinema = date(theatrical) ?: return QualityLineState.UNAVAILABLE
        if (cinema.isAfter(today) || cinema.isBefore(today.minusDays(RECENT_DAYS))) return QualityLineState.UNAVAILABLE
        val home = date(homeRelease)
        return if (home == null || home.isAfter(today)) QualityLineState.NOT_YET_ON_DIGITAL else QualityLineState.UNAVAILABLE
    }

    /** How long a found result is kept: QualiCache's own refresh time, else 1 day near release, else 7 days. */
    fun expiry(now: Long, releaseDate: LocalDate?, today: LocalDate, nextRefreshAt: Long?): Long {
        nextRefreshAt?.takeIf { it > now }?.let { return it }
        val recent = releaseDate != null && !releaseDate.isBefore(today.minusDays(90))
        return now + if (recent) DAY_MS else 7 * DAY_MS
    }

    const val RECENT_DAYS = 365L
    const val DAY_MS = 24 * 60 * 60_000L
    const val NOT_ON_DIGITAL_MS = 4 * 60 * 60_000L
}
