package com.nuvio.tv.core

import android.content.Context
import android.util.Log
import coil3.imageLoader
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C round 14 `player_memory_trim` (2026-10-21): when the full-screen player opens, the
// in-memory picture cache (Home / carousel backdrops, posters, logos decoded as bitmaps, up to 15% of
// the app's heap on a 2 GB TV) is cut to a quarter. Measured over adb on the TCL: Nuvio C sat at
// 680-830 MB during 4K Dolby Vision playback with up to 285 MB pushed to swap, which made everything
// stutter and got other apps (the launcher) killed. The cache refills by itself as you browse again
// (its maximum size is unchanged); the disk cache is untouched, so pictures come back fast.
object NuvioCPlayerMemory {
    private const val TAG = "NuvioCMemory"

    fun onPlayerOpened(context: Context) {
        if (!NuvioCFeatures.PLAYER_MEMORY_TRIM) return
        runCatching {
            val cache = context.applicationContext.imageLoader.memoryCache ?: return
            val before = cache.size
            cache.trimToSize(cache.maxSize / 4)
            Log.i(TAG, "player opened: image memory cache ${before / 1_048_576} MB -> ${cache.size / 1_048_576} MB")
        }.onFailure { Log.w(TAG, "image cache trim failed: ${it.message}") }
    }
}
