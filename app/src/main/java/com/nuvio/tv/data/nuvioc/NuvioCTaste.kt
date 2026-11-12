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
    val fetchedAt: Long = 0L
)

enum class NuvioCTasteRange { ALL, YEAR, DAYS_90 }

data class NuvioCTasteShare(val name: String, val count: Int, val percent: Int)
data class NuvioCTastePersonStat(val person: NuvioCTastePerson, val count: Int, val director: Boolean)
data class NuvioCTasteShelfItem(val id: String, val type: String, val name: String, val poster: String?, val note: String? = null)

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
    val localOnly: Boolean = false
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
    val builtAt: Long = 0L
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
}

// ---- repository ------------------------------------------------------------------------------------------

@Singleton
class NuvioCTasteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("tmdb") tmdbRetrofit: Retrofit,
    private val tmdbService: TmdbService,
    private val watchProgress: WatchProgressRepository,
    private val ratings: NuvioCRatingService,
    private val profiles: ProfileManager
) {
    private val api = tmdbRetrofit.create(NuvioCTasteTmdbApi::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }
    private val buildMutex = Mutex()
    private val titlesFile get() = File(context.filesDir, "nuvio_c_taste_titles.json")
    @Volatile private var titleCache: MutableMap<String, NuvioCTasteTitle>? = null

    private val _state = MutableStateFlow(NuvioCTasteState())
    val state: StateFlow<NuvioCTasteState> = _state.asStateFlow()

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
        val wanted = history.mapNotNull { titleRef(it) }.distinctBy { it.first }
        val cache = titles()
        val missing = wanted.filter { (key, _) -> cache[key]?.let { System.currentTimeMillis() - it.fetchedAt < REFETCH_MS } != true }
        if (!quiet) _state.value = _state.value.copy(pending = missing.size)
        if (missing.isNotEmpty()) fetchTitles(missing.take(MAX_FETCH_PER_BUILD), cache, quiet)
        val all = NuvioCTasteRange.entries.associateWith {
            computeTasteStats(history, cache, scores, it, System.currentTimeMillis(), ZoneId.systemDefault(), localOnly)
        }
        return NuvioCTasteState(profileId = profile, loading = false, pending = 0, all = all, builtAt = System.currentTimeMillis())
    }

    /** ("movie:tt123" or "tv:tt123" / "tv:tmdb:123", isShow) for a history line; null for ids TMDB can't place. */
    private fun titleRef(item: WatchedItem): Pair<String, Boolean>? {
        val id = item.contentId.trim()
        if (!id.startsWith("tt") && !id.startsWith("tmdb:")) return null
        val show = item.season != null || item.contentType.equals("series", true) || item.contentType.equals("tv", true)
        return (if (show) "tv:" else "movie:") + id.substringBefore(':').let { if (id.startsWith("tmdb:")) id else it } to show
    }

    private suspend fun fetchTitles(refs: List<Pair<String, Boolean>>, cache: MutableMap<String, NuvioCTasteTitle>, quiet: Boolean) = coroutineScope {
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
            if (!quiet) _state.value = _state.value.copy(pending = left.coerceAtLeast(0))
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
            fetchedAt = System.currentTimeMillis()
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
    localOnly: Boolean = false
): NuvioCTasteStats {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val from = when (range) {
        NuvioCTasteRange.ALL -> Long.MIN_VALUE
        NuvioCTasteRange.YEAR -> today.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
        NuvioCTasteRange.DAYS_90 -> nowMs - 90L * 24 * 60 * 60 * 1000
    }
    val lines = history.filter { it.watchedAt in from..nowMs && (it.contentId.startsWith("tt") || it.contentId.startsWith("tmdb:")) }
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
    val ratingSource = if (range == NuvioCTasteRange.ALL) scores.values.filter { it in 1..10 } else ratedKeys.map { it.second }
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
        rated = if (range == NuvioCTasteRange.ALL) ratingSource.size else ratedKeys.size,
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
        localOnly = localOnly
    )
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
