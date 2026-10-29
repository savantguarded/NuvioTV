package com.nuvio.tv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.repository.ImdbEpisodeRatingsRepository
import com.nuvio.tv.data.repository.MDBListRepository
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
    val episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap()
)

@HiltViewModel
class NuvioCPlayerInfoViewModel @Inject constructor(
    private val mdbList: MDBListRepository,
    private val episodeRatings: ImdbEpisodeRatingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(NuvioCPlayerRatings())
    val state: StateFlow<NuvioCPlayerRatings> = _state.asStateFlow()

    /** [contentId] like "tt0133093" or "tmdb:603"; [type] "movie" or "series". */
    fun bind(contentIdOrNull: String?, type: String?) {
        val contentId = contentIdOrNull?.takeIf { it.isNotBlank() } ?: return
        val id = contentId.substringBefore(':').takeIf { it.isNotBlank() } ?: contentId
        val key = "$contentId|$type"
        if (_state.value.key == key) return
        _state.value = NuvioCPlayerRatings(key = key)
        val isSeries = type.equals("series", true) || type.equals("tv", true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (isSeries) {
                    val imdb = id.takeIf { it.startsWith("tt") }
                    val tmdb = contentId.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(':')?.toIntOrNull()
                    val map = runCatching { episodeRatings.getEpisodeRatings(imdb, tmdb) }.getOrDefault(emptyMap())
                    _state.update { if (it.key == key) it.copy(episodeRatings = map) else it }
                } else {
                    val ratings = mdbList.nuvioCRatingsForItem(contentId, "movie")
                    val order = runCatching { mdbList.nuvioCRatingOrder() }.getOrDefault(MDBListSettings.DEFAULT_RATING_ORDER)
                    _state.update { if (it.key == key) it.copy(ratings = ratings, order = order) else it }
                }
            }
        }
    }
}

/** IMDb rating of season [season] episode [episode] as "8.7", or null. */
internal fun NuvioCPlayerRatings.episodeRating(season: Int?, episode: Int?): String? {
    if (season == null || episode == null) return null
    return episodeRatings[season to episode]?.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.US, "%.1f", it) }
}
