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

// [fork] 2026-10-12 round 6 (switch `sdh_session_pick`): official only saves subtitle picks you make
// yourself; the player's automatic pick (where Prefer SDH works) is never kept. On Next / auto-play
// the saved pick for the series (often an older plain-track choice) was restored instead and the
// automatic pick never ran. Now an automatic pick counts for the rest of this player session: while
// it stands, the next episode skips the saved subtitle and picks again the same way (SDH included).
// A subtitle you choose yourself ends it, and is saved as before. Audio restore is untouched.
internal object NuvioCSubtitleSession {
    private const val TAG = "NuvioCSdh"
    private var owner = 0
    private var autoPick: String? = null

    fun rememberAuto(controller: Any, track: TrackInfo) {
        if (!NuvioCFeatures.SDH_SESSION_PICK) return
        owner = System.identityHashCode(controller)
        autoPick = "lang=${track.language} sdh=${NuvioCSdh.isSdhText(track.name, track.trackId)} name=${track.name}"
        android.util.Log.d(TAG, "auto pick kept for this session: $autoPick")
    }

    fun forget(controller: Any) {
        if (owner == System.identityHashCode(controller) && autoPick != null) {
            android.util.Log.d(TAG, "manual subtitle pick: session auto pick cleared")
            autoPick = null
        }
    }

    /** Player closed: forget its automatic pick, so a later player (e.g. Continue Watching) restores as official. */
    fun end(controller: Any) {
        if (owner == System.identityHashCode(controller)) { autoPick = null; owner = 0 }
    }

    /** Saved preference to restore for the next episode: subtitle dropped while an automatic pick stands. */
    fun filterSaved(controller: Any, saved: PlayerRuntimeController.TrackPreference?): PlayerRuntimeController.TrackPreference? {
        if (!NuvioCFeatures.SDH_SESSION_PICK || saved?.subtitle == null) return saved
        if (owner != System.identityHashCode(controller) || autoPick == null) return saved
        android.util.Log.d(TAG, "next episode: saved subtitle skipped, picking again like last time ($autoPick)")
        return saved.copy(subtitle = null)
    }
}
