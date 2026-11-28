package com.nuvio.tv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.repository.ImdbEpisodeRatingsRepository
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.MDBListSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// [fork] Nuvio C round 31: ratings for the player's own screens (loading screen, the About tab of the Cast
// panel, the Episodes panel). They are the same ratings the details page showed: MDBList's come from its
// disk cache and the IMDb episode ratings from their memory cache when the details page was opened first,
// so normally nothing is downloaded. Asked once per title, off the main thread, never while a frame is drawn.

data class NuvioCPlayerRatings(
    val key: String? = null,
    val ratings: MDBListRatings? = null,
    val order: List<String> = MDBListSettings.DEFAULT_RATING_ORDER,
    val episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
    /** [fork] round 33: ratings the meta add-on gave on its episode list (used where IMDb has none). */
    val addonEpisodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
    /** [fork] round 68 osd_about_v68: the lookup for [key] has finished (with or without ratings). */
    val loaded: Boolean = false
) {
    /** Add-on ratings under the IMDb ones (IMDb wins), as on the details page. */
    val allEpisodeRatings: Map<Pair<Int, Int>, Double>
        get() = if (addonEpisodeRatings.isEmpty()) episodeRatings else addonEpisodeRatings + episodeRatings
}

@HiltViewModel
class NuvioCPlayerInfoViewModel @Inject constructor(
    private val mdbList: MDBListRepository,
    private val episodeRatings: ImdbEpisodeRatingsRepository,
    private val tmdbService: TmdbService,
    private val metaRepository: com.nuvio.tv.domain.repository.MetaRepository
) : ViewModel() {
    private val _state = MutableStateFlow(NuvioCPlayerRatings())
    val state: StateFlow<NuvioCPlayerRatings> = _state.asStateFlow()
    /** [fork] round 68: finished lookups by title for this playback, so reopening About never asks again. */
    private val done = HashMap<String, NuvioCPlayerRatings>()

    /** [contentId] like "tt0133093" or "tmdb:603"; [type] "movie" or "series". */
    fun bind(contentIdOrNull: String?, type: String?) {
        val contentId = contentIdOrNull?.takeIf { it.isNotBlank() } ?: return
        val id = contentId.substringBefore(':').takeIf { it.isNotBlank() } ?: contentId
        val key = "$contentId|$type"
        if (_state.value.key == key) return
        if (com.nuvio.tv.NuvioCFeatures.OSD_ABOUT_V68) done[key]?.let { cached ->
            _state.value = cached.copy(addonEpisodeRatings = _state.value.addonEpisodeRatings)
            return
        }
        _state.value = NuvioCPlayerRatings(key = key, addonEpisodeRatings = _state.value.addonEpisodeRatings)
        val isSeries = type.equals("series", true) || type.equals("tv", true)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) {
                if (isSeries) {
                    var imdb = id.takeIf { it.startsWith("tt") }
                    if (com.nuvio.tv.NuvioCFeatures.PLAYER_EPISODE_RATINGS_FIX && imdb == null) {
                        // the details page's meta (in memory) carries the IMDb id the add-on gave
                        val base = contentId.split(':').let { p -> if (p.size >= 2) p.take(2).joinToString(":") else contentId }
                        imdb = listOf(base, contentId, id).firstNotNullOfOrNull { k ->
                            runCatching { metaRepository.getCachedMeta(type ?: "series", k)?.imdbId }.getOrNull()
                                ?: runCatching { metaRepository.getCachedMeta("series", k)?.imdbId }.getOrNull()
                        }?.substringBefore(':')?.takeIf { it.startsWith("tt") }
                    }
                    var tmdb = contentId.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(':')?.toIntOrNull()
                    // [fork] round 33 player_episode_ratings_fix: other ids (tvdb:, add-on ids) are resolved through
                    // TMDB like the details page does, instead of giving up.
                    if (com.nuvio.tv.NuvioCFeatures.PLAYER_EPISODE_RATINGS_FIX && tmdb == null) {
                        tmdb = kotlinx.coroutines.withTimeoutOrNull(6_000) {
                            runCatching { tmdbService.ensureTmdbId(contentId, "series", fallbackImdbId = imdb) }.getOrNull()
                        }?.toIntOrNull()
                    }
                    // [fork] beta.2: ratings are lined up with the add-on's episode order, so give it the add-on's list
                    // (from the details page's cached meta; empty = IMDb's own numbering, as before)
                    val addonEpisodes = listOf(contentId.split(':').take(2).joinToString(":"), contentId, id).firstNotNullOfOrNull { k ->
                        (runCatching { metaRepository.getCachedMeta(type ?: "series", k) }.getOrNull()
                            ?: runCatching { metaRepository.getCachedMeta("series", k) }.getOrNull())?.videos?.takeIf { it.isNotEmpty() }
                    }.orEmpty().map { com.nuvio.tv.data.repository.AddonEpisodeAirDate(it.season, it.episode, it.released) }
                    val map = runCatching { episodeRatings.getEpisodeRatings(imdb, tmdb, addonEpisodes) }.getOrDefault(emptyMap())
                    _state.update { if (it.key == key) it.copy(episodeRatings = map) else it }
                } else {
                    val ratings = mdbList.nuvioCRatingsForItem(contentId, "movie")
                    val order = runCatching { mdbList.nuvioCRatingOrder() }.getOrDefault(MDBListSettings.DEFAULT_RATING_ORDER)
                    _state.update { if (it.key == key) it.copy(ratings = ratings, order = order) else it }
                }
            } } finally {
                // [fork] round 68: mark the lookup finished even when it failed, so About drops the held space
                if (com.nuvio.tv.NuvioCFeatures.OSD_ABOUT_V68) {
                    _state.update { if (it.key == key) it.copy(loaded = true) else it }
                    _state.value.takeIf { it.key == key && it.loaded }?.let { done[key] = it }
                }
            }
        }
    }

    /** [fork] round 33: the add-on's own episode ratings from the player's episode list. */
    fun setAddonEpisodes(videos: List<com.nuvio.tv.domain.model.Video>) {
        if (!com.nuvio.tv.NuvioCFeatures.PLAYER_EPISODE_RATINGS_FIX) return
        val map = videos.mapNotNull { v ->
            val s = v.season ?: return@mapNotNull null
            val e = v.episode ?: return@mapNotNull null
            val r = v.rating?.takeIf { it > 0.0 } ?: return@mapNotNull null
            (s to e) to r
        }.toMap()
        if (map != _state.value.addonEpisodeRatings) _state.update { it.copy(addonEpisodeRatings = map) }
    }
}

/** IMDb rating of season [season] episode [episode] as "8.7", or null. */
internal fun NuvioCPlayerRatings.episodeRating(season: Int?, episode: Int?): String? {
    if (season == null || episode == null) return null
    return allEpisodeRatings[season to episode]?.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.US, "%.1f", it) }
}
