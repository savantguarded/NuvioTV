package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 39 `taste_page`: what the Taste page knows and how it's worked out.
//
// Sources, all already on the TV or one TMDB call per title:
//  - Watch history: the profile's watched films and episodes with their dates (Nuvio sync's history, or this TV's).
//  - Ratings: your Trakt ratings (the same light copy the rate dialog keeps) plus ratings sent from this TV.
//  - TMDB: genres, year, country, running time, cast, director and the crowd score of each title, fetched once and
//    kept in one file for every profile (title facts aren't personal). Later builds only fetch new titles.
// No MDBList calls. The numbers are computed on the TV and kept per profile, so the page opens at once with the
// last result and refreshes quietly when it's more than a day old.

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- models ---------------------------------------------------------------------------------------------

@Serializable
data class NuvioCTastePerson(val id: Int, val name: String, val photo: String? = null)

/** TMDB facts for one title, shared by every profile. */
@Serializable
data class NuvioCTasteTitle(
    val key: String,
    val isShow: Boolean,
    val tmdbId: Int,
    val imdbId: String? = null,
    val name: String = "",
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    /** Film running time, or a typical episode's, in minutes. */
    val runtime: Int? = null,
    val episodes: Int? = null,
    val ended: Boolean = false,
    val vote: Double? = null,
    val votes: Int = 0,
    val poster: String? = null,
    val backdrop: String? = null,
    val cast: List<NuvioCTastePerson> = emptyList(),
    val directors: List<NuvioCTastePerson> = emptyList(),
    val fetchedAt: Long = 0L,
    // [fork] round 40 taste_insights: studio / network, original language and film collection (schema 2)
    val studios: List<String> = emptyList(),
    val lang: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    val schema: Int = 1
)

enum class NuvioCTasteRange { ALL, YEAR, DAYS_90 }

data class NuvioCTasteShare(val name: String, val count: Int, val percent: Int)
data class NuvioCTastePersonStat(val person: NuvioCTastePerson, val count: Int, val director: Boolean)
data class NuvioCTasteShelfItem(val id: String, val type: String, val name: String, val poster: String?, val note: String? = null)

/** [fork] round 40 taste_live: the counts on the live tiles (null = not known on this TV). */
data class NuvioCTasteLive(val watching: Int?, val upNext: Int?, val upcoming: Int?, val saved: Int?, val ratedThisMonth: Int?)

/** [fork] round 41 taste_review: the Month in review banner. */
data class NuvioCTasteReview(
    val month: java.time.YearMonth,
    /** true = the month is still running ("November so far"). */
    val current: Boolean,
    val headline: String,
    val hours: Int,
    val titles: Int,
    val topGenre: String?,
    val mostWatched: String?,
    val biggestDay: java.time.LocalDate?,
    val posters: List<String>
)

/** [fork] round 41 taste_card_titles: one row in a spotlight card's titles panel. */
data class NuvioCTastePanelItem(
    val id: String,
    val type: String,
    val name: String,
    val year: Int?,
    val poster: String?,
    /** Your rating 1..10 (half stars), null if unrated. */
    val score: Int?,
    val lastWatched: Long,
    /** [fork] round 42 taste_recap_panel: episodes watched in the recap's month (shows only). */
    val episodes: Int? = null
)

/** [fork] round 42 taste_recap_panel: one month's breakdown for the recap panel. */
data class NuvioCTasteRecapDetail(
    val month: java.time.YearMonth,
    val titles: Int,
    val hours: Int,
    val films: Int,
    val episodes: Int,
    val shows: Int,
    /** Top three genres with their share of the month's titles (0..100). */
    val genres: List<Pair<String, Int>>,
    val biggestDay: java.time.LocalDate?,
    val biggestDayMinutes: Int,
    val mostWatched: String?,
    val keys: List<String>
)

/** [fork] round 42 taste_review_fallback: not ready yet (retry) vs nothing to show. */
sealed interface NuvioCTasteReviewResult {
    object NotReady : NuvioCTasteReviewResult
    object None : NuvioCTasteReviewResult
    data class Ready(val review: NuvioCTasteReview) : NuvioCTasteReviewResult
}

/** [fork] round 40 taste_rows: one poster row for this visit. */
data class NuvioCTasteRow(val id: String, val title: String, val items: List<NuvioCTasteShelfItem>)

@Serializable
internal data class NuvioCTastePart(val id: Int, val name: String)

@Serializable
internal data class NuvioCTasteCollectionFile(val id: Int, val name: String, val parts: List<NuvioCTastePart>, val at: Long)

/** Everything the page draws. Sections the data can't support yet are null / empty, never guessed. */
data class NuvioCTasteStats(
    val range: NuvioCTasteRange = NuvioCTasteRange.ALL,
    val titles: Int = 0,
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0,
    val hours: Int = 0,
    val rated: Int = 0,
    val finishedPercent: Int? = null,
    val longestStreak: Int = 0,
    val historyDays: Int = 0,
    val genres: List<NuvioCTasteShare> = emptyList(),
    val decades: List<Pair<Int, Int>> = emptyList(),
    val people: List<NuvioCTastePersonStat> = emptyList(),
    /** Ratings 1..10 (half stars), counts. */
    val ratingCounts: List<Int> = emptyList(),
    val vsCrowd: Double? = null,
    val genreCritic: Pair<String, Double>? = null,
    val crowdAverage: Double? = null,
    /** Titles per day for the last 26 weeks, oldest first (182 values). */
    val heat: List<Int> = emptyList(),
    val peakDay: DayOfWeek? = null,
    val peakHour: Int? = null,
    val topDecade: Int? = null,
    val backdrops: List<String> = emptyList(),
    val unfinished: List<NuvioCTasteShelfItem> = emptyList(),
    val rewatch: List<NuvioCTasteShelfItem> = emptyList(),
    val anyRatings: Boolean = false,
    val localOnly: Boolean = false,
    /** [fork] round 41 taste_polish: backdrops of the five most recently watched titles (one shown per visit). */
    val recentBackdrops: List<String> = emptyList(),
    /** [fork] round 41 taste_polish: three posters (w154) from your best-liked titles in your top genre, for the Spin card. */
    val spinPosters: List<String> = emptyList()
) {
    val empty: Boolean get() = titles == 0
    val showNumbers: Boolean get() = titles >= 10
    val showGenres: Boolean get() = titles >= 10 && genres.isNotEmpty()
    val showPeople: Boolean get() = people.isNotEmpty()
    val showHabits: Boolean get() = historyDays >= 30 && heat.any { it > 0 }
    val showRatings: Boolean get() = rated >= 25
}

data class NuvioCTasteState(
    val profileId: Int = -1,
    val loading: Boolean = true,
    /** Titles still to look up on a first build (for the "Getting your history ready" line). */
    val pending: Int = 0,
    val all: Map<NuvioCTasteRange, NuvioCTasteStats> = emptyMap(),
    val builtAt: Long = 0L,
    // [fork] round 40: spotlight candidates per range, past-month recaps, months with history, first-build progress
    val insights: Map<NuvioCTasteRange, List<NuvioCTasteInsight>> = emptyMap(),
    val recaps: List<NuvioCTasteRecap> = emptyList(),
    val months: List<java.time.YearMonth> = emptyList(),
    val progressTotal: Int = 0,
    val progressPosters: List<String> = emptyList(),
    val progressGenres: List<Pair<String, Int>> = emptyList()
)

// ---- TMDB ----------------------------------------------------------------------------------------------

internal interface NuvioCTasteTmdbApi {
    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "credits",
        @Query("language") language: String = "en-US"
    ): Response<ResponseBody>

    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @QueryMap query: Map<String, String>): Response<ResponseBody>

    // [fork] round 40 taste_rows / taste_insights
    @GET("{type}/{id}/recommendations")
    suspend fun recommendations(@Path("type") type: String, @Path("id") id: Int, @Query("api_key") apiKey: String): Response<ResponseBody>

    @GET("person/{id}/combined_credits")
    suspend fun personCredits(@Path("id") id: Int, @Query("api_key") apiKey: String): Response<ResponseBody>

    @GET("collection/{id}")
    suspend fun collection(@Path("id") id: Int, @Query("api_key") apiKey: String): Response<ResponseBody>
}

// ---- repository ------------------------------------------------------------------------------------------

@Singleton
class NuvioCTasteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("tmdb") tmdbRetrofit: Retrofit,
    private val tmdbService: TmdbService,
    private val watchProgress: WatchProgressRepository,
    private val ratings: NuvioCRatingService,
    private val profiles: ProfileManager,
    private val library: com.nuvio.tv.domain.repository.LibraryRepository, // [fork] round 40 taste_live
    private val calendar: dagger.Lazy<com.nuvio.tv.core.calendar.CalendarRefresher> // [fork] round 40 taste_live
) {
    private val api = tmdbRetrofit.create(NuvioCTasteTmdbApi::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }
    private val buildMutex = Mutex()
    private val titlesFile get() = File(context.filesDir, "nuvio_c_taste_titles.json")
    @Volatile private var titleCache: MutableMap<String, NuvioCTasteTitle>? = null

    private val _state = MutableStateFlow(NuvioCTasteState())
    val state: StateFlow<NuvioCTasteState> = _state.asStateFlow()

    private val refetchAllNext = java.util.concurrent.atomic.AtomicBoolean(false)

    /** [fork] round 42: the next forced build reads every title's details again. */
    fun requestRefetchAll() { refetchAllNext.set(true) }

    /** The page opened: show the last result at once, rebuild when it's older than a day (or [force]). */
    suspend fun open(force: Boolean = false) {
        val profile = profiles.activeProfileId.value
        if (_state.value.profileId != profile) _state.value = NuvioCTasteState(profileId = profile)
        val saved = _state.value.takeIf { it.all.isNotEmpty() } ?: loadSaved(profile)
        if (saved != null) _state.value = saved
        val stale = saved == null || System.currentTimeMillis() - saved.builtAt > DAY_MS
        if (force || stale) build(profile) else _state.value = _state.value.copy(loading = false)
    }

    /** Quiet daily refresh (start-up, nothing playing): only when the saved result is over a day old. */
    suspend fun refreshIfStale() {
        val profile = profiles.activeProfileId.value
        val saved = loadSaved(profile)
        if (saved != null && System.currentTimeMillis() - saved.builtAt < DAY_MS) return
        runCatching { build(profile, quiet = true) }
    }

    private suspend fun build(profile: Int, quiet: Boolean = false) = buildMutex.withLock {
        if (!quiet) _state.value = _state.value.copy(loading = true)
        val result = runCatching { withContext(Dispatchers.IO) { compute(profile, quiet) } }
            .onFailure { Log.w(TAG, "Taste build failed: ${it.message}") }.getOrNull()
        if (result != null) {
            if (profiles.activeProfileId.value == profile) _state.value = result
            save(profile, result)
        } else if (!quiet) {
            _state.value = _state.value.copy(loading = false)
        }
    }

    private suspend fun compute(profile: Int, quiet: Boolean): NuvioCTasteState {
        val history = withTimeoutOrNull(8_000) { watchProgress.watchedItems.first() }.orEmpty()
        val localOnly = !watchProgress.hasActiveTrackingProgressProvider() && history.none { it.trackingProviderId != null }
        val scores = HashMap<String, Int>().apply {
            putAll(runCatching { ratings.traktScores() }.getOrDefault(emptyMap()))
            putAll(ratings.localScores()) // sent from this TV wins
        }
        // [fork] round 42 taste_profile_strict: history and ratings are read for the active profile; if that changed
        // since this build started, the result would belong to another profile, so drop it.
        if (com.nuvio.tv.NuvioCFeatures.TASTE_PROFILE_STRICT && profiles.activeProfileId.value != profile) {
            throw IllegalStateException("profile changed during the Taste build")
        }
        val wanted = history.mapNotNull { titleRef(it) }.distinctBy { it.first }
        val cache = titles()
        val refetch = refetchAllNext.getAndSet(false) // [fork] round 42 taste_refresh_feedback: long press re-reads every title
        val fresh = wanted.filter { (key, _) -> refetch || cache[key]?.let { System.currentTimeMillis() - it.fetchedAt < REFETCH_MS } != true }
        // [fork] round 40 taste_insights: titles cached before studios / language / collections were kept are read
        // again in quiet batches (300 a build), so the new cards fill in without one long first wait.
        val upgrade = if (com.nuvio.tv.NuvioCFeatures.TASTE_INSIGHTS) wanted.filter { (key, _) -> cache[key]?.let { it.schema < TASTE_SCHEMA } == true }.take(300) else emptyList()
        val missing = fresh + upgrade
        val firstBuild = _state.value.all.isEmpty()
        if (!quiet) _state.value = _state.value.copy(pending = missing.size, progressTotal = if (firstBuild) wanted.size else 0)
        if (missing.isNotEmpty()) fetchTitles(missing.take(MAX_FETCH_PER_BUILD), cache, quiet, if (firstBuild) wanted.map { it.first } else null)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val all = NuvioCTasteRange.entries.associateWith {
            computeTasteStats(history, cache, scores, it, now, zone, localOnly)
        }
        // [fork] round 40: inputs kept in memory for month views, rows and live tiles (nothing re-read per press)
        val collections = if (com.nuvio.tv.NuvioCFeatures.TASTE_INSIGHTS) collectionsFor(history, cache) else emptyMap()
        TasteInputs(profile, history, scores, localOnly, collections).let { lastInputs = it; inputsByProfile[profile] = it }
        val insights = if (com.nuvio.tv.NuvioCFeatures.TASTE_INSIGHTS) NuvioCTasteRange.entries.associateWith { r ->
            computeTasteInsights(history, cache, scores, rangeWindow(r, now, zone), now, zone, r == NuvioCTasteRange.ALL, collections)
        } else emptyMap()
        val recaps = if (com.nuvio.tv.NuvioCFeatures.TASTE_MONTH) computeTasteRecaps(history, cache, now, zone) else emptyList()
        val months = if (com.nuvio.tv.NuvioCFeatures.TASTE_MONTH) tasteHistoryMonths(history, zone) else emptyList()
        return NuvioCTasteState(
            profileId = profile, loading = false, pending = 0, all = all, builtAt = now,
            insights = insights, recaps = recaps, months = months
        )
    }

    /** ("movie:tt123" or "tv:tt123" / "tv:tmdb:123", isShow) for a history line; null for ids TMDB can't place. */
    private fun titleRef(item: WatchedItem): Pair<String, Boolean>? {
        val id = item.contentId.trim()
        if (!id.startsWith("tt") && !id.startsWith("tmdb:")) return null
        val show = item.season != null || item.contentType.equals("series", true) || item.contentType.equals("tv", true)
        return (if (show) "tv:" else "movie:") + id.substringBefore(':').let { if (id.startsWith("tmdb:")) id else it } to show
    }

    private suspend fun fetchTitles(
        refs: List<Pair<String, Boolean>>,
        cache: MutableMap<String, NuvioCTasteTitle>,
        quiet: Boolean,
        /** [fork] round 40 taste_build_anim: the profile's titles, so the first build can show its genres forming. */
        wantedKeys: List<String>? = null
    ) = coroutineScope {
        val gate = Semaphore(4)
        var left = refs.size
        refs.chunked(40).forEach { batch ->
            batch.map { (key, show) ->
                async {
                    gate.withPermit {
                        runCatching { fetchTitle(key, show) }.getOrNull()?.let { synchronized(cache) { cache[key] = it } }
                    }
                }
            }.awaitAll()
            left -= batch.size
            if (!quiet) {
                if (wantedKeys != null && com.nuvio.tv.NuvioCFeatures.TASTE_BUILD_ANIM) {
                    val known = synchronized(cache) { wantedKeys.mapNotNull { cache[it] } }
                    val genres = known.flatMap { it.genres.distinct() }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
                    val posters = synchronized(cache) { batch.mapNotNull { cache[it.first]?.poster } }.takeLast(6)
                    _state.value = _state.value.copy(pending = left.coerceAtLeast(0), progressGenres = genres,
                        progressPosters = posters.map { it.replace("/w342/", "/w154/") })
                } else {
                    _state.value = _state.value.copy(pending = left.coerceAtLeast(0))
                }
            }
            saveTitles(cache)
        }
    }

    private suspend fun fetchTitle(key: String, show: Boolean): NuvioCTasteTitle? {
        val raw = key.substringAfter(':')
        val tmdbId = if (raw.startsWith("tmdb:")) raw.removePrefix("tmdb:").toIntOrNull()
        else withTimeoutOrNull(6_000) { tmdbService.ensureTmdbId(raw, if (show) "series" else "movie") }?.toIntOrNull()
        tmdbId ?: return null
        val body = withTimeoutOrNull(8_000) {
            api.details(if (show) "tv" else "movie", tmdbId, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() }
        } ?: return null
        val o = json.parseToJsonElement(body).jsonObject
        val credits = o["credits"] as? JsonObject
        val cast = (credits?.get("cast") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.take(6).mapNotNull(::person)
        val directors = if (show) {
            (o["created_by"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.take(2).mapNotNull(::person)
        } else {
            (credits?.get("crew") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.str("job") == "Director" }.take(2).mapNotNull(::person)
        }
        val date = o.str(if (show) "first_air_date" else "release_date")
        val runtime = if (show) (o["episode_run_time"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.intOrNull } ?: 40
        else o.int("runtime")
        return NuvioCTasteTitle(
            key = key,
            isShow = show,
            tmdbId = tmdbId,
            imdbId = raw.takeIf { it.startsWith("tt") },
            name = o.str(if (show) "name" else "title").orEmpty(),
            year = date?.take(4)?.toIntOrNull(),
            genres = (o["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }.map(::tidyGenre),
            countries = (o["origin_country"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: (o["production_countries"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("iso_3166_1") },
            runtime = runtime?.takeIf { it > 0 },
            episodes = o.int("number_of_episodes"),
            ended = o.str("status")?.lowercase() in setOf("ended", "canceled", "cancelled"),
            vote = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 },
            votes = o.int("vote_count") ?: 0,
            poster = o.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
            backdrop = o.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            cast = cast,
            directors = directors,
            fetchedAt = System.currentTimeMillis(),
            studios = (if (show) o["networks"] else o["production_companies"]).let { it as? JsonArray }.orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("name") }.take(2),
            lang = o.str("original_language"),
            collectionId = (o["belongs_to_collection"] as? JsonObject)?.int("id"),
            collectionName = (o["belongs_to_collection"] as? JsonObject)?.str("name"),
            schema = TASTE_SCHEMA
        )
    }

    private fun person(o: JsonObject): NuvioCTastePerson? {
        val id = o.int("id") ?: return null
        val name = o.str("name") ?: return null
        return NuvioCTastePerson(id, name, o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" })
    }

    /** Unwatched, well-loved titles in your top genre and decade (the "Your gaps" shelf). One discover call. */
    suspend fun gaps(stats: NuvioCTasteStats, watchedKeys: Set<String>): List<NuvioCTasteShelfItem> = withContext(Dispatchers.IO) {
        val genre = stats.genres.firstOrNull()?.name ?: return@withContext emptyList()
        val genreId = TMDB_MOVIE_GENRES.entries.firstOrNull { it.value.equals(genre, true) }?.key ?: return@withContext emptyList()
        val q = linkedMapOf(
            "api_key" to tmdbService.apiKey(),
            "include_adult" to "false",
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to "1500",
            "vote_average.gte" to "7.4",
            "with_genres" to genreId.toString()
        )
        stats.topDecade?.let { q["primary_release_date.gte"] = "$it-01-01"; q["primary_release_date.lte"] = "${it + 9}-12-31" }
        val body = withTimeoutOrNull(8_000) { api.discover("movie", q).takeIf { it.isSuccessful }?.body()?.use { it.string() } }
            ?: return@withContext emptyList()
        val results = (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val cached = titles()
        val seenTmdb = cached.values.filter { "movie:" + (it.imdbId ?: "tmdb:${it.tmdbId}") in watchedKeys || it.key in watchedKeys }.map { it.tmdbId }.toSet()
        results.filter { (it.int("id") ?: 0) !in seenTmdb }.take(12).mapNotNull { r ->
            val id = r.int("id") ?: return@mapNotNull null
            NuvioCTasteShelfItem(
                id = "tmdb:$id",
                type = "movie",
                name = r.str("title").orEmpty(),
                poster = r.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                note = r.str("release_date")?.take(4)
            )
        }
    }

    // ---- [fork] round 40 ------------------------------------------------------------------------------------

    private class TasteInputs(
        val profile: Int,
        val history: List<WatchedItem>,
        val scores: Map<String, Int>,
        val localOnly: Boolean,
        val collections: Map<Int, NuvioCTasteCollection>
    )
    // [fork] round 42 taste_profile_strict: kept per profile; every reader gets the active profile's only.
    private val inputsByProfile = java.util.concurrent.ConcurrentHashMap<Int, TasteInputs>()
    @Volatile private var lastInputs: TasteInputs? = null
    private val inputs: TasteInputs?
        get() = if (com.nuvio.tv.NuvioCFeatures.TASTE_PROFILE_STRICT) inputsByProfile[profiles.activeProfileId.value] else lastInputs

    private fun rangeWindow(r: NuvioCTasteRange, now: Long, zone: ZoneId): LongRange = when (r) {
        NuvioCTasteRange.ALL -> Long.MIN_VALUE..now
        NuvioCTasteRange.YEAR -> LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()..now
        NuvioCTasteRange.DAYS_90 -> (now - 90L * DAY_MS)..now
    }

    /** One month's numbers and cards, from the kept inputs (pure, runs off the main thread). Null before a build. */
    suspend fun month(month: java.time.YearMonth): Pair<NuvioCTasteStats, List<NuvioCTasteInsight>>? = withContext(Dispatchers.Default) {
        val i = inputs?.takeIf { it.profile == profiles.activeProfileId.value } ?: return@withContext null
        val zone = ZoneId.systemDefault()
        val window = tasteMonthWindow(month, zone)
        val now = System.currentTimeMillis()
        val cache = titles()
        val stats = computeTasteStats(i.history, cache, i.scores, NuvioCTasteRange.ALL, now, zone, i.localOnly, window)
        stats to computeTasteInsights(i.history, cache, i.scores, window, now, zone, false, i.collections)
    }

    // ---- [fork] round 41 ------------------------------------------------------------------------------------

    /**
     * The Month in review banner: last month during the first 7 days of a month, then the current month so far.
     * Null before a build or when that month has fewer than 10 titles. Pure, from the kept inputs.
     */
    /**
     * [fork] round 42 taste_review_fallback: the month (current from day 8, else last) when it has 5 or more titles,
     * otherwise the most recent earlier month that does. NotReady when the profile's data isn't built yet.
     */
    suspend fun reviewResult(): NuvioCTasteReviewResult = withContext(Dispatchers.Default) {
        val i = inputs?.takeIf { it.profile == profiles.activeProfileId.value } ?: return@withContext NuvioCTasteReviewResult.NotReady
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val current = today.dayOfMonth > 7
        val first = java.time.YearMonth.from(today).let { if (current) it else it.minusMonths(1) }
        val cache = titles()
        for (back in 0L..18L) {
            val month = first.minusMonths(back)
            val r = computeTasteReview(i.history, cache, tasteMonthWindow(month, zone), month, current && back == 0L, zone, minTitles = 5)
            if (r != null) return@withContext NuvioCTasteReviewResult.Ready(r)
        }
        NuvioCTasteReviewResult.None
    }

    /** [fork] round 42 taste_recap_panel: a month's breakdown, from the kept inputs (active profile only). */
    suspend fun recapDetail(month: java.time.YearMonth): NuvioCTasteRecapDetail? = withContext(Dispatchers.Default) {
        val i = inputs?.takeIf { it.profile == profiles.activeProfileId.value } ?: return@withContext null
        computeTasteRecapDetail(i.history, titles(), tasteMonthWindow(month, ZoneId.systemDefault()), month, ZoneId.systemDefault())
    }

    suspend fun review(): NuvioCTasteReview? = withContext(Dispatchers.Default) {
        val i = inputs?.takeIf { it.profile == profiles.activeProfileId.value } ?: return@withContext null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val current = today.dayOfMonth > 7
        val month = java.time.YearMonth.from(today).let { if (current) it else it.minusMonths(1) }
        computeTasteReview(i.history, titles(), tasteMonthWindow(month, zone), month, current, zone)
    }

    /** A card's titles with what the panel shows (nothing fetched: cached facts, your ratings, your history). */
    suspend fun panel(keys: List<String>, window: LongRange? = null): List<NuvioCTastePanelItem> = withContext(Dispatchers.Default) {
        val i = inputs ?: return@withContext emptyList()
        val cache = titles()
        val wanted = keys.toSet()
        val last = HashMap<String, Long>()
        val episodes = HashMap<String, MutableSet<Pair<Int, Int>>>() // [fork] round 42: per show, in the window
        i.history.forEach { w ->
            val k = tasteKeyOf(w)
            if (k !in wanted || (window != null && w.watchedAt !in window)) return@forEach
            if (w.watchedAt > (last[k] ?: Long.MIN_VALUE)) last[k] = w.watchedAt
            w.season?.let { episodes.getOrPut(k) { HashSet() }.add(it to (w.episode ?: 0)) }
        }
        keys.distinct().mapNotNull { k ->
            val t = cache[k] ?: return@mapNotNull null
            if (t.name.isBlank()) return@mapNotNull null
            NuvioCTastePanelItem(
                id = t.imdbId ?: "tmdb:${t.tmdbId}",
                type = if (t.isShow) "series" else "movie",
                name = t.name,
                year = t.year,
                poster = t.poster?.replace("/w342/", "/w154/"),
                score = tasteScoreFor(k, cache, i.scores)?.takeIf { it in 1..10 },
                lastWatched = last[k] ?: 0L,
                episodes = if (t.isShow && window != null) episodes[k]?.size else null
            )
        }.sortedByDescending { it.lastWatched }
    }

    /** Collections you've seen two or more films of: parts from a small file, up to 5 new collections read per build. */
    private suspend fun collectionsFor(history: List<WatchedItem>, cache: Map<String, NuvioCTasteTitle>): Map<Int, NuvioCTasteCollection> {
        val ids = history.map(::tasteKeyOf).filter { it.startsWith("movie:") }.toSet().mapNotNull { cache[it]?.collectionId }
            .groupingBy { it }.eachCount().filter { it.value >= 2 }.keys
        if (ids.isEmpty()) return emptyMap()
        val saved = runCatching {
            collectionsFile.takeIf { it.exists() }?.readText()?.let { json.decodeFromString<List<NuvioCTasteCollectionFile>>(it) }
        }.getOrNull().orEmpty().associateBy { it.id }.toMutableMap()
        var changed = false
        ids.filter { it !in saved || System.currentTimeMillis() - saved.getValue(it).at > REFETCH_MS }.take(5).forEach { id ->
            val body = withTimeoutOrNull(6_000) { api.collection(id, tmdbService.apiKey()).takeIf { r -> r.isSuccessful }?.body()?.use { it.string() } }
                ?: return@forEach
            runCatching {
                val o = json.parseToJsonElement(body).jsonObject
                val today = LocalDate.now().toString()
                val parts = (o["parts"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .filter { (it.str("release_date") ?: "9999") <= today }
                    .sortedBy { it.str("release_date") ?: "9999" }
                    .mapNotNull { p -> p.int("id")?.let { pid -> NuvioCTastePart(pid, p.str("title").orEmpty()) } }
                saved[id] = NuvioCTasteCollectionFile(id, o.str("name").orEmpty(), parts, System.currentTimeMillis())
                changed = true
            }
        }
        if (changed) runCatching { collectionsFile.writeText(json.encodeToString(saved.values.toList())) }
        return saved.filterKeys { it in ids }.mapValues { (_, c) -> NuvioCTasteCollection(c.id, c.name, c.parts.map { it.id to it.name }) }
    }

    private val collectionsFile get() = File(context.filesDir, "nuvio_c_taste_collections.json")

    /** The live tiles: what's on the go now, for this profile. Each count fails quietly to null. */
    suspend fun live(): NuvioCTasteLive = withContext(Dispatchers.IO) {
        val watching = withTimeoutOrNull(4_000) { watchProgress.continueWatching.first() }?.map { it.contentId }?.toSet()?.size
        val upNext = withTimeoutOrNull(4_000) { watchProgress.observeNextUpSeeds().first() }?.map { it.contentId }?.toSet()?.size
        val saved = withTimeoutOrNull(4_000) { library.libraryItems.first() }?.size
        val upcoming = runCatching {
            val cal = calendar.get().state.value
            if (!cal.loaded) null else {
                val today = LocalDate.now()
                val until = today.plusDays(30)
                cal.titles.count { t ->
                    t.entries.any { e ->
                        val d = e.airInstantMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                            ?: e.airDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                        d != null && !d.isBefore(today) && !d.isAfter(until)
                    }
                }
            }
        }.getOrNull()
        val dates = runCatching { ratings.ratedDates() }.getOrDefault(emptyMap())
        val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ratedThisMonth = if (dates.isEmpty() && inputs?.scores.isNullOrEmpty()) null else dates.values.count { it >= monthStart }
        NuvioCTasteLive(watching, upNext, upcoming, saved, ratedThisMonth)
    }

    /**
     * This visit's poster rows: up to two that need TMDB (one call each), chosen at random with [seed], then
     * Unfinished and Rewatch when they have titles. Items are shuffled; watched titles are left out.
     */
    /** [fork] round 42 taste_seen_rated: TMDB ids you've seen, films and shows apart. */
    internal class TasteSeen(val movies: Set<Int>, val shows: Set<Int>) {
        val any: Set<Int> get() = movies + shows
        fun has(type: String, id: Int): Boolean =
            if (!com.nuvio.tv.NuvioCFeatures.TASTE_SEEN_RATED) id in movies || id in shows
            else if (type == "tv") id in shows else id in movies
    }

    private fun tasteSeen(i: TasteInputs, cache: Map<String, NuvioCTasteTitle>): TasteSeen {
        val movies = HashSet<Int>()
        val shows = HashSet<Int>()
        i.history.map(::tasteKeyOf).toSet().forEach { k -> cache[k]?.let { if (it.isShow) shows += it.tmdbId else movies += it.tmdbId } }
        if (com.nuvio.tv.NuvioCFeatures.TASTE_SEEN_RATED) {
            // Anything you've rated counts as seen (you rate what you've watched), even when it isn't in this
            // profile's watch history (watched before Nuvio sync, or on another app). Keys: "movie:tt…",
            // "movie:tmdb123", "show:tt…", "show:tmdb123".
            i.scores.keys.forEach { key ->
                val show = key.startsWith("show:")
                val raw = key.substringAfter(':')
                val tmdb = raw.removePrefix("tmdb").takeIf { raw.startsWith("tmdb") }?.toIntOrNull()
                    ?: cache[(if (show) "tv:" else "movie:") + raw]?.tmdbId
                tmdb?.let { if (show) shows += it else movies += it }
            }
        }
        return TasteSeen(movies, shows)
    }

    suspend fun rows(stats: NuvioCTasteStats, seed: Long): List<NuvioCTasteRow> = withContext(Dispatchers.IO) {
        val random = java.util.Random(seed)
        val i = inputs ?: return@withContext emptyList()
        val cache = titles()
        val seen = tasteSeen(i, cache)
        val watchedTmdb = seen.any
        val makers = mutableListOf<suspend () -> NuvioCTasteRow?>(
            { becauseYouLoved(i, cache, seen, random) },
            { moreFrom(stats, seen, random) },
            { decadeGaps(stats, seen, random) },
            { genreUnseen(stats, seen, random) }
        )
        java.util.Collections.shuffle(makers, random)
        val network = ArrayList<NuvioCTasteRow>()
        for (m in makers) {
            if (network.size >= 2) break
            runCatching { m() }.getOrNull()?.takeIf { it.items.size >= 4 }?.let { network += it }
        }
        val local = listOfNotNull(
            stats.unfinished.takeIf { it.isNotEmpty() }?.let { NuvioCTasteRow("unfinished", "Unfinished", it.shuffled(random)) },
            stats.rewatch.takeIf { it.isNotEmpty() }?.let { NuvioCTasteRow("rewatch", "Worth a rewatch", it.shuffled(random)) }
        )
        network + local
    }

    private val rowPrefs by lazy { context.getSharedPreferences("nuvio_c_taste_rows", Context.MODE_PRIVATE) }

    /** "Because you loved X": X rated 9+ in the last 12 months (recent weighted), not used in the last 7 days. */
    private suspend fun becauseYouLoved(i: TasteInputs, cache: Map<String, NuvioCTasteTitle>, watched: TasteSeen, random: java.util.Random): NuvioCTasteRow? {
        val now = System.currentTimeMillis()
        val dates = runCatching { ratings.ratedDates() }.getOrDefault(emptyMap())
        val keys = i.history.map(::tasteKeyOf).toSet()
        fun usedRecently(t: NuvioCTasteTitle) = now - rowPrefs.getLong("p${i.profile}:seed:${t.tmdbId}", 0L) < 7 * DAY_MS
        val loved = keys.mapNotNull { k ->
            val t = cache[k] ?: return@mapNotNull null
            val s = tasteScoreFor(k, cache, i.scores) ?: return@mapNotNull null
            if (s < 9 || usedRecently(t)) return@mapNotNull null
            val at = dates[(if (t.isShow) "show:" else "movie:") + (t.imdbId ?: "tmdb${t.tmdbId}")]
            t to at
        }
        val recent = loved.filter { (_, at) -> at != null && now - at < 365 * DAY_MS }
        val candidates: List<Pair<NuvioCTasteTitle, Double>> = when {
            recent.size >= 5 -> recent.map { (t, at) -> t to (1.0 + 2.0 * (1.0 - (now - at!!).toDouble() / (365 * DAY_MS))) }
            loved.isNotEmpty() -> loved.map { it.first to 1.0 }
            // no ratings: films the crowd loves, or shows you got through within a month
            else -> keys.mapNotNull { cache[it] }.filter { t ->
                !usedRecently(t) && (t.vote ?: 0.0) >= 7.8 && t.votes >= (if (t.isShow) 800 else 5_000)
            }.map { it to 1.0 }
        }
        if (candidates.isEmpty()) return null
        var r = random.nextDouble() * candidates.sumOf { it.second }
        val seed = (candidates.firstOrNull { r -= it.second; r <= 0 } ?: candidates.last()).first
        val body = withTimeoutOrNull(8_000) {
            api.recommendations(if (seed.isShow) "tv" else "movie", seed.tmdbId, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() }
        } ?: return null
        val items = discoverItems(body, if (seed.isShow) "tv" else "movie", watched).shuffled(random).take(15)
        rowPrefs.edit().putLong("p${i.profile}:seed:${seed.tmdbId}", now).apply()
        return NuvioCTasteRow("because:${seed.tmdbId}", "Because you loved ${seed.name}", items)
    }

    /** "More from X": one of your top five people, their acting (or directing) work you haven't seen. */
    private suspend fun moreFrom(stats: NuvioCTasteStats, watched: TasteSeen, random: java.util.Random): NuvioCTasteRow? {
        val person = stats.people.take(5).takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] } ?: return null
        val body = withTimeoutOrNull(8_000) {
            api.personCredits(person.person.id, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() }
        } ?: return null
        val o = json.parseToJsonElement(body).jsonObject
        val list = if (person.director) (o["crew"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.str("job") == "Director" || it.str("job") == "Creator" }
        else (o["cast"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter {
            val c = it.str("character").orEmpty().lowercase()
            c.isNotBlank() && "self" !in c && "himself" !in c && "herself" !in c
        }
        val items = list.filter { (it.int("vote_count") ?: 0) >= 50 && !watched.has(if (it.str("media_type") == "tv") "tv" else "movie", it.int("id") ?: 0) }
            .distinctBy { it.int("id") }
            .sortedByDescending { it.int("vote_count") ?: 0 }.take(20)
            .mapNotNull { r -> shelfItem(r, if (r.str("media_type") == "tv") "tv" else "movie") }
            .shuffled(random).take(15)
        return NuvioCTasteRow("person:${person.person.id}", "More from ${person.person.name}", items)
    }

    /** Well-loved titles in your top genre from one of your top three decades. */
    private suspend fun decadeGaps(stats: NuvioCTasteStats, watched: TasteSeen, random: java.util.Random): NuvioCTasteRow? {
        val decades = stats.decades.sortedByDescending { it.second }.take(3).map { it.first }
        val decade = decades.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] } ?: return null
        val genre = stats.genres.firstOrNull()?.name ?: return null
        val items = discover(genre, decade, watched, random) ?: return null
        return NuvioCTasteRow("decade:$decade", "${tasteDecadeLabel(decade).replaceFirstChar { it.uppercase() }} ${genre.lowercase()} you haven't seen", items)
    }

    /** Top-rated titles in one of your top four genres, any decade. */
    private suspend fun genreUnseen(stats: NuvioCTasteStats, watched: TasteSeen, random: java.util.Random): NuvioCTasteRow? {
        val genres = stats.genres.take(4).map { it.name }
        val genre = genres.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] } ?: return null
        val items = discover(genre, null, watched, random) ?: return null
        return NuvioCTasteRow("genre:$genre", "Top $genre you haven't seen", items)
    }

    private suspend fun discover(genre: String, decade: Int?, watched: TasteSeen, random: java.util.Random): List<NuvioCTasteShelfItem>? {
        val genreId = TMDB_MOVIE_GENRES.entries.firstOrNull { it.value.equals(genre, true) }?.key ?: return null
        val q = linkedMapOf(
            "api_key" to tmdbService.apiKey(),
            "include_adult" to "false",
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to if (decade != null && decade < 1980) "500" else "1500",
            "vote_average.gte" to "7.2",
            "with_genres" to genreId.toString(),
            "page" to (1 + random.nextInt(2)).toString()
        )
        decade?.let { q["primary_release_date.gte"] = "$it-01-01"; q["primary_release_date.lte"] = "${it + 9}-12-31" }
        val body = withTimeoutOrNull(8_000) { api.discover("movie", q).takeIf { it.isSuccessful }?.body()?.use { it.string() } } ?: return null
        return discoverItems(body, "movie", watched).shuffled(random).take(15)
    }

    private fun discoverItems(body: String, type: String, watched: TasteSeen): List<NuvioCTasteShelfItem> =
        (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { !watched.has(type, it.int("id") ?: 0) }
            .mapNotNull { shelfItem(it, type) }

    private fun shelfItem(r: JsonObject, type: String): NuvioCTasteShelfItem? {
        val id = r.int("id") ?: return null
        val name = r.str("title") ?: r.str("name") ?: return null
        val poster = r.str("poster_path") ?: return null
        return NuvioCTasteShelfItem(
            id = "tmdb:$id",
            type = if (type == "tv") "series" else "movie",
            name = name,
            poster = "https://image.tmdb.org/t/p/w342$poster",
            note = (r.str("release_date") ?: r.str("first_air_date"))?.take(4)
        )
    }

    // ---- files ----

    private fun titles(): MutableMap<String, NuvioCTasteTitle> {
        titleCache?.let { return it }
        val loaded = runCatching {
            titlesFile.takeIf { it.exists() }?.readText()?.let { json.decodeFromString<List<NuvioCTasteTitle>>(it) }
        }.getOrNull().orEmpty().associateByTo(HashMap()) { it.key }
        titleCache = loaded
        return loaded
    }

    private fun saveTitles(cache: Map<String, NuvioCTasteTitle>) = runCatching {
        val list = synchronized(cache) { cache.values.toList() }
        titlesFile.writeText(json.encodeToString(list))
    }

    /** Results are kept in memory per profile; after an app restart the first open rebuilds (title facts are cached). */
    private val lastResult = HashMap<Int, NuvioCTasteState>()

    private fun save(profile: Int, s: NuvioCTasteState) { lastResult[profile] = s }

    private fun loadSaved(profile: Int): NuvioCTasteState? = lastResult[profile]

    /** Watched keys ("movie:tt…", "tv:tt…") for the shelves. */
    suspend fun watchedKeys(): Set<String> =
        withTimeoutOrNull(5_000) { watchProgress.watchedItems.first() }.orEmpty().mapNotNull { titleRef(it)?.first }.toSet()

    companion object {
        private const val TAG = "NuvioCTaste"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val REFETCH_MS = 60L * DAY_MS
        private const val MAX_FETCH_PER_BUILD = 1_500
        internal const val TASTE_SCHEMA = 2

        internal val TMDB_MOVIE_GENRES = mapOf(
            28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
            18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
            9648 to "Mystery", 10749 to "Romance", 878 to "Sci-fi", 53 to "Thriller", 10752 to "War", 37 to "Western"
        )
    }
}

// ---- the numbers (pure, unit-tested) ----------------------------------------------------------------------

/** TMDB's genre names, shortened and merged so films and shows count together. */
internal fun tidyGenre(name: String): String = when (name.lowercase()) {
    "science fiction", "sci-fi & fantasy" -> "Sci-fi"
    "action & adventure" -> "Action"
    "war & politics" -> "War"
    "kids" -> "Family"
    else -> name
}

internal fun computeTasteStats(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    scores: Map<String, Int>,
    range: NuvioCTasteRange,
    nowMs: Long,
    zone: ZoneId,
    localOnly: Boolean = false,
    /** [fork] round 40 taste_month: an explicit window (a month) instead of [range]. */
    window: LongRange? = null
): NuvioCTasteStats {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val from = when (range) {
        NuvioCTasteRange.ALL -> Long.MIN_VALUE
        NuvioCTasteRange.YEAR -> today.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
        NuvioCTasteRange.DAYS_90 -> nowMs - 90L * 24 * 60 * 60 * 1000
    }
    val span = window ?: (from..nowMs)
    val lines = history.filter { it.watchedAt in span && (it.contentId.startsWith("tt") || it.contentId.startsWith("tmdb:")) }
    fun keyOf(w: WatchedItem): String {
        val show = w.season != null || w.contentType.equals("series", true) || w.contentType.equals("tv", true)
        val raw = w.contentId.trim().let { if (it.startsWith("tmdb:")) it else it.substringBefore(':') }
        return (if (show) "tv:" else "movie:") + raw
    }
    val byTitle = lines.groupBy(::keyOf)
    val movieKeys = byTitle.keys.filter { it.startsWith("movie:") }
    val showKeys = byTitle.keys.filter { it.startsWith("tv:") }
    val episodes = showKeys.sumOf { k -> byTitle.getValue(k).count { it.season != null } }
    val minutes = movieKeys.sumOf { titles[it]?.runtime ?: 110 } +
        showKeys.sumOf { k -> byTitle.getValue(k).count { it.season != null } * (titles[k]?.runtime ?: 40) }

    // genres: one vote per title
    val genreCounts = HashMap<String, Int>()
    byTitle.keys.forEach { k -> titles[k]?.genres?.distinct()?.forEach { g -> genreCounts[g] = (genreCounts[g] ?: 0) + 1 } }
    val genreTotal = genreCounts.values.sum().coerceAtLeast(1)
    val genres = genreCounts.entries.sortedByDescending { it.value }.take(6)
        .map { NuvioCTasteShare(it.key, it.value, (it.value * 100.0 / genreTotal).roundToInt()) }

    val decadeCounts = byTitle.keys.mapNotNull { titles[it]?.year }.groupingBy { it - it % 10 }.eachCount()
    val decades = decadeCounts.entries.sortedBy { it.key }.filter { it.key >= 1930 }.map { it.key to it.value }
    val topDecade = decadeCounts.maxByOrNull { it.value }?.key

    // people: billed cast (top 6) and directors / creators, three titles or more
    val castCount = HashMap<Int, Pair<NuvioCTastePerson, Int>>()
    val dirCount = HashMap<Int, Pair<NuvioCTastePerson, Int>>()
    byTitle.keys.forEach { k ->
        val t = titles[k] ?: return@forEach
        t.cast.forEach { p -> castCount[p.id] = p to ((castCount[p.id]?.second ?: 0) + 1) }
        t.directors.forEach { p -> dirCount[p.id] = p to ((dirCount[p.id]?.second ?: 0) + 1) }
    }
    val people = (castCount.values.map { NuvioCTastePersonStat(it.first, it.second, false) } +
        dirCount.values.map { NuvioCTastePersonStat(it.first, it.second, true) })
        .filter { it.count >= 3 }
        .sortedWith(compareByDescending<NuvioCTastePersonStat> { it.count }.thenBy { it.director })
        .distinctBy { it.person.id }
        .take(8)

    // ratings: titles in range you've rated (any time)
    fun scoreFor(k: String): Int? {
        val t = titles[k]
        val prefix = if (k.startsWith("tv:")) "show:" else "movie:"
        val raw = k.substringAfter(':')
        return scores[prefix + raw] ?: t?.imdbId?.let { scores[prefix + it] } ?: t?.tmdbId?.let { scores[prefix + "tmdb" + it] }
    }
    val ratedKeys = byTitle.keys.mapNotNull { k -> scoreFor(k)?.let { k to it } }
    val ratingSource = if (range == NuvioCTasteRange.ALL && window == null) scores.values.filter { it in 1..10 } else ratedKeys.map { it.second }
    val ratingCounts = (1..10).map { s -> ratingSource.count { it == s } }
    val diffs = ratedKeys.mapNotNull { (k, s) -> titles[k]?.vote?.let { s - it } }
    val vsCrowd = diffs.takeIf { it.size >= 10 }?.average()
    val genreCritic = genres.take(6).mapNotNull { g ->
        val d = ratedKeys.mapNotNull { (k, s) -> titles[k]?.takeIf { g.name in it.genres }?.vote?.let { s - it } }
        if (d.size >= 5) g.name to d.average() else null
    }.filter { vsCrowd != null && abs(it.second - vsCrowd) >= 0.6 }.maxByOrNull { abs(it.second - (vsCrowd ?: 0.0)) }
    val crowdAverage = byTitle.keys.mapNotNull { titles[it]?.vote }.takeIf { it.size >= 5 }?.average()

    // shows finished: started shows with a known length
    val started = showKeys.mapNotNull { k ->
        val total = titles[k]?.episodes?.takeIf { it > 0 } ?: return@mapNotNull null
        val seen = byTitle.getValue(k).mapNotNull { w -> w.season?.let { it to (w.episode ?: 0) } }.toSet().size
        k to (seen.toDouble() / total)
    }
    val finishedPercent = started.takeIf { it.size >= 5 }?.let { list -> (list.count { it.second >= 0.9 } * 100.0 / list.size).roundToInt() }

    // habits
    val days = lines.map { Instant.ofEpochMilli(it.watchedAt).atZone(zone).toLocalDate() }
    val daySet = days.toSortedSet()
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in daySet) {
        val p = prev
        run = if (p != null && ChronoUnit.DAYS.between(p, d) == 1L) run + 1 else 1
        longest = maxOf(longest, run)
        prev = d
    }
    val historyDays = if (daySet.isEmpty()) 0 else ChronoUnit.DAYS.between(daySet.first(), today).toInt() + 1
    val heatStart = today.minusDays(181)
    val perDay = days.filter { !it.isBefore(heatStart) }.groupingBy { it }.eachCount()
    val heat = (0 until 182).map { perDay[heatStart.plusDays(it.toLong())] ?: 0 }
    val stamps = lines.map { Instant.ofEpochMilli(it.watchedAt).atZone(zone) }
    val peakDay = stamps.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key?.takeIf { stamps.size >= 20 }
    val peakHour = stamps.groupingBy { it.hour }.eachCount().maxByOrNull { it.value }?.key?.takeIf { stamps.size >= 20 }

    // shelves (always from all of history)
    val lastByKey = history.groupBy(::keyOf).mapValues { (_, v) -> v.maxOf { it.watchedAt } }
    val unfinished = history.filter { it.season != null }.groupBy(::keyOf).mapNotNull { (k, v) ->
        val t = titles[k] ?: return@mapNotNull null
        val total = t.episodes?.takeIf { it > 0 } ?: return@mapNotNull null
        val seen = v.mapNotNull { w -> w.season?.let { it to (w.episode ?: 0) } }.toSet().size
        val idleDays = (nowMs - v.maxOf { it.watchedAt }) / (24L * 60 * 60 * 1000)
        if (seen >= 2 && seen < total * 0.9 && idleDays >= 45) Triple(k, t, idleDays) else null
    }.sortedBy { it.third }.take(12).map { (_, t, _) ->
        NuvioCTasteShelfItem(t.imdbId ?: "tmdb:${t.tmdbId}", "series", t.name, t.poster, null)
    }
    val threeYears = 3L * 365 * 24 * 60 * 60 * 1000
    val rewatch = lastByKey.filter { (k, at) -> k.startsWith("movie:") && nowMs - at > threeYears }.keys.mapNotNull { k ->
        val t = titles[k] ?: return@mapNotNull null
        val s = scoreFor(k)
        val strong = if (s != null) s >= 9 else (t.vote ?: 0.0) >= 8.0 && t.votes >= 2000
        if (strong) t to (s ?: 0) else null
    }.sortedByDescending { it.second }.take(12).map { (t, _) -> NuvioCTasteShelfItem(t.imdbId ?: "tmdb:${t.tmdbId}", "movie", t.name, t.poster, t.year?.toString()) }

    val backdrops = byTitle.keys.mapNotNull { k -> titles[k]?.let { t -> (scoreFor(k) ?: ((t.vote ?: 0.0) * 1).toInt()) to t.backdrop } }
        .filter { it.second != null }.sortedByDescending { it.first }.take(3).mapNotNull { it.second }

    return NuvioCTasteStats(
        range = range,
        titles = byTitle.size,
        movies = movieKeys.size,
        shows = showKeys.size,
        episodes = episodes,
        hours = (minutes / 60.0).roundToInt(),
        rated = if (range == NuvioCTasteRange.ALL && window == null) ratingSource.size else ratedKeys.size,
        finishedPercent = finishedPercent,
        longestStreak = longest,
        historyDays = historyDays,
        genres = genres,
        decades = decades,
        people = people,
        ratingCounts = ratingCounts,
        vsCrowd = vsCrowd,
        genreCritic = genreCritic,
        crowdAverage = crowdAverage,
        heat = heat,
        peakDay = peakDay,
        peakHour = peakHour,
        topDecade = topDecade,
        backdrops = backdrops,
        unfinished = unfinished,
        rewatch = rewatch,
        anyRatings = scores.isNotEmpty(),
        localOnly = localOnly,
        recentBackdrops = byTitle.entries.sortedByDescending { e -> e.value.maxOf { it.watchedAt } }
            .mapNotNull { titles[it.key]?.backdrop }.distinct().take(5),
        spinPosters = genres.firstOrNull()?.name?.let { top ->
            byTitle.keys.mapNotNull { k -> titles[k]?.takeIf { top in it.genres && it.poster != null }?.let { t -> t to (scoreFor(k)?.toDouble() ?: (t.vote ?: 0.0)) } }
                .sortedByDescending { it.second }.take(3).mapNotNull { it.first.poster?.replace("/w342/", "/w154/") }
        }.orEmpty()
    )
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** [fork] round 41 taste_review: one month's banner (pure, tested). */
internal fun computeTasteReview(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    window: LongRange,
    month: java.time.YearMonth,
    current: Boolean,
    zone: ZoneId,
    minTitles: Int = 10
): NuvioCTasteReview? {
    val lines = history.filter { it.watchedAt in window && (it.contentId.startsWith("tt") || it.contentId.startsWith("tmdb:")) }
    val byTitle = lines.groupBy(::tasteKeyOf)
    if (byTitle.size < minTitles) return null
    fun minutesOf(k: String, v: List<WatchedItem>) =
        if (k.startsWith("movie:")) titles[k]?.runtime ?: 110 else v.count { it.season != null } * (titles[k]?.runtime ?: 40)
    val minutes = byTitle.entries.sumOf { (k, v) -> minutesOf(k, v) }
    val genre = byTitle.keys.flatMap { titles[it]?.genres?.distinct().orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val most = byTitle.entries.maxByOrNull { (k, v) -> minutesOf(k, v) }?.let { titles[it.key]?.name }?.takeIf { it.isNotBlank() }
    // biggest day by watch time
    val perDay = HashMap<LocalDate, Int>()
    lines.forEach { w ->
        val k = tasteKeyOf(w)
        val m = if (k.startsWith("movie:")) titles[k]?.runtime ?: 110 else titles[k]?.runtime ?: 40
        val d = Instant.ofEpochMilli(w.watchedAt).atZone(zone).toLocalDate()
        perDay[d] = (perDay[d] ?: 0) + m
    }
    val big = perDay.maxByOrNull { it.value }
    val headline = buildString {
        append(genre?.let { "Mostly " + pluralGenre(it) } ?: "${byTitle.size} titles")
        if (big != null && big.value >= 240) append(", and a long " + big.key.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US))
    }
    val posters = byTitle.entries.sortedByDescending { e -> e.value.maxOf { it.watchedAt } }
        .mapNotNull { titles[it.key]?.poster?.replace("/w342/", "/w154/") }.distinct().take(12)
    return NuvioCTasteReview(month, current, headline, (minutes / 60.0).roundToInt(), byTitle.size, genre, most, big?.key, posters)
}

/** [fork] round 42 taste_recap_panel: one month's breakdown (pure, tested). Null when the month has no titles. */
internal fun computeTasteRecapDetail(
    history: List<WatchedItem>,
    titles: Map<String, NuvioCTasteTitle>,
    window: LongRange,
    month: java.time.YearMonth,
    zone: ZoneId
): NuvioCTasteRecapDetail? {
    val lines = history.filter { it.watchedAt in window && (it.contentId.startsWith("tt") || it.contentId.startsWith("tmdb:")) }
    val byTitle = lines.groupBy(::tasteKeyOf)
    if (byTitle.isEmpty()) return null
    fun episodesOf(v: List<WatchedItem>) = v.mapNotNull { w -> w.season?.let { it to (w.episode ?: 0) } }.toSet().size
    fun minutesOf(k: String, v: List<WatchedItem>) =
        if (k.startsWith("movie:")) titles[k]?.runtime ?: 110 else episodesOf(v) * (titles[k]?.runtime ?: 40)
    val films = byTitle.keys.count { it.startsWith("movie:") }
    val showEntries = byTitle.filterKeys { !it.startsWith("movie:") }
    val episodes = showEntries.values.sumOf { episodesOf(it) }
    val minutes = byTitle.entries.sumOf { (k, v) -> minutesOf(k, v) }
    val genreCounts = byTitle.keys.flatMap { titles[it]?.genres?.distinct().orEmpty() }.groupingBy { it }.eachCount()
    val genres = genreCounts.entries.sortedByDescending { it.value }.take(3)
        .map { it.key to ((it.value * 100.0) / byTitle.size).roundToInt().coerceIn(0, 100) }
    val most = byTitle.entries.maxByOrNull { (k, v) -> minutesOf(k, v) }?.let { titles[it.key]?.name }?.takeIf { it.isNotBlank() }
    val perDay = HashMap<LocalDate, Int>()
    lines.forEach { w ->
        val k = tasteKeyOf(w)
        val m = if (k.startsWith("movie:")) titles[k]?.runtime ?: 110 else titles[k]?.runtime ?: 40
        val d = Instant.ofEpochMilli(w.watchedAt).atZone(zone).toLocalDate()
        perDay[d] = (perDay[d] ?: 0) + m
    }
    val big = perDay.maxByOrNull { it.value }
    return NuvioCTasteRecapDetail(
        month = month, titles = byTitle.size, hours = (minutes / 60.0).roundToInt(), films = films,
        episodes = episodes, shows = showEntries.size, genres = genres, biggestDay = big?.key,
        biggestDayMinutes = big?.value ?: 0, mostWatched = most, keys = byTitle.keys.toList()
    )
}
