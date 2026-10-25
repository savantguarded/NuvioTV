package com.nuvio.tv.ui.components

import android.content.Context
import android.util.Log
import coil3.request.SuccessResult
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.streams.StreamBadgeRules
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// [fork] Nuvio C round 27c `badge_prefetch`. The imported badge pack is a small, fixed set of logos (Charles's
// pack: about 49 PNGs, ~300 KB on his Vercel site), but the sources list fetched them card by card, on demand,
// often while a film was downloading in the same connection. Some timed out and stayed blank on one card while
// the same logo showed on the next. Now, once the app has settled after start-up (and whenever the pack changes),
// every logo of every imported pack is fetched and decoded once, at the exact size and memory key the chips use:
// opening the sources list then only reads memory (or the badge disk cache if Android trimmed memory), nothing
// downloads during playback. Runs 6 at a time; a logo that fails is tried again on the next run.

internal object NuvioCBadgePrefetch {
    private const val TAG = "NuvioCBadgePrefetch"
    private const val PARALLEL = 6

    @Volatile private var ready: Set<String> = emptySet()

    suspend fun run(context: Context, rules: StreamBadgeRules) {
        if (!NuvioCFeatures.BADGE_PREFETCH) return
        val urls = rules.imports.asSequence()
            .flatMap { it.filters.asSequence() }
            .map { it.imageURL.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        val todo = urls.filterNot { it in ready }
        if (todo.isEmpty()) return
        val app = context.applicationContext
        val (w, h) = badgeDecodeSize(app.resources.displayMetrics.density)
        val loader = getBadgeImageLoader(app)
        val started = android.os.SystemClock.uptimeMillis()
        val ok = mutableListOf<String>()
        coroutineScope {
            todo.chunked(PARALLEL).forEach { batch ->
                batch.map { url ->
                    async {
                        val result = runCatching { loader.execute(badgeImageRequest(app, url, w, h)) }.getOrNull()
                        url.takeIf { result is SuccessResult }
                    }
                }.awaitAll().filterNotNullTo(ok)
            }
        }
        ready = ready + ok
        Log.i(TAG, "Badges ready: ${ok.size}/${todo.size} in ${android.os.SystemClock.uptimeMillis() - started} ms")
    }
}
