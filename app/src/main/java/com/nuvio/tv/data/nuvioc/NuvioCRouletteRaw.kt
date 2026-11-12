package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 34 `roulette_raw`: Roulette as a genuine shuffle of TMDB instead of a personal blend.
// Every eligible title (films or shows above a quality floor, already released) has a near-equal chance:
// one discover query for the choices gives the total; a random page of it, then a random title on that
// page. When a query is larger than TMDB lets you page through (10,000 results), it is split by decade and
// a decade is chosen in proportion to its size first. The same pool for every profile; the chips (type,
// mood) become filters on that query, so narrowing never shrinks it to a handful. Titles shown in the last
// 30 days are skipped. Totals are cached 24 h, so a roll is usually a single request.

import android.content.Context
import com.nuvio.tv.core.tmdb.TmdbService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap
import java.security.SecureRandom
import java.time.LocalDate

internal interface NuvioCRouletteTmdbApi {
    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @QueryMap query: Map<String, String>): Response<ResponseBody>
}

internal class NuvioCRouletteRaw(
    private val context: Context,
    private val api: NuvioCRouletteTmdbApi,
    private val tmdbService: TmdbService,
    private val genreName: (Int) -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val random = SecureRandom()
    private val totals = HashMap<String, Pair<Long, Int>>() // query key -> (fetched at, total results)
    private val totalsMutex = Mutex()
    private val recentItems = ArrayDeque<NuvioCShuffleItem>() // for the reel / mood tile posters

    /** One random eligible title for these choices, or null when nothing matches. */
    suspend fun candidate(
        kind: NuvioCShuffleKind,
        mood: NuvioCShuffleMood,
        refine: NuvioCRouletteRefine = NuvioCRouletteRefine(pool = NuvioCRoulettePool.CURATED)
    ): NuvioCShuffleItem? {
        val r = refine.effective()
        val types = when (kind) {
            NuvioCShuffleKind.MOVIES -> listOf("movie")
            NuvioCShuffleKind.SHOWS -> listOf("tv")
            NuvioCShuffleKind.EITHER, NuvioCShuffleKind.ANIME -> listOf("movie", "tv").shuffled(random)
            NuvioCShuffleKind.EPISODES -> return null // [fork] round 37: episodes come from your own shows (NuvioCRouletteEpisodes)
        }
        for (type in types) {
            val anime = kind == NuvioCShuffleKind.ANIME
            if (r.pool == NuvioCRoulettePool.CURATED && r.active == 0) {
                val query = query(type, mood, anime, r, null) ?: continue
                draw(type, query)?.let { return it }
            } else {
                drawSplit(type, mood, anime, r)?.let { return it }
            }
        }
        return null
    }

    /** [fork] round 36 roulette_copy: how many titles these choices draw from (TMDB totals, cached 24 h). */
    suspend fun poolSize(
        kind: NuvioCShuffleKind,
        mood: NuvioCShuffleMood,
        refine: NuvioCRouletteRefine = NuvioCRouletteRefine(pool = NuvioCRoulettePool.CURATED)
    ): Int? {
        val r = refine.effective()
        val types = when (kind) {
            NuvioCShuffleKind.MOVIES -> listOf("movie")
            NuvioCShuffleKind.SHOWS -> listOf("tv")
            NuvioCShuffleKind.EPISODES -> return null
            else -> listOf("movie", "tv")
        }
        var sum = 0
        var any = false
        for (type in types) {
            val anime = kind == NuvioCShuffleKind.ANIME
            if (r.pool == NuvioCRoulettePool.CURATED && r.active == 0) {
                val q = query(type, mood, anime, r, null) ?: continue
                total(type, q)?.let { sum += it; any = true }
            } else {
                val parts = sliceTotals(type, mood, anime, r) ?: continue
                sum += parts.sumOf { it.second }; any = true
            }
        }
        return sum.takeIf { any && it > 0 }
    }

    /** Posters seen recently (no requests); empty until a roll or a warm-up has fetched a page. */
    fun posters(kind: NuvioCShuffleKind, count: Int): List<String> = synchronized(recentItems) {
        recentItems.filter { nuvioCKindMatches(it, kind) && !it.poster.isNullOrBlank() }
            .shuffled(random).mapNotNull { it.poster }.distinct().take(count)
    }

    suspend fun warmUp(refine: NuvioCRouletteRefine = NuvioCRouletteRefine(pool = NuvioCRoulettePool.CURATED)) {
        runCatching { candidate(NuvioCShuffleKind.MOVIES, NuvioCShuffleMood.ANY, refine) }
        runCatching { candidate(NuvioCShuffleKind.SHOWS, NuvioCShuffleMood.ANY, refine) }
    }

    /** Total titles for the plain film and show queries (for the settings "Refreshed" line). */
    suspend fun clearAndCount(): Int {
        totalsMutex.withLock { totals.clear() }
        var sum = 0
        for (type in listOf("movie", "tv")) {
            val q = query(type, NuvioCShuffleMood.ANY, false, NuvioCRouletteRefine(pool = NuvioCRoulettePool.CURATED), null) ?: continue
            sum += total(type, q) ?: 0
        }
        return sum
    }

    // ---- 30-day memory -------------------------------------------------------------------------------
    private val seenPrefs by lazy { context.getSharedPreferences("nuvio_c_roulette_seen", Context.MODE_PRIVATE) }

    fun seenRecently(identity: String): Boolean {
        val at = seenPrefs.getLong(identity, 0L)
        return at > 0L && System.currentTimeMillis() - at < SEEN_MS
    }

    fun markSeen(identity: String) {
        val now = System.currentTimeMillis()
        val editor = seenPrefs.edit().putLong(identity, now)
        val all = seenPrefs.all
        if (all.size > SEEN_CAP) {
            all.entries.filter { (it.value as? Long ?: 0L) < now - SEEN_MS }.forEach { editor.remove(it.key) }
        }
        editor.apply()
    }

    // ---- TMDB ----------------------------------------------------------------------------------------
    /**
     * One discover query. [decadeStart] picks the vote floor for that decade (older titles need fewer votes); null =
     * the recent floor. Refine filters (decade range, region, length, acclaim) are added on top; null = no match possible.
     */
    private fun query(type: String, mood: NuvioCShuffleMood, anime: Boolean, refine: NuvioCRouletteRefine, decadeStart: Int?): Map<String, String>? {
        val today = LocalDate.now().toString()
        val movie = type == "movie"
        var (minVotes, minScore) = refine.pool.floor(type, anime, decadeStart)
        var maxVotes: Int? = null
        var acclaimTo: String? = null
        when (refine.acclaim) {
            NuvioCRouletteAcclaim.ANY -> Unit
            NuvioCRouletteAcclaim.CROWD -> { minVotes = maxOf(minVotes, if (movie) 10_000 else 3_000); minScore = maxOf(minScore, if (movie) 7.0f else 7.5f) }
            NuvioCRouletteAcclaim.TOP -> { minVotes = maxOf(minVotes, if (movie) 5_000 else 1_000); minScore = maxOf(minScore, if (movie) 8.0f else 8.3f) }
            NuvioCRouletteAcclaim.GEMS -> { maxVotes = if (movie) 2_000 else 800; minScore = maxOf(minScore, if (movie) 7.5f else 8.0f) }
            NuvioCRouletteAcclaim.CULT -> { minVotes = maxOf(minVotes, if (movie) 1_000 else 200); minScore = maxOf(minScore, if (movie) 7.2f else 7.8f); acclaimTo = if (movie) "1999-12-31" else "2005-12-31" }
        }
        if (maxVotes != null && maxVotes < minVotes) return null
        val q = linkedMapOf(
            "api_key" to tmdbService.apiKey(),
            "include_adult" to "false",
            "sort_by" to "vote_count.desc",
            "vote_count.gte" to minVotes.toString(),
            "vote_average.gte" to minScore.toString()
        )
        maxVotes?.let { q["vote_count.lte"] = it.toString() }
        val dateKey = if (movie) "primary_release_date" else "first_air_date"
        if (movie) q["include_video"] = "false" else q["without_genres"] = TV_EXCLUDED
        // date range: today, the Decade choice and Cult classics' cut-off, whichever is tightest
        val lte = listOfNotNull(today, refine.decade.to?.let { "$it-12-31" }, acclaimTo).min()
        q["$dateKey.lte"] = lte
        refine.decade.from?.let { q["$dateKey.gte"] = "$it-01-01" }
        if ((q["$dateKey.gte"] ?: "") > lte) return null
        refine.region.countries?.let { q["with_origin_country"] = it }
        val (rMin, rMax) = if (movie) refine.length.movieMin to refine.length.movieMax else refine.length.tvMin to refine.length.tvMax
        rMin?.let { q["with_runtime.gte"] = it.toString() }
        rMax?.let { q["with_runtime.lte"] = it.toString() }
        // [fork] round 39 roulette_refine_more: theme (keywords, AND with the mood's), audience (films), status (shows)
        val themeKeyword = refine.theme.keyword
        when (refine.audience) {
            NuvioCRouletteAudience.ANY -> Unit
            NuvioCRouletteAudience.FAMILY -> if (movie) { q["certification_country"] = "US"; q["certification.lte"] = "PG" }
            NuvioCRouletteAudience.ADULTS -> if (movie) { q["certification_country"] = "US"; q["certification"] = "R|NC-17" }
        }
        if (!movie) when (refine.status) {
            NuvioCRouletteStatus.ANY -> Unit
            NuvioCRouletteStatus.ONGOING -> q["with_status"] = "0"
            NuvioCRouletteStatus.ENDED -> q["with_status"] = "3|4"
            NuvioCRouletteStatus.MINISERIES -> q["with_type"] = "2"
        }
        val warGenre = if (refine.theme == NuvioCRouletteTheme.WAR) (if (movie) "10752" else "10768") else null
        if (mood == NuvioCShuffleMood.TRUE_STORY && com.nuvio.tv.NuvioCFeatures.ROULETTE_TRUE_STORY_TAG && !anime) {
            q["with_keywords"] = listOfNotNull(TRUE_STORY_KEYWORD, themeKeyword).joinToString(",") // [fork] round 37: TMDB "based on true story" (was the History genre)
            warGenre?.let { q["with_genres"] = it }
            return q
        }
        themeKeyword?.let { q["with_keywords"] = it }
        val genres = if (anime) "16" else moodGenres(type, mood)
        if (genres == null) return null
        val withGenres = when {
            warGenre == null -> genres
            genres.isEmpty() -> warGenre
            else -> "$genres,$warGenre" // mood (any of) and war
        }
        if (withGenres.isNotEmpty()) q["with_genres"] = withGenres
        if (anime) q["with_original_language"] = "ja"
        return q
    }

    /** TMDB genre ids for a mood ("|" = any of them); "" = no filter; null = that type has no such genre. */
    private fun moodGenres(type: String, mood: NuvioCShuffleMood): String? {
        val movie = type == "movie"
        return when (mood) {
            NuvioCShuffleMood.ANY -> ""
            NuvioCShuffleMood.FEEL_GOOD -> if (movie) "35|10751|10749|16" else "35|10751|16"
            NuvioCShuffleMood.GRIPPING -> if (movie) "53|28|80" else "10759|80"
            NuvioCShuffleMood.FUNNY -> "35"
            NuvioCShuffleMood.DARK -> if (movie) "27|80|10752" else "80|9648|10768"
            NuvioCShuffleMood.MIND_BENDING -> if (movie) "878|9648" else "10765|9648"
            NuvioCShuffleMood.ROMANTIC -> if (movie) "10749" else null
            NuvioCShuffleMood.ANIMATED -> "16"
            NuvioCShuffleMood.TRUE_STORY -> if (movie) "36" else "10768|99"
            NuvioCShuffleMood.WESTERN -> "37"
            NuvioCShuffleMood.DOCUMENTARY -> "99"
            NuvioCShuffleMood.ADVENTURE -> if (movie) "12" else "10759"
            NuvioCShuffleMood.FAMILY -> if (movie) "10751" else "10751|10762"
            NuvioCShuffleMood.MUSIC -> if (movie) "10402" else null
        }
    }

    private suspend fun draw(type: String, base: Map<String, String>): NuvioCShuffleItem? {
        val total = total(type, base) ?: return null
        if (total <= 0) return null
        val q = if (total <= MAX_REACHABLE) base else decadeQuery(type, base) ?: base
        val reachable = (if (q === base) total else total(type, q) ?: total).coerceAtMost(MAX_REACHABLE)
        if (reachable <= 0) return null
        val index = random.nextInt(reachable) // every reachable title equally likely
        val page = index / PAGE_SIZE + 1
        val results = fetch(type, q + ("page" to page.toString()))?.second ?: return null
        val items = results.mapNotNull { it.toItem(type) }
        if (items.isEmpty()) return null
        synchronized(recentItems) {
            items.forEach { recentItems.addLast(it) }
            while (recentItems.size > RECENT_CAP) recentItems.removeFirst()
        }
        return items.getOrNull(index % PAGE_SIZE) ?: items[random.nextInt(items.size)]
    }

    /**
     * [fork] round 37: Wide / Deep pools and refined spins. The range is cut into decades (each with its own vote floor,
     * so classics aren't squeezed out by today's vote counts), a decade is chosen in proportion to its size, and a
     * decade too big to page through is cut into single years the same way. Every title stays equally likely.
     */
    private suspend fun drawSplit(type: String, mood: NuvioCShuffleMood, anime: Boolean, refine: NuvioCRouletteRefine): NuvioCShuffleItem? {
        val parts = sliceTotals(type, mood, anime, refine) ?: return null
        val (q, n) = weighted(parts) ?: return null
        val pick = if (n <= MAX_REACHABLE) q else {
            val dateKey = if (type == "movie") "primary_release_date" else "first_air_date"
            val from = q["$dateKey.gte"]?.take(4)?.toIntOrNull() ?: return draw(type, q)
            val to = q["$dateKey.lte"]?.take(4)?.toIntOrNull() ?: return draw(type, q)
            val years = (from..to).map { y ->
                q + ("$dateKey.gte" to maxOf("$y-01-01", q.getValue("$dateKey.gte"))) + ("$dateKey.lte" to minOf("$y-12-31", q.getValue("$dateKey.lte")))
            }
            weighted(totalsOf(type, years)) ?.first ?: q
        }
        return draw(type, pick)
    }

    /** (query, total) per decade for these choices; null when nothing matches. Totals cached 24 h. */
    private suspend fun sliceTotals(type: String, mood: NuvioCShuffleMood, anime: Boolean, refine: NuvioCRouletteRefine): List<Pair<Map<String, String>, Int>>? {
        val thisYear = LocalDate.now().year
        val dateKey = if (type == "movie") "primary_release_date" else "first_air_date"
        val first = maxOf(FIRST_DECADE, refine.decade.from?.let { it - it % 10 } ?: FIRST_DECADE)
        val last = minOf(thisYear, refine.decade.to ?: thisYear)
        val queries = (first..last step 10).mapNotNull { d ->
            val base = query(type, mood, anime, refine, d) ?: return@mapNotNull null
            val gte = maxOf("$d-01-01", base["$dateKey.gte"] ?: "$d-01-01")
            val lte = minOf("${d + 9}-12-31", base.getValue("$dateKey.lte"))
            if (gte > lte) null else base + ("$dateKey.gte" to gte) + ("$dateKey.lte" to lte)
        }
        if (queries.isEmpty()) return null
        return totalsOf(type, queries).takeIf { it.isNotEmpty() }
    }

    private suspend fun totalsOf(type: String, queries: List<Map<String, String>>): List<Pair<Map<String, String>, Int>> =
        kotlinx.coroutines.coroutineScope {
            queries.chunked(4).flatMap { chunk ->
                chunk.map { q -> async { total(type, q)?.takeIf { it > 0 }?.let { q to it } } }.awaitAll().filterNotNull()
            }
        }

    private fun weighted(parts: List<Pair<Map<String, String>, Int>>): Pair<Map<String, String>, Int>? {
        val sum = parts.sumOf { it.second }
        if (sum <= 0) return null
        var r = random.nextInt(sum)
        for (p in parts) {
            if (r < p.second) return p
            r -= p.second
        }
        return parts.last()
    }

    /** A query over 10,000 results: pick a decade in proportion to its size. */
    private suspend fun decadeQuery(type: String, base: Map<String, String>): Map<String, String>? {
        val thisYear = LocalDate.now().year
        val decades = (FIRST_DECADE..thisYear step 10).toList()
        val dateKey = if (type == "movie") "primary_release_date" else "first_air_date"
        val sized = decades.mapNotNull { d ->
            val q = base + ("$dateKey.gte" to "$d-01-01") + ("$dateKey.lte" to minOf("${d + 9}-12-31", base["$dateKey.lte"] ?: "${d + 9}-12-31"))
            total(type, q)?.takeIf { it > 0 }?.let { q to it }
        }
        if (sized.isEmpty()) return null
        var r = random.nextInt(sized.sumOf { it.second })
        for ((q, n) in sized) {
            if (r < n) return q
            r -= n
        }
        return sized.last().first
    }

    private suspend fun total(type: String, q: Map<String, String>): Int? {
        val key = type + q.filterKeys { it != "api_key" && it != "page" }.toSortedMap().toString()
        totalsMutex.withLock { totals[key]?.takeIf { System.currentTimeMillis() - it.first < TOTALS_MS }?.let { return it.second } }
        val n = fetch(type, q + ("page" to "1"))?.first ?: return null
        totalsMutex.withLock { totals[key] = System.currentTimeMillis() to n }
        return n
    }

    /** (total results, results of that page). */
    private suspend fun fetch(type: String, q: Map<String, String>): Pair<Int, List<JsonObject>>? = runCatching {
        val body = api.discover(type, q).takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@runCatching null
        val root = json.parseToJsonElement(body).jsonObject
        val total = root["total_results"]?.jsonPrimitive?.intOrNull ?: 0
        val results = root["results"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        total to results
    }.getOrNull()

    private fun JsonObject.toItem(type: String): NuvioCShuffleItem? {
        fun s(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        val id = (this["id"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: return null
        val name = s("title") ?: s("name") ?: return null
        val released = s("release_date") ?: s("first_air_date")
        val genreIds = (this["genre_ids"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }
        return NuvioCShuffleItem(
            id = "tmdb:$id",
            type = if (type == "tv") "series" else "movie",
            name = name,
            poster = s("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
            background = s("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            description = s("overview"),
            year = released?.take(4),
            released = released,
            genres = genreIds.mapNotNull(genreName),
            tmdbId = id,
            from = "tmdb",
            score = s("vote_average")?.toFloatOrNull(),
            anime = 16 in genreIds && s("original_language") == "ja"
        )
    }

    companion object {
        const val MIN_VOTES = 300
        const val ANIME_MIN_VOTES = 150
        const val MIN_SCORE = 6.5f
        private const val PAGE_SIZE = 20
        private const val MAX_REACHABLE = 500 * PAGE_SIZE // TMDB serves pages 1..500
        private const val FIRST_DECADE = 1920
        private const val TV_EXCLUDED = "10763|10764|10766|10767" // news, reality, soap, talk
        private const val TRUE_STORY_KEYWORD = "9672" // [fork] round 37: TMDB keyword "based on true story"
        private const val TOTALS_MS = 24 * 60 * 60 * 1000L
        private const val SEEN_MS = 30L * 24 * 60 * 60 * 1000
        private const val SEEN_CAP = 3000
        private const val RECENT_CAP = 400
    }
}
