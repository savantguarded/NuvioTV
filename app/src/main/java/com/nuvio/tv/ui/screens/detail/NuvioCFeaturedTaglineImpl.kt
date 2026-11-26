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
    private val libraryRepository: LibraryRepository,
    // [fork] round 52 featured_tags_v2: this profile's progress, ratings and Taste (lazy: built on first use)
    private val watchProgress: dagger.Lazy<com.nuvio.tv.domain.repository.WatchProgressRepository>,
    private val ratings: dagger.Lazy<com.nuvio.tv.data.nuvioc.NuvioCRatingService>,
    private val taste: dagger.Lazy<com.nuvio.tv.data.nuvioc.NuvioCTasteRepository>
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
            contextTag = if (NuvioCFeatures.FEATURED_CONTEXT_TAGS) runCatching { contextTagFor(item, tmdbId, enrichment) }.getOrNull() else null,
            lastAired = enrichment.nuvioCLastEp?.let { ep -> ep.airDate?.let { Triple(ep.season, ep.episode, it) } },
            certificate = if (NuvioCFeatures.FEATURED_V66) enrichment.ageRating?.takeIf { it.isNotBlank() } else null,
            // [fork] round 66e: season count and the context line's second part, from this same TMDB answer
            seasons = enrichment.nuvioCSeasonEpisodes.keys.count { it > 0 }.takeIf { it > 0 },
            identity = if (NuvioCFeatures.TAGS_SECOND_PART) runCatching {
                val isSeries = item.apiType.equals("series", true) || item.apiType.equals("tv", true)
                com.nuvio.tv.core.nuvioc.NuvioCFeaturedTag66e.identity(
                    isSeries = isSeries,
                    showType = enrichment.nuvioCShowType,
                    status = enrichment.status,
                    hasNextEpisode = enrichment.nuvioCNextEp != null,
                    latestSeasonAirDate = enrichment.nuvioCLatestSeason?.second?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                    networks = enrichment.networks.map { it.name },
                    studios = enrichment.productionCompanies.map { it.name },
                    today = java.time.LocalDate.now()
                )
            }.getOrNull() else null
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
        val v2 = if (NuvioCFeatures.FEATURED_TAGS_V2) runCatching { v2Facts(item, tmdbId, e, isSeries, last, today, zone) }.getOrNull() else null
        val facts = NuvioCTagFacts(
            isSeries = isSeries,
            returning = e.status.equals("Returning Series", true),
            releaseDate = date(e.nuvioCReleaseDate),
            latestSeason = e.nuvioCLatestSeason?.first,
            latestSeasonAirDate = date(e.nuvioCLatestSeason?.second),
            seasonEpisodes = e.nuvioCSeasonEpisodes,
            next = next,
            last = last,
            inLibrary = inLibrary,
            digitalDate = v2?.digitalDate,
            cinemaDate = v2?.cinemaDate,
            resumeMinutesLeft = v2?.resumeMinutesLeft,
            upNext = v2?.upNext,
            newSinceStopped = v2?.newSinceStopped,
            userRating = v2?.userRating,
            watchedYear = v2?.watchedYear,
            collectionSeen = v2?.collectionSeen,
            director = v2?.director,
            ended = v2?.ended ?: false,
            started = v2?.started ?: false,
            seasons = v2?.seasons
        )
        return NuvioCFeaturedTagRules.tag(facts, today, zone)
    }

    /** [fork] round 52 featured_tags_v2: the extra facts, each with a short time limit so the tag is never late. */
    private suspend fun v2Facts(
        item: MetaPreview,
        tmdbId: String,
        e: com.nuvio.tv.core.tmdb.TmdbEnrichment,
        isSeries: Boolean,
        last: NuvioCTagEpisode?,
        today: LocalDate,
        zone: ZoneId
    ): NuvioCTagFacts {
        val tmdbLong = tmdbId.toLongOrNull()
        val record = calendarRefresher.state.value.titles.firstOrNull { it.matchesKey(item.id) || (tmdbLong != null && it.ids.tmdb == tmdbLong) }
        val filmEntries = record?.entries.orEmpty().filter { it.kind != CalendarEntryKind.EPISODE }
        val digital = filmEntries.firstOrNull { it.kind == CalendarEntryKind.MOVIE_DIGITAL }?.localDate(zone)
        val cinema = filmEntries.firstOrNull { it.kind == CalendarEntryKind.MOVIE_THEATRICAL }?.localDate(zone)
        val progress = withTimeoutOrNull(600) { watchProgress.get().getProgress(item.id).first() }
        val scoreKeys = buildList {
            val kind = if (isSeries) "show" else "movie"
            if (item.id.startsWith("tt")) add("$kind:${item.id}")
            add("$kind:tmdb$tmdbId")
        }
        val score = withTimeoutOrNull(600) {
            val r = ratings.get()
            val local = r.localScores()
            scoreKeys.firstNotNullOfOrNull { local[it] } ?: r.traktScores().let { t -> scoreKeys.firstNotNullOfOrNull { t[it] } }
        }
        val watched = withTimeoutOrNull(600) { watchProgress.get().watchedItems.first() }.orEmpty()
        val watchedYear = watched.filter { it.contentId == item.id }.maxOfOrNull { it.watchedAt }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).year }
        var resume: Int? = null
        var upNext: Pair<Int, Int>? = null
        var newSince: Int? = null
        if (progress != null && progress.duration > 0) {
            val pct = progress.position.toDouble() / progress.duration
            if (!isSeries) {
                if (pct in 0.02..0.92) resume = ((progress.duration - progress.position) / 60_000L).toInt().coerceAtLeast(1)
            } else {
                val s = progress.season
                val ep = progress.episode
                if (s != null && ep != null) {
                    val finished = pct >= 0.9
                    val candidate = when {
                        !finished -> s to ep
                        ep < (e.nuvioCSeasonEpisodes[s] ?: Int.MAX_VALUE) -> s to ep + 1
                        e.nuvioCSeasonEpisodes.containsKey(s + 1) -> (s + 1) to 1
                        else -> null
                    }
                    // only an episode that has aired
                    val aired = candidate?.let { c -> last == null || c.first < last.season || (c.first == last.season && c.second <= last.episode) } == true
                    if (aired) upNext = candidate
                    if (last != null && last.season == s) newSince = (last.episode - ep).takeIf { finished && it >= 2 }
                }
            }
        }
        // the film's collection (TMDB, cached after the first ask) and how many parts this profile has watched
        val collection = if (!isSeries && e.collectionId != null) withTimeoutOrNull(1_500) {
            val c = tmdbMetadataService.fetchMovieCollection(e.collectionId!!, tmdbSettings.settings.value.language)
            val ids = c.items.map { it.id }.toSet()
            val seen = watched.count { it.contentId in ids && it.season == null }
            if (c.items.size >= 2) Triple(seen, c.items.size, e.collectionName ?: c.name.orEmpty()).takeIf { it.third.isNotBlank() } else null
        } else null
        val director = if (!isSeries) e.director.firstOrNull()?.let { name ->
            val stats = taste.get().state.value.all[com.nuvio.tv.data.nuvioc.NuvioCTasteRange.ALL]
            stats?.people?.firstOrNull { it.director && it.person.name.equals(name, true) }?.let { name to it.count }
        } else null
        return NuvioCTagFacts(
            isSeries = isSeries,
            digitalDate = digital,
            cinemaDate = cinema,
            resumeMinutesLeft = resume,
            upNext = upNext,
            newSinceStopped = newSince,
            userRating = score?.takeIf { it in 1..10 },
            watchedYear = watchedYear,
            collectionSeen = collection,
            director = director,
            ended = e.status.equals("Ended", true) || e.status.equals("Canceled", true),
            started = progress != null || watched.any { it.contentId == item.id },
            seasons = e.nuvioCSeasonEpisodes.keys.count { it > 0 }.takeIf { it > 0 }
        )
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
