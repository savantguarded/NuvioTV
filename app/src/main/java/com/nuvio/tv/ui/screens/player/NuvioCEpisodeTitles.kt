package com.nuvio.tv.ui.screens.player

import android.util.Log
import android.util.LruCache
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.domain.model.Video

// [fork] Nuvio C episode names on Next / auto-play (switch `next_ep_title`, 2026-10-12).
// Playing from Home shows the episode name the details page had (from your meta add-on). Next and
// auto-play took the name from the player's own meta lookup instead, which can come back without
// names (blank), so only "S1 E4" showed. The details page now leaves its episode names here (memory
// only, no network) and the player uses them first; its own lookup is the fallback, and a blank
// name never wins over a real one. Logcat tag NuvioCEpisodeTitle shows which name was used.

internal object NuvioCEpisodeTitles {
    private const val TAG = "NuvioCEpisodeTitle"
    private val byVideoId = LruCache<String, String>(2000)
    private val bySeriesEpisode = LruCache<String, String>(2000)

    private fun seriesKey(videoId: String, season: Int?, episode: Int?): String? {
        if (season == null || episode == null) return null
        val prefix = videoId.substringBefore(':').takeIf { it.isNotBlank() } ?: return null
        return "$prefix|$season|$episode"
    }

    /** Called by the details page with the episode list it shows. */
    fun remember(videos: List<Video>) {
        if (!NuvioCFeatures.NEXT_EP_TITLE) return
        for (v in videos) {
            val title = v.title.trim().takeIf { it.isNotEmpty() } ?: continue
            byVideoId.put(v.id, title)
            seriesKey(v.id, v.season, v.episode)?.let { bySeriesEpisode.put(it, title) }
        }
    }

    /** Name for [target] when switching episodes in the player; null = official fallback chain. */
    fun pick(target: Video?): String? {
        if (!NuvioCFeatures.NEXT_EP_TITLE || target == null) return null
        val known = byVideoId.get(target.id)
            ?: seriesKey(target.id, target.season, target.episode)?.let { bySeriesEpisode.get(it) }
        val own = target.title.trim().takeIf { it.isNotEmpty() }
        val chosen = known ?: own
        Log.d(TAG, "id=${target.id} S${target.season}E${target.episode} details=${known != null} player='${target.title}' -> '${chosen.orEmpty()}'")
        return chosen
    }
}
