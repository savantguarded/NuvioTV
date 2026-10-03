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
// - YouTube's website answers 429 ("too many requests"): back-off for 15 minutes. During it the
//   app stops asking the YouTube website (so the limit can clear instead of being topped up by
//   every trailer) and goes to IMDb first, YouTube only if IMDb fails. The next trailer after the
//   15 minutes tries YouTube normally again; another 429 starts a new 15 minutes.
//
// Low-quality YouTube links and IMDb links are never put in the trailer caches, so once the
// limit clears a title gets its full-quality YouTube trailer again.
// Upstream files only call in here through a few `[fork]` lines in InAppYouTubeExtractor.kt and
// TrailerService.kt. Logcat tag: NuvioCTrailerBackup.

private const val TAG = "NuvioCTrailerBackup"

/** Shared YouTube state the extractor reports into. Plain object so the extractor needs no new wiring. */
internal object NuvioCYouTubeHealth {
    const val BACKOFF_MS = 15 * 60 * 1000L
    private const val MAX_TRACKED_URLS = 200

    /** Overridable clock for unit tests. */
    @Volatile internal var now: () -> Long = System::currentTimeMillis

    @Volatile private var backoffUntil = 0L

    // Links that must not be cached: YouTube's low-quality fallback, and IMDb links.
    private val degradedUrls = Collections.synchronizedSet(LinkedHashSet<String>())
    private val uncacheableUrls = Collections.synchronizedSet(LinkedHashSet<String>())

    /** The YouTube watch page failed; a 429 starts (or restarts) the back-off. */
    fun onWatchPageFailed(status: Int) {
        if (status != 429) return
        backoffUntil = now() + BACKOFF_MS
        Log.w(TAG, "YouTube rate limit (429): backing off YouTube for 15 min, IMDb goes first")
    }

    fun backingOff(): Boolean = now() < backoffUntil

    fun backoffMinutesLeft(): Long = ((backoffUntil - now()).coerceAtLeast(0L) + 59_999L) / 60_000L

    /** The extractor fell back to the progressive (~360p) stream for this link. */
    fun markDegraded(source: TrailerPlaybackSource) {
        remember(degradedUrls, source.videoUrl)
        remember(uncacheableUrls, source.videoUrl)
    }

    fun markUncacheable(source: TrailerPlaybackSource) = remember(uncacheableUrls, source.videoUrl)

    fun isDegraded(source: TrailerPlaybackSource): Boolean = source.videoUrl in degradedUrls

    fun cacheable(source: TrailerPlaybackSource): Boolean = source.videoUrl !in uncacheableUrls

    internal fun resetForTest() {
        backoffUntil = 0L
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
    private val tmdbService: TmdbService
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
    ): TrailerPlaybackSource? = choose(youtube = youtube, imdb = { imdbFor(tmdbId, type) })

    private suspend fun imdbFor(tmdbId: String?, type: String?): TrailerPlaybackSource? {
        val numericId = tmdbId?.toIntOrNull() ?: return null
        val imdbId = runCatching { tmdbService.tmdbToImdb(numericId, type ?: "movie") }
            .getOrNull()
            ?.takeIf { it.startsWith("tt") }
            ?: return null
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
            imdb: suspend () -> TrailerPlaybackSource?
        ): TrailerPlaybackSource? {
            if (NuvioCYouTubeHealth.backingOff()) {
                Log.i(TAG, "YouTube backing off (${NuvioCYouTubeHealth.backoffMinutesLeft()} min left): IMDb first")
                imdb()?.let {
                    Log.i(TAG, "Using IMDb trailer")
                    return it
                }
                Log.i(TAG, "No IMDb trailer, using YouTube")
                return youtube()
            }
            val fromYouTube = youtube() ?: return null
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
