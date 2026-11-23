package com.nuvio.tv.data.quality

// [fork] Nuvio C round 60 `detail_quality_line`: where a details page gets its quality badges.
//  1. Public Torrentio (no config, no debrid, OkHttp's own user agent, 5 s timeout, 2 calls at a time, same
//     title never asked twice at once, 15 min pause after a 403 / 429). Its cache headers are kept by an
//     HTTP cache, so a re-open inside the hour costs nothing.
//  2. QualiCache only when Torrentio gives nothing usable, errors, times out or is paused: `min_trust=high`,
//     key from build config (QUALICACHE_API_KEY); no key = this step is skipped. `pending` gets one re-poll.
// Only the compact result is kept (TitleQualityCache). Asked only from a details page, never from a row.

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What one source said about one title / episode. */
sealed interface SourceAnswer {
    /** Round 64: [editions] completes once the rest of the answer was read for editions (films only), else null. */
    data class Usable(
        val quality: TitleQuality,
        val nextRefreshAt: Long? = null,
        val editions: kotlinx.coroutines.Deferred<List<QualityEdition>>? = null
    ) : SourceAnswer
    /** The source answered and has nothing usable. */
    data object Empty : SourceAnswer
    /** QualiCache is still crawling this title. */
    data object Pending : SourceAnswer
    /** Error, timeout, paused after a rate limit, or no key. */
    data object Failed : SourceAnswer
}

/** The end of a lookup. */
sealed interface QualityLookup {
    data class Found(val quality: TitleQuality, val editions: kotlinx.coroutines.Deferred<List<QualityEdition>>? = null) : QualityLookup
    /** Every source that could answer did, and none has a usable release. */
    data object NothingUsable : QualityLookup
    /** No source could answer (offline, errors, rate limit). */
    data object Failed : QualityLookup
}

/** One episode, or a movie when [season] / [episode] are null. */
data class QualityTarget(val imdbId: String, val season: Int? = null, val episode: Int? = null) {
    val isEpisode get() = season != null && episode != null
    val torrentioPath get() = if (isEpisode) "series/$imdbId:$season:$episode" else "movie/$imdbId"
}

@Singleton
class TitleQualityRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val cache = TitleQualityCache.shared(context)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val torrentioGate = Semaphore(MAX_CONCURRENT)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<SourceAnswer>>()
    @Volatile private var cooldownUntil = 0L
    // round 60c/d: the Torrentio answer is read and parsed on low-priority threads (one per allowed call), so it never
    // competes with the details page for the CPU
    private val parseDispatcher = java.util.concurrent.Executors.newFixedThreadPool(MAX_CONCURRENT) { r ->
        Thread(r, "NuvioCQualityRead").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()

    // Own client: no app user-agent header, so OkHttp sends its normal one (a blank or script one gets a 403).
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "nuvio_c_quality_http"), 8L * 1024 * 1024))
            .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    /** Streams read by the last Torrentio lookup (for the log / tests). */
    @Volatile var lastStreamsRead = 0
        private set

    val qualiCacheEnabled: Boolean get() = BuildConfig.QUALICACHE_API_KEY.isNotBlank()

    fun inCooldown(now: Long = System.currentTimeMillis()) = now < cooldownUntil

    /** Torrentio first, then QualiCache; for a series, [targets] are the latest aired episode then the one before. */
    suspend fun lookup(
        targets: List<QualityTarget>, movieYear: Int?, onPending: suspend () -> Unit = {}, movieTitle: String? = null
    ): Pair<QualityLookup, Long?> {
        var torrentioAnswered = false
        for (t in targets.take(2)) {
            when (val a = torrentio(t, movieYear, movieTitle)) {
                is SourceAnswer.Usable -> return QualityLookup.Found(a.quality, a.editions) to null
                SourceAnswer.Empty -> torrentioAnswered = true
                else -> break // error / paused: straight to QualiCache, no second Torrentio call
            }
        }
        val first = targets.firstOrNull() ?: return QualityLookup.Failed to null
        if (!qualiCacheEnabled) return (if (torrentioAnswered) QualityLookup.NothingUsable else QualityLookup.Failed) to null
        var q = qualiCache(first)
        if (q == SourceAnswer.Pending) {
            onPending()
            delay(REPOLL_MS)
            q = qualiCache(first)
        }
        return when (q) {
            is SourceAnswer.Usable -> QualityLookup.Found(q.quality) to q.nextRefreshAt
            SourceAnswer.Empty -> (if (torrentioAnswered) QualityLookup.NothingUsable else QualityLookup.Failed) to null
            else -> (if (torrentioAnswered) QualityLookup.NothingUsable else QualityLookup.Failed) to null
        }
    }

    suspend fun torrentio(target: QualityTarget, movieYear: Int?, movieTitle: String? = null): SourceAnswer {
        if (inCooldown()) return SourceAnswer.Failed
        val key = target.torrentioPath
        val mine = CompletableDeferred<SourceAnswer>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) return existing.await()
        if (NuvioCFeatures.QUALITY_EDITIONS && !target.isEpisode) {
            // round 64: the read runs on its own; the pick is handed over as soon as it is known and the rest of the
            // answer is then read for editions only (the page doesn't wait for that part)
            val editions = CompletableDeferred<List<QualityEdition>>()
            tailScope.launch {
                try {
                    torrentioGate.withPermit { fetchTorrentio(target, movieYear, movieTitle, mine, editions) }
                } finally {
                    if (!mine.isCompleted) mine.complete(SourceAnswer.Failed)
                    if (!editions.isCompleted) editions.complete(emptyList())
                    inFlight.remove(key, mine)
                }
            }
            return mine.await()
        }
        val answer = try {
            torrentioGate.withPermit { fetchTorrentio(target, movieYear) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            mine.complete(SourceAnswer.Failed); inFlight.remove(key, mine); throw e
        }
        mine.complete(answer)
        inFlight.remove(key, mine)
        return answer
    }

    /** Round 64: the edition read outlives the details page (it is short and capped), so it has its own scope. */
    private val tailScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + parseDispatcher)

    /**
     * Round 64 (films): the pick as in round 60d, handed to [pick] the moment it is known; then the rest of the list
     * (up to [EDITION_SCAN_MAX] streams) is read for editions, which go to [editions].
     */
    private suspend fun fetchTorrentio(
        target: QualityTarget, movieYear: Int?, movieTitle: String?,
        pick: CompletableDeferred<SourceAnswer>, editions: CompletableDeferred<List<QualityEdition>>
    ) {
        runCatching {
            val req = Request.Builder().url("$TORRENTIO/stream/${target.torrentioPath}.json").build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 403 || resp.code == 429) {
                    cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                    pick.complete(SourceAnswer.Failed); return@use
                }
                if (!resp.isSuccessful) { pick.complete(SourceAnswer.Failed); return@use }
                val body = resp.body ?: run { pick.complete(SourceAnswer.Failed); return@use }
                body.charStream().buffered(8 * 1024).use { reader ->
                    val seen = ArrayList<QualityStreamText>()
                    val iter = TitleQualityParser.streamsFrom(reader).iterator()
                    val tapped = object : Iterator<QualityStreamText> {
                        override fun hasNext() = iter.hasNext()
                        override fun next() = iter.next().also { seen += it }
                    }
                    val early = TitleQualityParser.bestEarly(Sequence { tapped }, movieYear)
                    lastStreamsRead = early.read
                    pick.complete(early.quality?.let { SourceAnswer.Usable(it, editions = editions) } ?: SourceAnswer.Empty)
                    if (early.quality == null) return@use
                    while (seen.size < EDITION_SCAN_MAX && tapped.hasNext()) tapped.next()
                    editions.complete(TitleQualityParser.editions(seen, movieTitle, movieYear))
                }
            }
        }.onFailure { if (!pick.isCompleted) pick.complete(SourceAnswer.Failed) }
    }

    /**
     * Round 60d: reads the answer as it downloads, one stream at a time, and stops (closing the download) as soon as
     * nothing further down Torrentio's quality-sorted list can change the pick.
     */
    private suspend fun fetchTorrentio(target: QualityTarget, movieYear: Int?): SourceAnswer = withContext(parseDispatcher) {
        runCatching {
            val req = Request.Builder().url("$TORRENTIO/stream/${target.torrentioPath}.json").build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 403 || resp.code == 429) {
                    cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                    return@use SourceAnswer.Failed
                }
                if (!resp.isSuccessful) return@use SourceAnswer.Failed
                val body = resp.body ?: return@use SourceAnswer.Failed
                val pick = body.charStream().buffered(8 * 1024).use { reader ->
                    TitleQualityParser.bestEarly(TitleQualityParser.streamsFrom(reader), if (target.isEpisode) null else movieYear)
                }
                lastStreamsRead = pick.read
                pick.quality?.let { SourceAnswer.Usable(it) } ?: SourceAnswer.Empty
            }
        }.getOrElse { SourceAnswer.Failed }
    }

    suspend fun qualiCache(target: QualityTarget): SourceAnswer = withContext(Dispatchers.IO) {
        val key = BuildConfig.QUALICACHE_API_KEY
        if (key.isBlank()) return@withContext SourceAnswer.Failed
        runCatching {
            val type = if (target.isEpisode) "series" else "movie"
            val url = buildString {
                append("$QUALICACHE/v1/quality/$type/${target.imdbId}?min_trust=high")
                if (target.isEpisode) append("&season=${target.season}&episode=${target.episode}")
            }
            client.newCall(Request.Builder().url(url).header("X-API-Key", key).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use SourceAnswer.Failed
                val o = json.parseToJsonElement(resp.body?.string() ?: return@use SourceAnswer.Failed).jsonObject
                when (o.str("status")) {
                    "ready" -> {
                        val tokens = (o["tokens"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        // Unix seconds (a float), sometimes years ahead: kept at most 30 days
                        val next = o.str("next_refresh_at")?.let { raw ->
                            raw.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                        }?.coerceAtMost(System.currentTimeMillis() + MAX_KEEP_MS)
                        TitleQualityParser.fromTokens(tokens)?.let { SourceAnswer.Usable(it, next) } ?: SourceAnswer.Empty
                    }
                    "empty" -> SourceAnswer.Empty
                    "pending", "error" -> SourceAnswer.Pending
                    else -> SourceAnswer.Failed
                }
            }
        }.getOrElse { SourceAnswer.Failed }
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val TORRENTIO = "https://torrentio.strem.fun"
        const val QUALICACHE = "https://quality.myaio.xyz"
        const val MAX_CONCURRENT = 2
        const val TIMEOUT_S = 5L
        const val COOLDOWN_MS = 15 * 60_000L
        const val REPOLL_MS = 20_000L
        const val MAX_KEEP_MS = 30L * 24 * 60 * 60_000L
        /** Round 64: how many streams of a film's answer are read for editions at most. */
        const val EDITION_SCAN_MAX = 400
    }
}
