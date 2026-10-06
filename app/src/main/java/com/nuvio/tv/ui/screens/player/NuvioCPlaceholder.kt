package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C placeholder check (idea from ysosrs123's fork, lighter rules). Debrid services and
// scrapers sometimes answer with a short playable "error" clip (not cached, downloading, service
// unavailable) instead of the film. Official already stops those clips marking the title watched or
// chaining auto-play, but still plays them. Nuvio C pauses such a clip as soon as its length is
// known, says so, and opens the Sources panel so another source can be picked.
//
// A clip counts as a placeholder when it is under 3 minutes AND the title is clearly long:
// its metadata runtime is 20 min+ and the clip is under a third of it, or the add-on said the file
// is 1 GB+ (a 9 GB file that lasts 40 seconds is not the film). Without either, nothing is judged.
// Switch: NuvioCFeatures.PLACEHOLDER_CHECK.

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Meta
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "NuvioCPlaceholder"

/** Pure rules (unit tested). */
@VisibleForTesting
internal object NuvioCPlaceholderRules {
    const val MAX_CLIP_MS = 3L * 60_000L
    const val MIN_RUNTIME_MS = 20L * 60_000L
    const val MAX_RUNTIME_FRACTION = 0.33
    const val BIG_FILE_BYTES = 1024L * 1024L * 1024L

    fun isPlaceholder(durationMs: Long, expectedRuntimeMs: Long?, advertisedBytes: Long?): Boolean {
        if (durationMs <= 0L || durationMs >= MAX_CLIP_MS) return false
        val longByRuntime = expectedRuntimeMs != null && expectedRuntimeMs >= MIN_RUNTIME_MS &&
            durationMs < expectedRuntimeMs * MAX_RUNTIME_FRACTION
        val longBySize = advertisedBytes != null && advertisedBytes >= BIG_FILE_BYTES
        return longByRuntime || longBySize
    }

    /** "2h 10min", "130 min", "1h", "45m", "130" → minutes; null when there is no number. */
    fun parseRuntimeMinutes(text: String?): Int? {
        val t = text?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val h = Regex("""(\d+)\s*h""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val m = Regex("""(\d+)\s*m""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (h != null || m != null) return (h ?: 0) * 60 + (m ?: 0)
        return Regex("""\d+""").find(t)?.value?.toIntOrNull()
    }
}

/** One player at a time, so the little state this needs lives here instead of in the controller. */
internal object NuvioCPlaceholder {
    @Volatile private var movieRuntimeMinutes: Int? = null
    @Volatile private var checkedUrl: String? = null
    @Volatile private var rejectedUrl: String? = null

    /** Hook in applyMetaDetails: remember the title's runtime (episodes come from metaVideos). */
    fun rememberMeta(meta: Meta) {
        movieRuntimeMinutes = NuvioCPlaceholderRules.parseRuntimeMinutes(meta.runtime)
    }

    internal fun check(controller: PlayerRuntimeController, durationMs: Long): Boolean {
        if (!NuvioCFeatures.PLACEHOLDER_CHECK) return false
        val url = controller.currentStreamUrl
        if (url.isBlank()) return false
        if (url == rejectedUrl) return true
        if (url == checkedUrl || durationMs <= 0L || !controller.hasRenderedFirstFrame) return false
        checkedUrl = url

        val state = controller._uiState.value
        val season = state.currentSeason
        val episode = state.currentEpisode
        val runtimeMin = if (season != null && episode != null) {
            controller.metaVideos.firstOrNull { it.season == season && it.episode == episode }?.runtime
        } else {
            movieRuntimeMinutes
        }
        val expectedMs = runtimeMin?.takeIf { it > 0 }?.let { it * 60_000L }
        val size = controller.currentVideoSize
        val reject = NuvioCPlaceholderRules.isPlaceholder(durationMs, expectedMs, size)
        Log.i(TAG, "duration=${durationMs}ms runtime=${runtimeMin ?: -1}min size=${size ?: -1} reject=$reject")
        if (!reject) return false
        rejectedUrl = url
        controller.nuvioCRejectPlaceholder()
        return true
    }
}

private fun PlayerRuntimeController.nuvioCRejectPlaceholder() {
    userPausedManually = true
    setPlaybackPaused(true)
    cancelNextEpisodeAutoPlayOnFatalError()
    _uiState.update {
        it.copy(
            postPlayMode = null,
            showStreamSourceIndicator = true,
            streamSourceIndicatorText = context.getString(R.string.nuvio_c_placeholder_stream)
        )
    }
    showSourcesPanel()
    scope.launch {
        delay(6_000L)
        _uiState.update { it.copy(showStreamSourceIndicator = false) }
    }
}

/** Hook at the top of evaluatePostPlayOverlayVisibility; true = placeholder, skip post-play. */
internal fun PlayerRuntimeController.nuvioCCheckPlaceholder(durationMs: Long): Boolean =
    runCatching { NuvioCPlaceholder.check(this, durationMs) }.getOrDefault(false)
