package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [fork] Nuvio C round 31 (switch `start_over_previous`): the OSD Start over button's second press. The
// previous episode is the one before the playing one in season / episode order (specials left out), and
// it is started exactly like Next: official's next-episode flow (auto source, binge group, or the
// stream picker in manual mode) is handed the previous episode as its target. If that flow ends without
// changing episode (picker closed, nothing found), the real next episode is worked out again so the Up
// next card is never left pointing backwards.

/** The episode before the playing one, or null (films, first episode, unknown position). */
internal fun PlayerRuntimeController.nuvioCPreviousEpisodeVideo(): Video? {
    val season = currentSeason ?: return null
    val episode = currentEpisode ?: return null
    val type = contentType?.lowercase()
    if (type != "series" && type != "tv") return null
    return nuvioCPreviousIn(metaVideos, season, episode)
}

internal fun nuvioCPreviousIn(videos: List<Video>, season: Int, episode: Int): Video? {
    val ordered = videos
        .filter { (it.season ?: 0) > 0 && it.episode != null }
        .sortedWith(compareBy<Video>({ it.season ?: 0 }, { it.episode ?: 0 }))
    val index = ordered.indexOfFirst { it.season == season && it.episode == episode }
    return if (index > 0) ordered[index - 1] else null
}

internal fun PlayerRuntimeController.nuvioCPlayPreviousEpisode() {
    val previous = nuvioCPreviousEpisodeVideo() ?: return
    val season = previous.season ?: return
    val episode = previous.episode ?: return
    val startedOn = currentVideoId
    nextEpisodeVideo = previous
    _uiState.update {
        it.copy(
            nextEpisode = NextEpisodeInfo(
                videoId = previous.id,
                season = season,
                episode = episode,
                title = NuvioCEpisodeTitles.known(previous) ?: previous.title,
                thumbnail = previous.thumbnail,
                overview = previous.overview,
                released = previous.released,
                hasAired = true,
                available = previous.available,
                unairedMessage = null
            ),
            postPlayMode = null
        )
    }
    playNextEpisode(userInitiated = true)
    scope.launch {
        delay(300)
        nextEpisodeAutoPlayJob?.join()
        // Stream picker (manual mode) may still be open: wait for it to go away.
        var waited = 0
        while (_uiState.value.showEpisodesPanel && waited < 600) { delay(500); waited++ }
        if (currentVideoId == startedOn) recomputeNextEpisode(resetVisibility = false)
    }
}
