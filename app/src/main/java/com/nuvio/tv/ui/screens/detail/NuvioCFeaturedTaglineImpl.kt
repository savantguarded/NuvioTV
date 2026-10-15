package com.nuvio.tv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.di.NuvioCFeaturedTagline
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.MetaPreview
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

// [fork] Nuvio C home_featured: the details-page tagline (detail_tagline) offered to the Featured
// carousel through NuvioCFeaturedTagline. Same TMDB call the details page makes, so a title opened
// from the carousel finds it cached (and the reverse).
@Singleton
class NuvioCFeaturedTaglineImpl @Inject constructor(
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbSettings: TmdbSettingsDataStore
) : NuvioCFeaturedTagline {

    override suspend fun taglineFor(item: MetaPreview): String? {
        if (!NuvioCFeatures.DETAIL_TAGLINE) return null
        val settings = tmdbSettings.settings.value
        if (!settings.enabled) return null
        val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, item.apiType) }.getOrNull() ?: return null
        return runCatching {
            tmdbMetadataService.fetchEnrichment(tmdbId = tmdbId, contentType = item.type, language = settings.language)
        }.getOrNull()?.tagline?.let { nuvioCTaglineText(it) }
    }

    @Composable
    override fun Draw(tagline: String, widthFraction: Float, modifier: Modifier) {
        NuvioCTagline(tagline = tagline, widthFraction = widthFraction, modifier = modifier)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NuvioCFeaturedTaglineModule {
    @Binds
    @IntoSet
    abstract fun bind(impl: NuvioCFeaturedTaglineImpl): NuvioCFeaturedTagline
}
