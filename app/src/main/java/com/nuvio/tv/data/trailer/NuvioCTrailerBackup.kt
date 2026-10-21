package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// [fork] Nuvio C: IMDb trailers as a backup when YouTube rate-limits. YouTube stays the source;
// IMDb is only asked when YouTube can't give a good picture:
//
// - YouTube answers normally (adaptive or HLS): used as is, IMDb is never touched.
// - YouTube hands back its low-quality fallback (the ~360p combined file): IMDb is tried for that
//   title; if IMDb has nothing at 720p or better, the 360p YouTube trailer still plays.
// - YouTube rate-limits (429 / bot check): since 2026-10-14 official's own gate (YouTubeRateLimitGate,
//   1.1.0-beta.6) pauses YouTube, doubling up to 15 minutes. While it is paused the backup asks IMDb
//   for every title instead; official's gate decides when YouTube is asked again.
//
// - YouTube's best video stream is under 720p (old or low-res uploads): treated like the fallback
//   above, IMDb is tried first (2026-10-11).
// - "Prefer IMDb Trailers" on (Settings > Layout > Detail Page, 2026-10-11): IMDb is asked first for
//   every trailer; YouTube only when IMDb has nothing at 720p or better. Since round 14 an IMDb
//   lookup is one small request to IMDb's own data service (under a second), not a hidden browser.
// - Never while the full-screen player is open (round 14).
//
// Low-quality YouTube links and IMDb links are never put in the trailer caches, so once the
// limit clears a title gets its full-quality YouTube trailer again.
// Upstream files only call in here through a few `[fork]` lines in InAppYouTubeExtractor.kt and
// TrailerService.kt. Logcat tag: NuvioCTrailerBackup.

private const val TAG = "NuvioCTrailerBackup"

/** Shared YouTube state the extractor reports into. Plain object so the extractor needs no new wiring. */
internal object NuvioCYouTubeHealth {
    private const val MAX_TRACKED_URLS = 200

    /** Overridable clock for unit tests (IMDb cache ages). */
    @Volatile internal var now: () -> Long = System::currentTimeMillis

    // Official's rate-limit gate, attached by InAppYouTubeExtractor (one [fork] line). Tests set these.
    @Volatile internal var blocked: () -> Boolean = { false }
    @Volatile internal var remainingMs: () -> Long = { 0L }

    // Links that must not be cached: YouTube's low-quality fallback, and IMDb links.
    private val degradedUrls = Collections.synchronizedSet(LinkedHashSet<String>())
    private val uncacheableUrls = Collections.synchronizedSet(LinkedHashSet<String>())

    internal fun attach(gate: YouTubeRateLimitGate) {
        blocked = gate::isBlocked
        remainingMs = gate::remainingMs
    }

    fun backingOff(): Boolean = blocked()

    fun backoffMinutesLeft(): Long = (remainingMs().coerceAtLeast(0L) + 59_999L) / 60_000L

    /** The extractor fell back to the progressive (~360p) stream for this link. */
    fun markDegraded(source: TrailerPlaybackSource) {
        if (!com.nuvio.tv.NuvioCFeatures.IMDB_TRAILER_BACKUP) return
        remember(degradedUrls, source.videoUrl)
        remember(uncacheableUrls, source.videoUrl)
    }

    fun markUncacheable(source: TrailerPlaybackSource) = remember(uncacheableUrls, source.videoUrl)

    fun isDegraded(source: TrailerPlaybackSource): Boolean = source.videoUrl in degradedUrls

    fun cacheable(source: TrailerPlaybackSource): Boolean = source.videoUrl !in uncacheableUrls

    internal fun resetForTest() {
        blocked = { false }
        remainingMs = { 0L }
        degradedUrls.clear()
        uncacheableUrls.clear()
        now = System::currentTimeMillis
    }

    private fun remember(set: MutableSet<String>, url: String) {
        synchronized(set) {
            set.add(url)
            while (set.size > MAX_TRACKED_URLS) set.remove(set.first())
        }
    }
}

@Singleton
class NuvioCTrailerBackup @Inject constructor(
    private val imdbResolver: NuvioCImdbTrailerResolver,
    private val tmdbService: TmdbService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {
    private data class ImdbResult(val source: TrailerPlaybackSource?, val at: Long)

    // One hidden IMDb browser at a time, so fast scrolling can't stack several up.
    private val imdbMutex = Mutex()
    // imdbId -> last IMDb answer. Hits are kept 1 h (IMDb links are signed and expire), misses 30 min.
    private val imdbCache = ConcurrentHashMap<String, ImdbResult>()

    /** Picks the trailer for a title: [youtube] is the normal TMDB -> YouTube lookup. */
    suspend fun pick(
        tmdbId: String?,
        type: String?,
        youtube: suspend () -> TrailerPlaybackSource?
    ): TrailerPlaybackSource? = choose(
        youtube = youtube,
        imdb = { imdbFor(tmdbId, type) },
        preferImdb = preferImdb()
    )

    fun preferImdb(): Boolean = com.nuvio.tv.data.local.NuvioCTrailerPrefs.preferImdb(appContext)

    /** IMDb only: used while official's gate keeps YouTube paused, or with Prefer IMDb on. */
    suspend fun imdb(tmdbId: String?, type: String?): TrailerPlaybackSource? = imdbFor(tmdbId, type)

    private suspend fun imdbFor(tmdbId: String?, type: String?): TrailerPlaybackSource? {
        // Round 14 (2026-10-21): no IMDb lookups while the full-screen player is open. The post-play
        // prefetch asks for trailers at ~80% of an episode; the YouTube result is used as is then.
        if (com.nuvio.tv.NuvioCFeatures.IMDB_NO_BROWSER && com.nuvio.tv.core.NuvioCPlayback.active) {
            Log.i(TAG, "Player open: skipping the IMDb lookup, keeping YouTube")
            return null
        }
        val numericId = tmdbId?.toIntOrNull() ?: run {
            Log.i(TAG, "No TMDB id for this trailer, can't look it up on IMDb")
            return null
        }
        val imdbId = runCatching { tmdbService.tmdbToImdb(numericId, type ?: "movie") }
            .getOrNull()
            ?.takeIf { it.startsWith("tt") }
            ?: run {
                Log.i(TAG, "No IMDb id for TMDB $numericId")
                return null
            }
        Log.i(TAG, "Looking up $imdbId on IMDb")
        cached(imdbId)?.let { return it.source }
        return imdbMutex.withLock {
            cached(imdbId)?.let { return@withLock it.source }
            val source = imdbResolver.resolve(imdbId, type)
            source?.let(NuvioCYouTubeHealth::markUncacheable)
            imdbCache[imdbId] = ImdbResult(source, NuvioCYouTubeHealth.now())
            source
        }
    }

    private fun cached(imdbId: String): ImdbResult? {
        val hit = imdbCache[imdbId] ?: return null
        val ttl = if (hit.source != null) 60 * 60 * 1000L else 30 * 60 * 1000L
        if (NuvioCYouTubeHealth.now() - hit.at < ttl) return hit
        imdbCache.remove(imdbId, hit)
        return null
    }

    internal companion object {
        /** The decision itself, kept free of Android and network code so it can be unit-tested. */
        internal suspend fun choose(
            youtube: suspend () -> TrailerPlaybackSource?,
            imdb: suspend () -> TrailerPlaybackSource?,
            preferImdb: Boolean = false
        ): TrailerPlaybackSource? {
            if (preferImdb) {
                imdb()?.let {
                    Log.i(TAG, "Prefer IMDb: using IMDb trailer")
                    return it
                }
                Log.i(TAG, "Prefer IMDb: nothing on IMDb at 720p+, falling back to YouTube")
                return youtube()
            }
            val fromYouTube = youtube()
            if (fromYouTube == null) {
                // A dead link outside a rate limit stays without a trailer, as before.
                if (!NuvioCYouTubeHealth.backingOff()) return null
                Log.i(TAG, "YouTube failed while rate-limited (${NuvioCYouTubeHealth.backoffMinutesLeft()} min left): trying IMDb")
                return imdb()?.also { Log.i(TAG, "Using IMDb trailer") }
            }
            if (!NuvioCYouTubeHealth.isDegraded(fromYouTube)) return fromYouTube
            Log.i(TAG, "YouTube gave its low-quality fallback: trying IMDb")
            imdb()?.let {
                Log.i(TAG, "Using IMDb trailer")
                return it
            }
            Log.i(TAG, "No IMDb trailer, keeping the low-quality YouTube one")
            return fromYouTube
        }
    }
}
