package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 28 `shuffle_page`: builds the Shuffle pool and makes the picks.
//
// Pool (built in the background, saved on the TV per profile, reused for 12 h):
//  - For you: TMDB recommendations for your recent finishes and your best ratings of the last few months
//    (Nuvio sync watch history + Trakt / this-TV ratings). Up to 10 seed titles, 2 pages each.
//  - Trakt: Trakt's own recommendations for you (movies + shows, 100 each).
//  - Add-on catalogs: the catalogs ticked in settings (automatic: ones that look like recommendations),
//    up to 3 pages each so picks aren't only from the top of a list.
//  - Home rows: what Home shows (first page). Library: your library list.
// A title found by several sources counts once, credited to the first in that order.
//
// A pick (see NuvioCShuffle.kt for the randomness): draw, then check the drawn title properly (IMDb id,
// watched, out to stream yet) and fetch its details from your meta add-on. A title that fails the check
// is set aside for this visit and another is drawn. While a pick is on screen the next one is prepared
// in the background, so Shuffle again is instant.

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.calendar.CalendarEntryKind
import com.nuvio.tv.core.calendar.TmdbCalendarClient
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.catalogTypeKey
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

internal interface NuvioCShuffleTraktApi {
    @GET("recommendations/{type}")
    suspend fun recommendations(
        @Header("Authorization") authorization: String,
        @Path("type") type: String,
        @Query("limit") limit: Int = 100,
        @Query("ignore_collected") ignoreCollected: Boolean = false,
        @Query("extended") extended: String = "full"
    ): Response<ResponseBody>
}

internal interface NuvioCShuffleTmdbApi {
    @GET("{type}/{id}/recommendations")
    suspend fun recommendations(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int
    ): Response<ResponseBody>

    /** [fork] round 31 roulette_anime: Japanese animation, well rated. */
    @GET("discover/{type}")
    suspend fun discoverAnime(
        @Path("type") type: String,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("sort_by") sortBy: String,
        @Query("vote_count.gte") minVotes: Int,
        @Query("vote_average.gte") minScore: Float = 6.8f,
        @Query("with_genres") genres: String = "16",
        @Query("with_original_language") language: String = "ja",
        @Query("include_adult") includeAdult: Boolean = false
    ): Response<ResponseBody>
}

/** A finished pick, with what the result screen shows. */
data class NuvioCShufflePick(
    val item: NuvioCShuffleItem,
    val runtime: String? = null
)

sealed interface NuvioCShuffleOutcome {
    data class Picked(val pick: NuvioCShufflePick) : NuvioCShuffleOutcome
    /** Nothing in the pool fits the choices (filters too tight, or every source empty). */
    data object NothingLeft : NuvioCShuffleOutcome
}

private data class Filters(val kind: NuvioCShuffleKind, val mood: NuvioCShuffleMood, val unwatched: Boolean)

@Singleton
class NuvioCShufflePool @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("trakt") traktRetrofit: Retrofit,
    @Named("tmdb") tmdbRetrofit: Retrofit,
    private val prefs: NuvioCShufflePrefs,
    private val profiles: ProfileManager,
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val watchProgress: WatchProgressRepository,
    private val metaRepository: MetaRepository,
    private val layoutPrefs: LayoutPreferenceDataStore,
    private val tmdbService: TmdbService,
    private val tmdbCalendar: TmdbCalendarClient,
    private val traktAuth: TraktAuthService,
    private val ratings: NuvioCRatingService
) {
    private val traktApi = traktRetrofit.create(NuvioCShuffleTraktApi::class.java)
    private val tmdbApi = tmdbRetrofit.create(NuvioCShuffleTmdbApi::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buildMutex = Mutex()
    private val net = Semaphore(3) // at most 3 requests at once (2 GB TV)

    @Volatile private var pool: NuvioCShufflePoolFile? = null
    @Volatile private var building: Deferred<NuvioCShufflePoolFile?>? = null

    // Per visit: titles that failed the check, and the prepared next pick.
    private val rejected = HashSet<String>()
    private var prepared: Pair<Filters, Deferred<NuvioCShuffleOutcome>>? = null
    private val pickMutex = Mutex()

    private fun file(profile: Int) = File(context.filesDir, "nuvio_c_shuffle_pool_p$profile.json")

    /** True when a usable pool is in memory (or on disk) for the current profile and settings. */
    suspend fun hasPool(): Boolean = currentPool() != null

    private suspend fun currentPool(): NuvioCShufflePoolFile? {
        val profile = profiles.activeProfileId.value
        val signature = prefs.config.value.poolSignature
        pool?.takeIf { it.profile == profile && it.signature == signature }?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(NuvioCShufflePoolFile.serializer(), file(profile).readText()) }.getOrNull()
        }?.takeIf { it.profile == profile && it.signature == signature }
        if (loaded != null) pool = loaded
        return loaded
    }

    /** Background warm-up (after start-up settles): builds the pool only when there's none or it's over 12 h old. */
    fun warmUp() {
        scope.launch { ensure(rebuildIfOlderThanMs = MAX_AGE_MS) }
    }

    /** Rebuild now (settings "Refresh picks"). */
    fun refresh() {
        scope.launch { ensure(rebuildIfOlderThanMs = 0L) }
    }

    /**
     * The pool to draw from. A stale pool is used at once and rebuilt in the background; with no pool at all
     * this waits for the build.
     */
    private suspend fun ensure(rebuildIfOlderThanMs: Long = MAX_AGE_MS): NuvioCShufflePoolFile? {
        val existing = currentPool()
        val stale = existing == null || System.currentTimeMillis() - existing.builtAt > rebuildIfOlderThanMs
        if (!stale) return existing
        val job = buildMutex.withLock {
            building?.takeIf { it.isActive } ?: scope.async { build() }.also { building = it }
        }
        return existing ?: job.await()
    }

    private suspend fun build(): NuvioCShufflePoolFile? = runCatching {
        val config = prefs.config.value
        val profile = profiles.activeProfileId.value
        val started = System.currentTimeMillis()
        val sources = NuvioCShuffleSource.entries.filter { config.weight(it) > 0 }
        val lists = coroutineScope {
            sources.map { source ->
                async {
                    source to (runCatching {
                        when (source) {
                            NuvioCShuffleSource.FOR_YOU -> forYou()
                            NuvioCShuffleSource.TRAKT -> trakt()
                            NuvioCShuffleSource.ADDON -> addonCatalogs(config.catalogKeys)
                            NuvioCShuffleSource.HOME -> homeRows()
                            NuvioCShuffleSource.LIBRARY -> library()
                        }
                    }.onFailure { Log.w(TAG, "${source.key} failed: ${it.message}") }.getOrNull())
                }
            }.awaitAll()
        }.toMap()
        // A source that failed keeps its titles from the last pool, so one bad day doesn't empty it.
        val previous = pool?.takeIf { it.profile == profile }
        val seen = HashSet<String>()
        val items = LinkedHashMap<String, List<NuvioCShuffleItem>>()
        NuvioCShuffleSource.entries.filter { it in sources }.forEach { source ->
            val list = lists[source] ?: previous?.items?.get(source.key).orEmpty()
            items[source.key] = list.filter { seen.add(it.identity) }
        }
        // [fork] round 31 roulette_anime: a list of its own for Anime (titles already in a source above keep that credit).
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_ANIME) {
            val anime = runCatching { anime() }.onFailure { Log.w(TAG, "anime failed: ${it.message}") }.getOrNull()
                ?: previous?.items?.get(ANIME_KEY).orEmpty()
            items[ANIME_KEY] = anime.filter { seen.add(it.identity) }
        }
        val file = NuvioCShufflePoolFile(profile, System.currentTimeMillis(), config.poolSignature, items)
        if (items.values.sumOf { it.size } > 0) {
            pool = file
            withContext(Dispatchers.IO) { runCatching { file(profile).writeText(json.encodeToString(NuvioCShufflePoolFile.serializer(), file)) } }
        }
        Log.i(TAG, "Pool built in ${System.currentTimeMillis() - started} ms: " + items.entries.joinToString { "${it.key}=${it.value.size}" })
        file
    }.onFailure { Log.w(TAG, "Pool build failed: ${it.message}") }.getOrNull()

    // ---- sources --------------------------------------------------------------------------------------

    private suspend fun <T> limited(block: suspend () -> T): T = net.withPermit { block() }

    private suspend fun forYou(): List<NuvioCShuffleItem> {
        val since = System.currentTimeMillis() - SEED_WINDOW_MS
        val watched = withTimeoutOrNull(6_000) { watchProgress.watchedItems.first() }.orEmpty()
            .filter { it.watchedAt >= since }
            .sortedByDescending { it.watchedAt }
        // One entry per title (episodes collapse into their show), most recent first.
        val titles = LinkedHashMap<String, Pair<Boolean, String>>() // contentId -> (isShow, id)
        watched.forEach { w ->
            val isShow = w.season != null || w.contentType.equals("series", true) || w.contentType.equals("tv", true)
            titles.putIfAbsent(w.contentId, isShow to w.contentId)
        }
        if (titles.isEmpty()) return emptyList()
        val scores = ratings.traktScores() + ratings.localScores()
        fun score(isShow: Boolean, id: String): Int? {
            val p = if (isShow) "show:" else "movie:"
            return scores[p + id] ?: id.removePrefix("tmdb:").toIntOrNull()?.let { scores[p + "tmdb" + it] }
        }
        val all = titles.values.toList()
        // [fork] round 31 roulette_v2: seeds are your 4.5 to 5 star titles of the last months first (Trakt 9-10),
        // then 4 stars, then recent finishes; each recommendation remembers which title it came from.
        val v2 = com.nuvio.tv.NuvioCFeatures.ROULETTE_V2
        val lovedFloor = if (v2) 9 else 8
        val lovedAll = all.mapNotNull { (isShow, id) -> score(isShow, id)?.takeIf { it >= 8 }?.let { Triple(isShow, id, it) } }
            .sortedByDescending { it.third }
        val loved = (lovedAll.filter { it.third >= lovedFloor } + if (v2) lovedAll.filter { it.third < lovedFloor } else emptyList())
            .take(if (v2) 8 else 6).map { it.first to it.second }
        val recent = all.filterNot { it in loved }.take(SEEDS - loved.size)
        val seeds = (loved + recent).take(SEEDS)
        val names = watched.filter { it.season == null && it.episode == null }.associate { it.contentId to it.title } // whole titles only, never an episode's name
        val seedTmdb = java.util.Collections.synchronizedSet(HashSet<String>())
        return coroutineScope {
            seeds.map { (isShow, id) ->
                async {
                    val tmdb = id.removePrefix("tmdb:").toIntOrNull()
                        ?: if (id.startsWith("tt")) limited { tmdbService.imdbToTmdb(id, if (isShow) "tv" else "movie") } else null
                    if (tmdb == null) return@async emptyList()
                    seedTmdb += "tmdb:$tmdb"
                    val title = names[id]?.takeIf { it.isNotBlank() }
                    val because = when {
                        !v2 || title == null -> null
                        score(isShow, id) != null && score(isShow, id)!! >= 8 -> "rated|$title|${score(isShow, id)}"
                        else -> "watched|$title"
                    }
                    (1..2).flatMap { page -> limited { tmdbRecommendations(isShow, tmdb, page) } }.map { it.copy(because = because) }
                }
            }.awaitAll().flatten()
        }.filterNot { item -> item.id in seedTmdb || titles.containsKey(item.id) }.distinctBy { it.identity }
    }

    private suspend fun tmdbRecommendations(isShow: Boolean, tmdbId: Int, page: Int): List<NuvioCShuffleItem> {
        val response = runCatching { tmdbApi.recommendations(if (isShow) "tv" else "movie", tmdbId, tmdbService.apiKey(), page) }.getOrNull()
        val body = response?.takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return emptyList()
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"]?.jsonArray }.getOrNull() ?: return emptyList()
        return results.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o.int("id") ?: return@mapNotNull null
            if ((o.int("vote_count") ?: 0) < MIN_TMDB_VOTES) return@mapNotNull null
            // [fork] round 31 roulette_v2: leave out the poorly rated and the barely known.
            val score = o.float("vote_average")
            if (com.nuvio.tv.NuvioCFeatures.ROULETTE_V2 && ((o.int("vote_count") ?: 0) < V2_MIN_VOTES || (score ?: 0f) < V2_MIN_SCORE)) return@mapNotNull null
            val name = o.str("title") ?: o.str("name") ?: return@mapNotNull null
            val released = o.str("release_date") ?: o.str("first_air_date")
            NuvioCShuffleItem(
                id = "tmdb:$id",
                type = if (isShow) "series" else "movie",
                name = name,
                poster = o.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                background = o.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
                description = o.str("overview"),
                year = released?.take(4),
                released = released,
                genres = (o["genre_ids"] as? JsonArray).orEmpty().mapNotNull { g -> g.jsonPrimitive.intOrNull?.let(TMDB_GENRES::get) },
                tmdbId = id,
                from = FROM_FOR_YOU,
                score = score,
                anime = o.isAnime()
            )
        }
    }

    // [fork] round 31 roulette_anime: well-rated Japanese animation, films and series, most loved and most popular.
    private suspend fun anime(): List<NuvioCShuffleItem> = coroutineScope {
        listOf(
            Triple("movie", "vote_average.desc", 400), Triple("movie", "popularity.desc", 200),
            Triple("tv", "vote_average.desc", 250), Triple("tv", "popularity.desc", 150)
        ).map { (type, sort, votes) ->
            async {
                (1..2).flatMap { page ->
                    limited {
                        val response = runCatching { tmdbApi.discoverAnime(type, tmdbService.apiKey(), page, sort, votes) }.getOrNull()
                        val body = response?.takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@limited emptyList()
                        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"]?.jsonArray }.getOrNull().orEmpty()
                        results.mapNotNull { element ->
                            val o = element as? JsonObject ?: return@mapNotNull null
                            val id = o.int("id") ?: return@mapNotNull null
                            val name = o.str("title") ?: o.str("name") ?: return@mapNotNull null
                            val released = o.str("release_date") ?: o.str("first_air_date")
                            NuvioCShuffleItem(
                                id = "tmdb:$id",
                                type = if (type == "tv") "series" else "movie",
                                name = name,
                                poster = o.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                                background = o.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
                                description = o.str("overview"),
                                year = released?.take(4),
                                released = released,
                                genres = (o["genre_ids"] as? JsonArray).orEmpty().mapNotNull { g -> g.jsonPrimitive.intOrNull?.let(TMDB_GENRES::get) },
                                tmdbId = id,
                                from = FROM_ANIME,
                                score = o.float("vote_average"),
                                anime = true
                            )
                        }
                    }
                }
            }
        }.awaitAll().flatten().distinctBy { it.identity }
    }

    private suspend fun trakt(): List<NuvioCShuffleItem> = coroutineScope {
        listOf("movies" to false, "shows" to true).map { (path, isShow) ->
            async {
                val response = limited { traktAuth.executeAuthorizedRequest { traktApi.recommendations(it, path) } }
                val body = response?.takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@async null
                runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()?.mapNotNull { element ->
                    val o = (element as? JsonObject)?.let { (it["movie"] ?: it["show"]) as? JsonObject ?: it } ?: return@mapNotNull null
                    val ids = o["ids"] as? JsonObject
                    val imdb = ids?.str("imdb")
                    val tmdb = ids?.int("tmdb")
                    val id = imdb ?: tmdb?.let { "tmdb:$it" } ?: return@mapNotNull null
                    val released = o.str("released") ?: o.str("first_aired")?.take(10)
                    NuvioCShuffleItem(
                        id = id,
                        type = if (isShow) "series" else "movie",
                        name = o.str("title") ?: return@mapNotNull null,
                        description = o.str("overview"),
                        year = o.int("year")?.toString() ?: released?.take(4),
                        released = released,
                        genres = (o["genres"] as? JsonArray).orEmpty().mapNotNull { g -> g.jsonPrimitive.contentOrNull?.let(::normalizeShuffleGenre) },
                        imdbId = imdb,
                        tmdbId = tmdb,
                        from = FROM_TRAKT,
                        score = o.float("rating"),
                        anime = (o["genres"] as? JsonArray).orEmpty().any { g -> g.jsonPrimitive.contentOrNull == "anime" } ||
                            (o.str("language") == "ja" && (o["genres"] as? JsonArray).orEmpty().any { g -> g.jsonPrimitive.contentOrNull == "animation" })
                    )
                }
            }
        }.awaitAll().let { results ->
            // Both calls failing (not connected / Trakt down) counts as a failed source.
            if (results.all { it == null }) throw IllegalStateException("Trakt unavailable")
            results.filterNotNull().flatten()
        }
    }

    private suspend fun addons(): List<Addon> =
        withTimeoutOrNull(5_000) { addonRepository.getInstalledAddons().first() }.orEmpty()

    /** Catalogs that can be fetched without input (no required extras, not search-only). */
    private fun CatalogDescriptor.browsable() = extraRequired.isEmpty() && extra.none { it.isRequired } && !isSearchOnlyCatalog()

    private suspend fun addonCatalogs(chosen: Set<String>?): List<NuvioCShuffleItem> {
        val addons = addons()
        val catalogs = addons.flatMap { addon -> addon.catalogs.filter { it.browsable() }.map { addon to it } }
        val picked = if (chosen != null) {
            catalogs.filter { (addon, catalog) -> nuvioCShuffleCatalogKey(addon, catalog) in chosen }
        } else {
            catalogs.filter { (addon, catalog) -> looksLikeRecommendations(addon, catalog) }
                .ifEmpty { homeCatalogs(addons) }
        }
        return fetchCatalogs(picked, pages = 3, from = null)
    }

    private suspend fun homeRows(): List<NuvioCShuffleItem> = fetchCatalogs(homeCatalogs(addons()), pages = 1, from = FROM_HOME)

    private suspend fun homeCatalogs(addons: List<Addon>): List<Pair<Addon, CatalogDescriptor>> {
        val disabled = withTimeoutOrNull(3_000) { layoutPrefs.disabledHomeCatalogKeys.first() }.orEmpty().toSet()
        return addons.filter { it.enabled }.flatMap { addon ->
            addon.catalogs.filter { catalog ->
                catalog.browsable() && (!catalog.hasExplicitShowInHome || catalog.showInHome) &&
                    "${addon.baseUrl}_${catalogTypeKey(catalog.apiType)}_${catalog.id}_${catalog.name}" !in disabled &&
                    "${addon.id}_${catalogTypeKey(catalog.apiType)}_${catalog.id}" !in disabled
            }.map { addon to it }
        }
    }

    private suspend fun fetchCatalogs(catalogs: List<Pair<Addon, CatalogDescriptor>>, pages: Int, from: String?): List<NuvioCShuffleItem> =
        coroutineScope {
            catalogs.take(MAX_CATALOGS).map { (addon, catalog) ->
                async {
                    val step = catalog.skipStep()
                    val supportsSkip = catalog.supportsExtra("skip")
                    val out = ArrayList<NuvioCShuffleItem>()
                    for (page in 0 until (if (supportsSkip) pages else 1)) {
                        val result = limited {
                            withTimeoutOrNull(15_000) {
                                catalogRepository.getCatalog(
                                    addonBaseUrl = addon.baseUrl,
                                    addonId = addon.id,
                                    addonName = addon.displayName,
                                    catalogId = catalog.id,
                                    catalogName = catalog.name,
                                    type = catalog.apiType,
                                    skip = page * step,
                                    skipStep = step,
                                    supportsSkip = supportsSkip
                                ).first { it !is NetworkResult.Loading }
                            }
                        }
                        val items = (result as? NetworkResult.Success)?.data?.items.orEmpty()
                        if (items.isEmpty()) break
                        items.mapNotNullTo(out) { it.toShuffleItem(from ?: catalog.name, addon.baseUrl) }
                        if (items.size < step / 2) break
                    }
                    out
                }
            }.awaitAll().flatten()
        }

    private suspend fun library(): List<NuvioCShuffleItem> =
        withTimeoutOrNull(6_000) { libraryRepository.libraryItems.first() }.orEmpty().mapNotNull { e ->
            val isShow = e.type.equals("series", true) || e.type.equals("tv", true)
            if (!isShow && !e.type.equals("movie", true)) return@mapNotNull null
            NuvioCShuffleItem(
                id = e.id,
                type = if (isShow) "series" else "movie",
                name = e.name,
                poster = e.poster,
                background = e.background,
                logo = e.logo,
                description = e.description,
                year = e.releaseInfo?.take(4),
                genres = e.genres.map(::normalizeShuffleGenre),
                imdbId = e.imdbId ?: e.id.takeIf { it.startsWith("tt") },
                tmdbId = e.tmdbId,
                from = FROM_LIBRARY,
                addonBaseUrl = e.addonBaseUrl
            )
        }

    private fun MetaPreview.toShuffleItem(from: String, addonBaseUrl: String): NuvioCShuffleItem? {
        val t = apiType.lowercase()
        val isShow = t == "series" || t == "tv"
        if (!isShow && t != "movie") return null
        return NuvioCShuffleItem(
            id = id,
            type = if (isShow) "series" else "movie",
            name = name,
            poster = poster ?: rawPosterUrl,
            background = background,
            logo = logo,
            description = description,
            year = releaseInfo?.take(4),
            released = released?.take(10),
            genres = genres.map(::normalizeShuffleGenre),
            imdbId = imdbId ?: id.takeIf { it.startsWith("tt") },
            tmdbId = id.removePrefix("tmdb:").takeIf { id.startsWith("tmdb:") }?.toIntOrNull(),
            from = from,
            addonBaseUrl = addonBaseUrl
        )
    }

    /** The catalogs shown in settings for the add-on source, with their keys. */
    suspend fun browsableCatalogs(): List<Triple<String, String, String>> = addons().flatMap { addon ->
        addon.catalogs.filter { it.browsable() }.map { catalog ->
            Triple(nuvioCShuffleCatalogKey(addon, catalog), catalog.name, addon.displayName)
        }
    }.distinctBy { it.first }

    // ---- picking ------------------------------------------------------------------------------------

    /** A new visit to Shuffle: forget titles set aside last time and any prepared pick. */
    fun beginVisit() {
        rejected.clear()
        prepared?.second?.cancel()
        prepared = null
    }

    /** Posters for the mood tiles and the reel strip, from the pool (no requests). */
    suspend fun posters(kind: NuvioCShuffleKind, count: Int): List<String> {
        val all = (currentPool() ?: return emptyList()).items
            .filterKeys { kind == NuvioCShuffleKind.ANIME || it != ANIME_KEY } // [fork] roulette_anime
            .values.flatten()
            .filter { nuvioCKindMatches(it, kind) && !it.poster.isNullOrBlank() }
        if (all.isEmpty()) return emptyList()
        return List(minOf(count * 3, all.size)) { all[shuffleRandom.nextInt(all.size)].poster!! }.distinct().take(count)
    }

    /** One pick for these choices. Uses the prepared pick when it was made for the same choices. */
    suspend fun roll(kind: NuvioCShuffleKind, mood: NuvioCShuffleMood, unwatched: Boolean): NuvioCShuffleOutcome {
        val filters = Filters(kind, mood, unwatched)
        val ready = pickMutex.withLock {
            prepared?.takeIf { it.first == filters }?.second.also { prepared = null }
        }
        val outcome = ready?.let { runCatching { it.await() }.getOrNull() }?.takeIf { it is NuvioCShuffleOutcome.Picked }
            ?: pick(filters)
        if (outcome is NuvioCShuffleOutcome.Picked) {
            prefs.addRecent(outcome.pick.item.identity)
            pickMutex.withLock {
                prepared?.second?.cancel()
                prepared = filters to scope.async { pick(filters) }
            }
        }
        return outcome
    }

    private suspend fun pick(filters: Filters): NuvioCShuffleOutcome {
        val pool = ensure() ?: return NuvioCShuffleOutcome.NothingLeft
        val config = prefs.config.value
        val recent = prefs.recent().toHashSet()
        val watched = if (filters.unwatched) watchedIds() else emptySet()
        repeat(MAX_TRIES) {
            val skip = synchronized(rejected) { recent + rejected }
            fun keep(item: NuvioCShuffleItem) = nuvioCKindMatches(item, filters.kind) && nuvioCMoodMatches(item, filters.mood) &&
                item.identity !in skip && item.ids().none { it in watched }
            val (_, item) = if (filters.kind == NuvioCShuffleKind.ANIME) {
                // [fork] roulette_anime: anime from every source plus the anime list, one pot (no source weights).
                val pot = pool.items.values.flatten().filter(::keep).distinctBy { it.identity }
                nuvioCDraw(mapOf(NuvioCShuffleSource.FOR_YOU to pot), { 1 }, shuffleRandom)
            } else {
                val eligible = NuvioCShuffleSource.entries.associateWith { source -> pool.items[source.key].orEmpty().filter(::keep) }
                nuvioCDraw(eligible, config::weight, shuffleRandom)
            } ?: return NuvioCShuffleOutcome.NothingLeft
            val checked = runCatching { check(item, watched) }.getOrNull()
            if (checked != null) return NuvioCShuffleOutcome.Picked(checked)
            synchronized(rejected) { rejected += item.identity }
        }
        return NuvioCShuffleOutcome.NothingLeft
    }

    /** Watched films and started shows (every id form), read fresh for each pick. */
    private suspend fun watchedIds(): Set<String> = withContext(Dispatchers.Default) {
        val out = HashSet<String>()
        withTimeoutOrNull(3_000) { watchProgress.observeWatchedMovieIds().first() }?.let(out::addAll)
        runCatching { watchProgress.getWatchedShowEpisodes().keys }.getOrNull()?.let(out::addAll)
        runCatching { watchProgress.getShowIdSiblings() }.getOrNull()?.forEach { (k, v) -> if (k in out) out.addAll(v) }
        withTimeoutOrNull(3_000) { watchProgress.allProgress.first() }?.forEach { p ->
            if (p.contentType.equals("series", true) || p.season != null) out += p.contentId
        }
        out
    }

    /**
     * Checks a drawn title and fills in its details. Null = set it aside (watched under another id,
     * not out to stream yet, or its details can't be found).
     */
    private suspend fun check(item: NuvioCShuffleItem, watched: Set<String>): NuvioCShufflePick? {
        val mediaType = if (item.isShow) "tv" else "movie"
        val imdb = item.imdbId ?: item.tmdbId?.let { limited { tmdbService.tmdbToImdb(it, mediaType) } }
        if (imdb != null && imdb in watched) return null
        val openId = imdb ?: item.id
        if (!isOut(item, imdb)) return null
        val meta = withTimeoutOrNull(6_000) {
            metaRepository.getMetaFromPrimaryAddon(item.type, openId).first { it !is NetworkResult.Loading }
        }
        val m = (meta as? NetworkResult.Success)?.data
        if (m == null && item.poster == null && item.description == null) return null
        val filled = item.copy(
            id = openId,
            imdbId = imdb ?: m?.imdbId,
            name = m?.name?.takeIf { it.isNotBlank() } ?: item.name,
            poster = m?.poster ?: item.poster,
            background = m?.background ?: item.background,
            logo = m?.logo ?: item.logo,
            description = m?.description?.takeIf { it.isNotBlank() } ?: item.description,
            year = m?.releaseInfo?.take(4) ?: item.year,
            genres = m?.genres?.takeIf { it.isNotEmpty() }?.map(::normalizeShuffleGenre) ?: item.genres
        )
        return NuvioCShufflePick(filled, runtime = m?.runtime)
    }

    /**
     * Shows: first aired by today. Films: anything over 9 months old counts as out; newer films need a
     * digital release date (TMDB) on or before today, so cinema-only films are skipped.
     */
    private suspend fun isOut(item: NuvioCShuffleItem, imdb: String?): Boolean {
        val today = LocalDate.now()
        val date = item.released?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        if (item.isShow) return date == null || !date.isAfter(today)
        val year = item.year?.toIntOrNull()
        if (date != null && date.isBefore(today.minusDays(OUT_AFTER_DAYS))) return true
        if (date == null && year != null && year < today.year - 1) return true
        if (date != null && date.isAfter(today)) return false
        val tmdb = item.tmdbId ?: imdb?.let { limited { tmdbService.imdbToTmdb(it, "movie") } } ?: return date == null
        val region = Locale.getDefault().country.ifBlank { "US" }
        val dates = runCatching { limited { tmdbCalendar.film(tmdb.toLong(), region) } }.getOrNull() ?: return true
        val digital = dates.entries.firstOrNull { it.kind == CalendarEntryKind.MOVIE_DIGITAL }?.airDate
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        return digital != null && !digital.isAfter(today)
    }

    companion object {
        private const val TAG = "NuvioCShuffle"
        const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
        private const val SEED_WINDOW_MS = 120L * 24 * 60 * 60 * 1000
        private const val SEEDS = 10
        private const val MIN_TMDB_VOTES = 10
        private const val V2_MIN_VOTES = 150 // [fork] round 31 roulette_v2
        private const val V2_MIN_SCORE = 6.3f // [fork] round 31 roulette_v2
        const val ANIME_KEY = "anime" // [fork] round 31 roulette_anime
        const val FROM_ANIME = "Anime" // [fork] round 31 roulette_anime
        private const val MAX_CATALOGS = 24
        private const val MAX_TRIES = 8
        private const val OUT_AFTER_DAYS = 270L
        const val FROM_FOR_YOU = "For you"
        const val FROM_TRAKT = "Trakt"
        const val FROM_HOME = "Home"
        const val FROM_LIBRARY = "Library"

        private val TMDB_GENRES = mapOf(
            28 to "action", 12 to "adventure", 16 to "animation", 35 to "comedy", 80 to "crime", 99 to "documentary",
            18 to "drama", 10751 to "family", 14 to "fantasy", 36 to "history", 27 to "horror", 10402 to "music",
            9648 to "mystery", 10749 to "romance", 878 to "science fiction", 10770 to "tv movie", 53 to "thriller",
            10752 to "war", 37 to "western", 10759 to "action & adventure", 10762 to "kids", 10763 to "news",
            10764 to "reality", 10765 to "sci fi & fantasy", 10766 to "soap", 10767 to "talk", 10768 to "war & politics"
        )

        private val RECOMMENDATION_WORDS = listOf("recommend", "for you", "foryou", "because", "watchly", "similar", "picked")

        fun looksLikeRecommendations(addon: Addon, catalog: CatalogDescriptor): Boolean {
            val text = listOf(addon.id, addon.name, catalog.id, catalog.name).joinToString(" ").lowercase()
            return RECOMMENDATION_WORDS.any { it in text }
        }
    }
}

fun nuvioCShuffleCatalogKey(addon: Addon, catalog: CatalogDescriptor): String = "${addon.baseUrl}|${catalog.apiType}|${catalog.id}"

private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.int(key: String): Int? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
private fun JsonObject.float(key: String): Float? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toFloatOrNull()

/** [fork] round 31 roulette_anime: a TMDB result that is Japanese animation. */
private fun JsonObject.isAnime(): Boolean =
    str("original_language") == "ja" && (this["genre_ids"] as? JsonArray).orEmpty().any { it.jsonPrimitive.intOrNull == 16 }
