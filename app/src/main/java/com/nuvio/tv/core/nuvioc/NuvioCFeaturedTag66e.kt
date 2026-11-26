package com.nuvio.tv.core.nuvioc

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// [fork] Nuvio C round 66e `TAGS_SECOND_PART`: the context line's second part, saying what the title is (the first part,
// from NuvioCFeaturedTagRules, says when). Only data the carousel already has: TMDB's details answer (show type,
// status, networks, studios), the quality cache's editions and the MDBList scores the ratings line loads. No history.

internal object NuvioCFeaturedTag66e {
    /** How recently a finished show's last season must have started to read FINAL SEASON. */
    const val FINAL_SEASON_DAYS = 60L

    /** Streamers whose shows read "<NAME> ORIGINAL" when they are the show's first network (TMDB network name). */
    private val ORIGINALS = linkedMapOf(
        "apple tv+" to "APPLE TV+", "apple tv" to "APPLE TV+",
        "netflix" to "NETFLIX",
        "hbo" to "HBO", "max" to "MAX", "hbo max" to "MAX",
        "prime video" to "PRIME VIDEO", "amazon" to "PRIME VIDEO", "amazon prime video" to "PRIME VIDEO",
        "disney+" to "DISNEY+", "hulu" to "HULU", "paramount+" to "PARAMOUNT+", "peacock" to "PEACOCK",
        "fx" to "FX", "amc" to "AMC", "showtime" to "SHOWTIME"
    )

    /** Studios worth naming (TMDB production company name, case-insensitive). */
    private val STUDIOS = linkedMapOf(
        "a24" to "A24", "neon" to "NEON", "studio ghibli" to "STUDIO GHIBLI", "pixar" to "PIXAR", "blumhouse productions" to "BLUMHOUSE"
    )

    /** What the title is, from TMDB alone: limited / final season, then a streamer original (shows), then a studio. */
    fun identity(
        isSeries: Boolean,
        showType: String?,
        status: String?,
        hasNextEpisode: Boolean,
        latestSeasonAirDate: LocalDate?,
        networks: List<String>,
        studios: List<String>,
        today: LocalDate
    ): String? {
        if (isSeries) {
            if (showType.equals("Miniseries", true)) return "LIMITED SERIES"
            val finished = status.equals("Ended", true) || status.equals("Canceled", true)
            val recent = latestSeasonAirDate?.let { ChronoUnit.DAYS.between(it, today) in 0..FINAL_SEASON_DAYS } == true
            if (finished && (hasNextEpisode || recent)) return "FINAL SEASON"
            networks.firstOrNull()?.trim()?.lowercase()?.let { ORIGINALS[it] }?.let { return "$it ORIGINAL" }
        }
        studios.firstNotNullOfOrNull { STUDIOS[it.trim().lowercase()] }?.let { return it }
        return null
    }

    /** An edition the title has (any release), films only: "DIRECTOR'S CUT AVAILABLE". */
    fun edition(labels: List<String>): String? {
        val first = labels.firstOrNull { it in EDITION_TEXT } ?: return null
        return EDITION_TEXT.getValue(first) + " AVAILABLE"
    }

    private val EDITION_TEXT = linkedMapOf(
        "DIRECTOR'S CUT" to "DIRECTOR'S CUT", "EXTENDED" to "EXTENDED EDITION", "FINAL CUT" to "FINAL CUT",
        "ULTIMATE CUT" to "ULTIMATE CUT", "REDUX" to "REDUX", "IMAX" to "IMAX EDITION", "CRITERION" to "CRITERION EDITION"
    )

    /** Critics' scores high enough to say so: Metacritic 81+, or Certified Fresh at 95%+. */
    fun acclaimed(metacritic: Double?, tomatoes: Double?, tomatoesCertified: Boolean): String? =
        if ((metacritic ?: 0.0) >= 81.0 || (tomatoesCertified && (tomatoes ?: 0.0) >= 95.0)) "CRITICALLY ACCLAIMED" else null

    /** Both parts with a dot between, or whichever exists; the second is dropped when the first already says it. */
    fun join(first: String?, second: String?): String? {
        val a = first?.takeIf { it.isNotBlank() }
        val b = second?.takeIf { it.isNotBlank() && (a == null || !a.contains(it, ignoreCase = true)) }
        return when {
            a != null && b != null -> "$a$SEPARATOR$b"
            else -> a ?: b
        }
    }

    const val SEPARATOR = "  •  "
}
