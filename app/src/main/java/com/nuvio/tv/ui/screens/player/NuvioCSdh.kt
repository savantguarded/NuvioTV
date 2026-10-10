package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C "Prefer SDH subtitles" (2026-10-10): when the player picks a subtitle in your
// preferred language by itself, an embedded SDH track (hearing impaired: sound descriptions and
// speaker names) wins over the plain one. Embedded tracks only; add-on subtitles are untouched.
// Forced subtitles keep priority (they are picked before this runs). No SDH track = official pick.
// Switch: NuvioCFeatures.PREFER_SDH (the in-app setting, off by default, turns it on).

import androidx.media3.common.C
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.nuvioc.NuvioCPrefs
import java.util.Locale

internal object NuvioCSdh {
    /** Track label / id says SDH (unit tested). */
    fun isSdhText(vararg texts: String?): Boolean = texts.filterNotNull().any { raw ->
        val t = raw.lowercase(Locale.US)
        Regex("""(\b|[._ \[(-])(sdh|shd|hoh|cc)(\b|[._ \])-])""").containsMatchIn(t) ||
            t.contains("hearing impaired") || t.contains("hearing-impaired") ||
            t.contains("closed caption") || t.contains("deaf")
    }
}

/**
 * The SDH track among [candidates] (indexes into [tracks], all in the target language and not
 * forced), or null to let the official pick run. Regional targets (pt / pt-br, es / es-419) only
 * take an SDH track of the right variant.
 */
internal fun PlayerRuntimeController.nuvioCPreferSdhIndex(
    tracks: List<TrackInfo>,
    candidates: List<Int>,
    normalizedTarget: String
): Int? {
    if (!NuvioCFeatures.PREFER_SDH || candidates.isEmpty()) return null
    if (!runCatching { NuvioCPrefs.preferSdh(context) }.getOrDefault(false)) return null
    val roleSdhIds = runCatching {
        _exoPlayer?.currentTracks?.groups.orEmpty()
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
            .filter { (it.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0 }
            .mapNotNull { it.id }
            .toSet()
    }.getOrDefault(emptySet())
    val regional = normalizedTarget in setOf("pt", "pt-br", "es", "es-419")
    return candidates.firstOrNull { index ->
        val track = tracks.getOrNull(index) ?: return@firstOrNull false
        if (track.isForced) return@firstOrNull false
        val sdh = (track.trackId != null && track.trackId in roleSdhIds) ||
            NuvioCSdh.isSdhText(track.name, track.trackId)
        if (!sdh) return@firstOrNull false
        if (!regional) return@firstOrNull true
        PlayerSubtitleUtils.detectTrackLanguageVariant(track.language, track.name, track.trackId) == normalizedTarget
    }
}
