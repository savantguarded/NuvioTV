package com.nuvio.tv.core.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.tv.domain.model.MetaPreview
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * [fork] Nuvio C home_featured: the Featured carousel shows the details-page tagline. The tagline
 * code lives in the optional details-page patch (and uses TMDB's tagline from the filmography patch),
 * so it plugs in here instead of being called directly: if the build robot ever drops those patches
 * the set is empty, the carousel shows no tagline, and the core still compiles.
 */
interface NuvioCFeaturedTagline {
    /** TMDB tagline for this title, or null. Cached by the TMDB service. */
    suspend fun taglineFor(item: MetaPreview): String?

    /** featured_badges (2026-10-17): tagline plus release facts for the NEW / NEW SEASON chip, one TMDB call. */
    suspend fun infoFor(item: MetaPreview): NuvioCFeaturedInfo? = NuvioCFeaturedInfo(tagline = taglineFor(item))

    /** Drawn in the details-page tagline style. */
    @Composable
    fun Draw(tagline: String, widthFraction: Float, modifier: Modifier)
}

/** [fork] What the carousel shows from TMDB for one title. Dates are ISO yyyy-MM-dd. */
data class NuvioCFeaturedInfo(
    val tagline: String? = null,
    val releaseDate: String? = null,
    val latestSeason: Int? = null,
    val latestSeasonAirDate: String? = null,
    /** [fork] round 51 featured_context_tags: the one contextual line above the logo ("NEW EPISODE TODAY · 9:00 PM"), or null. */
    val contextTag: String? = null,
    /** [fork] round 66 featured_v66: the age certificate. */
    val certificate: String? = null,
    /** [fork] round 66b: the last aired episode (season, episode, air date), so a show's quality can be looked up without its episode list. */
    val lastAired: Triple<Int, Int, String>? = null,
    /** [fork] round 66e: number of seasons (TMDB, specials not counted), for "2022 • 5 seasons". */
    val seasons: Int? = null,
    /** [fork] round 66e TAGS_SECOND_PART: what the title is (LIMITED SERIES, FINAL SEASON, APPLE TV+ ORIGINAL, A24…). */
    val identity: String? = null
)

@Module
@InstallIn(SingletonComponent::class)
abstract class NuvioCFeaturedModule {
    @Multibinds
    abstract fun featuredTaglines(): Set<NuvioCFeaturedTagline>
}
