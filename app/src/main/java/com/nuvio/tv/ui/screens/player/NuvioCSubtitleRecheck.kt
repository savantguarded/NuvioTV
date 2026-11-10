package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 37 `subtitle_recheck`: a single safety re-check a few seconds after playback is ready.
// Official picks a subtitle as tracks and add-on subtitles arrive; when everything lands in an unlucky order
// (add-on list still loading at the last trigger, tracks read late) a title can start with nothing selected.
// The re-check runs the same official automatic pick once more, only when ALL of these hold:
//  - nothing is selected (no embedded track, no add-on subtitle),
//  - you did not pick or switch subtitles off yourself in this session,
//  - your saved choice for the title is not "off",
//  - you have a preferred subtitle language,
//  - the server isn't burning a subtitle in.
// It never overrides a choice; forced-subtitle rules stay official (the official pick decides).

import android.util.Log
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.WeakHashMap

internal object NuvioCSubtitleRecheck {
    private const val TAG = "NuvioCSubRecheck"
    private const val DELAY_MS = 4_500L
    private val jobs = WeakHashMap<PlayerRuntimeController, Job>()
    private val done = WeakHashMap<PlayerRuntimeController, Any>()

    /** Called when the player is ready; the re-check runs at most once per loaded stream. */
    fun schedule(controller: PlayerRuntimeController) {
        if (!NuvioCFeatures.SUBTITLE_RECHECK) return
        synchronized(this) {
            val key = controller.currentStreamUrl
            if (done[controller] == key || jobs[controller]?.isActive == true) return
            done[controller] = key
            jobs[controller] = controller.scope.launch {
                delay(DELAY_MS)
                runCatching { controller.recheck() }.onFailure { Log.w(TAG, "re-check failed: ${it.message}") }
            }
        }
    }

    private fun PlayerRuntimeController.recheck() {
        val state = _uiState.value
        val nothingSelected = state.selectedSubtitleTrackIndex < 0 && state.selectedAddonSubtitle == null
        when {
            !nothingSelected -> return
            isUserExplicitSubtitleSelection -> { Log.d(TAG, "skip: your own choice stands"); return }
            subtitleDisabledByPersistedPreference -> { Log.d(TAG, "skip: saved as off for this title"); return }
            hasBurnedInServerSubtitle -> return
            subtitleLanguageTargets().isEmpty() -> { Log.d(TAG, "skip: no preferred subtitle language"); return }
        }
        Log.d(TAG, "nothing selected after start, running the automatic pick once more")
        autoSubtitleSelected = false
        tryAutoSelectPreferredSubtitleFromAvailableTracks()
    }
}
