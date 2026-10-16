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
    val latestSeasonAirDate: String? = null
)

@Module
@InstallIn(SingletonComponent::class)
abstract class NuvioCFeaturedModule {
    @Multibinds
    abstract fun featuredTaglines(): Set<NuvioCFeaturedTagline>
}
