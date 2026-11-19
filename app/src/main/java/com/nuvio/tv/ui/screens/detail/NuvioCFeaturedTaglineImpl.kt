package com.nuvio.tv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.di.NuvioCFeaturedTagline
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.core.calendar.CalendarEntry
import com.nuvio.tv.core.calendar.CalendarEntryKind
import com.nuvio.tv.core.calendar.CalendarEpisodeLabel
import com.nuvio.tv.core.calendar.CalendarRefresher
import com.nuvio.tv.core.nuvioc.NuvioCFeaturedTagRules
import com.nuvio.tv.core.nuvioc.NuvioCTagEpisode
import com.nuvio.tv.core.nuvioc.NuvioCTagFacts
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
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
    private val tmdbSettings: TmdbSettingsDataStore,
    private val calendarRefresher: CalendarRefresher, // [fork] round 51 featured_context_tags: air times and labels
    private val libraryRepository: LibraryRepository
) : NuvioCFeaturedTagline {

    override suspend fun infoFor(item: MetaPreview): com.nuvio.tv.core.di.NuvioCFeaturedInfo? {
        val settings = tmdbSettings.settings.value
        if (!settings.enabled) return null
        val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, item.apiType) }.getOrNull() ?: return null
        val enrichment = runCatching {
            tmdbMetadataService.fetchEnrichment(tmdbId = tmdbId, contentType = item.type, language = settings.language)
        }.getOrNull() ?: return null
        return com.nuvio.tv.core.di.NuvioCFeaturedInfo(
            tagline = if (NuvioCFeatures.DETAIL_TAGLINE) enrichment.tagline?.let { nuvioCTaglineText(it) } else null,
            releaseDate = enrichment.nuvioCReleaseDate,
            latestSeason = enrichment.nuvioCLatestSeason?.first,
            latestSeasonAirDate = enrichment.nuvioCLatestSeason?.second,
            contextTag = if (NuvioCFeatures.FEATURED_CONTEXT_TAGS) runCatching { contextTagFor(item, tmdbId, enrichment) }.getOrNull() else null
        )
    }

    // [fork] round 51 featured_context_tags: TMDB's answer (already fetched for the tagline) plus the Calendar's cache
    // for air times and finale / premiere labels, plus the library. No request of its own.
    private suspend fun contextTagFor(item: MetaPreview, tmdbId: String, e: com.nuvio.tv.core.tmdb.TmdbEnrichment): String? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val isSeries = item.apiType.equals("series", true) || item.apiType.equals("tv", true)
        fun date(raw: String?) = raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        fun com.nuvio.tv.core.tmdb.NuvioCEpisodeFact.toTag(): NuvioCTagEpisode? {
            val d = date(airDate) ?: return null
            return NuvioCTagEpisode(season, episode, d, finale = type.equals("finale", true), premiere = episode == 1)
        }
        fun CalendarEntry.toTag(): NuvioCTagEpisode? {
            if (kind != CalendarEntryKind.EPISODE) return null
            val d = localDate(zone) ?: return null
            return NuvioCTagEpisode(
                season ?: return null, episode ?: return null, d, airInstantMs,
                finale = label == CalendarEpisodeLabel.SEASON_FINALE || label == CalendarEpisodeLabel.SERIES_FINALE || label == CalendarEpisodeLabel.MID_SEASON_FINALE,
                premiere = label == CalendarEpisodeLabel.SEASON_PREMIERE || label == CalendarEpisodeLabel.MID_SEASON_PREMIERE || label == CalendarEpisodeLabel.NEW_SHOW
            )
        }
        var next = e.nuvioCNextEp?.toTag()
        var last = e.nuvioCLastEp?.toTag()
        if (isSeries) {
            val tmdbLong = tmdbId.toLongOrNull()
            val record = calendarRefresher.state.value.titles.firstOrNull { it.matchesKey(item.id) || (tmdbLong != null && it.ids.tmdb == tmdbLong) }
            val fromCalendar = record?.entries?.mapNotNull { it.toTag() }.orEmpty()
            fromCalendar.filter { it.date >= today }.minByOrNull { it.date }?.let { calNext ->
                // the Calendar knows the air time and the labels; TMDB only the date
                if (next == null || next.date >= calNext.date || (next.season == calNext.season && next.episode == calNext.episode)) next = calNext
            }
            fromCalendar.filter { it.date < today }.maxByOrNull { it.date }?.let { calLast ->
                if (last == null || calLast.date > last.date) last = calLast
            }
        }
        val inLibrary = withTimeoutOrNull(800) { libraryRepository.isInLibrary(item.id, item.apiType).first() } ?: false
        val facts = NuvioCTagFacts(
            isSeries = isSeries,
            returning = e.status.equals("Returning Series", true),
            releaseDate = date(e.nuvioCReleaseDate),
            latestSeason = e.nuvioCLatestSeason?.first,
            latestSeasonAirDate = date(e.nuvioCLatestSeason?.second),
            seasonEpisodes = e.nuvioCSeasonEpisodes,
            next = next,
            last = last,
            inLibrary = inLibrary
        )
        return NuvioCFeaturedTagRules.tag(facts, today, zone)
    }

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
