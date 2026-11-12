package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.remote.api.ImdbApiParentsGuideCategory
import com.nuvio.tv.data.remote.api.ParentalGuideApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** [fork] Nuvio C round 43: one guide category for the details page: level (none / mild / moderate / severe) and a short note. */
data class NuvioCGuideLine(val level: String?, val note: String?)

/** [fork] Nuvio C round 43: the five categories, in the order the details page shows them. */
data class NuvioCGuide(
    val nudity: NuvioCGuideLine? = null,
    val violence: NuvioCGuideLine? = null,
    val profanity: NuvioCGuideLine? = null,
    val alcohol: NuvioCGuideLine? = null,
    val frightening: NuvioCGuideLine? = null
)

/**
 * Resolved parental guide data with a single severity per category,
 * determined by the highest-voted severity level from the API.
 */
data class ParentalGuideResult(
    val nudity: String? = null,
    val violence: String? = null,
    val profanity: String? = null,
    val alcohol: String? = null,
    val frightening: String? = null
)

@Singleton
class ParentalGuideRepository @Inject constructor(
    private val api: ParentalGuideApi
) {
    private val cache = ConcurrentHashMap<String, ParentalGuideResult>()

    suspend fun getParentalGuide(imdbId: String): ParentalGuideResult? {
        if (!imdbId.startsWith("tt")) return null

        cache[imdbId]?.let { return it }

        return try {
            val response = api.getParentsGuide(imdbId)
            if (response.isSuccessful && !response.body()?.parentsGuide.isNullOrEmpty()) {
                val categories = response.body()!!.parentsGuide!!
                val result = mapToResult(categories)
                cache[imdbId] = result
                result
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ParentalGuide", "Failed to fetch parental guide for $imdbId", e)
            null
        }
    }

    // [fork] Nuvio C round 43 detail_guide: the same IMDb guide with a level and one short note per category, for the
    // details page. Kept apart from the player's lookup above (own cache), which stays as official.
    private val nuvioCCache = ConcurrentHashMap<String, NuvioCGuide>()

    suspend fun nuvioCGuide(imdbId: String): NuvioCGuide? {
        if (!imdbId.startsWith("tt")) return null
        nuvioCCache[imdbId]?.let { return it }
        return try {
            val response = api.getParentsGuide(imdbId)
            if (!response.isSuccessful) return null
            val categories = response.body()?.parentsGuide.orEmpty()
            val byKey = categories.associateBy { it.category.uppercase() }
            fun line(key: String): NuvioCGuideLine? {
                val c = byKey[key] ?: return null
                val votes = c.severityBreakdowns.orEmpty().filter { it.voteCount > 0 }
                val level = votes.maxByOrNull { it.voteCount }?.severityLevel?.lowercase()
                val note = c.reviews.orEmpty()
                    .filter { it.isSpoiler != true }
                    .mapNotNull { r -> r.text?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() } }
                    .map { it.replace(Regex("\\s+"), " ").trim() }
                    .filter { it.length >= 3 }
                    .minByOrNull { it.length }
                    ?.let { if (it.length > 170) it.take(168).trimEnd() + "…" else it }
                if (level == null && note == null) return null
                return NuvioCGuideLine(level, note)
            }
            NuvioCGuide(
                nudity = line("SEXUAL_CONTENT"),
                violence = line("VIOLENCE"),
                profanity = line("PROFANITY"),
                alcohol = line("ALCOHOL_DRUGS"),
                frightening = line("FRIGHTENING_INTENSE_SCENES")
            ).also { nuvioCCache[imdbId] = it }
        } catch (e: Exception) {
            Log.w("ParentalGuide", "Nuvio C guide failed for $imdbId", e)
            null
        }
    }

    private fun mapToResult(categories: List<ImdbApiParentsGuideCategory>): ParentalGuideResult {
        val categoryMap = categories.associateBy { it.category.uppercase() }

        return ParentalGuideResult(
            nudity = resolveSeverity(categoryMap["SEXUAL_CONTENT"]),
            violence = resolveSeverity(categoryMap["VIOLENCE"]),
            profanity = resolveSeverity(categoryMap["PROFANITY"]),
            alcohol = resolveSeverity(categoryMap["ALCOHOL_DRUGS"]),
            frightening = resolveSeverity(categoryMap["FRIGHTENING_INTENSE_SCENES"])
        )
    }

    /**
     * Determines the dominant severity level for a category by picking
     * the level with the highest vote count (excluding "none").
     * Returns null if no meaningful votes exist.
     */
    private fun resolveSeverity(category: ImdbApiParentsGuideCategory?): String? {
        if (category == null) return null

        val breakdowns = category.severityBreakdowns ?: return null

        // Find the severity with the most votes, excluding "none"
        val dominant = breakdowns
            .filter { it.severityLevel.lowercase() != "none" }
            .maxByOrNull { it.voteCount }

        // If "none" has more votes than any other severity, treat as no concern
        val noneVotes = breakdowns
            .firstOrNull { it.severityLevel.lowercase() == "none" }
            ?.voteCount ?: 0

        if (dominant == null || dominant.voteCount <= noneVotes) return null

        return dominant.severityLevel.lowercase()
    }
}
