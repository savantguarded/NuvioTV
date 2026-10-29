package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.components.MDBListRatingsRow
import kotlinx.coroutines.delay

// [fork] Nuvio C round 31 (switch `loading_synopsis`): under official's centred, pulsing loading logo (its own
// fixed 320 x 180 box, untouched), a block of fixed size: the episode line or year, the ratings (three MDBList
// ratings for a film, IMDb for an episode) and the synopsis, three lines at most. The block's space is there
// from the first frame, so the logo never moves; its text fades in once, with no motion, when the ratings
// are known (or after 1.2 s without them). Everything comes from memory or the ratings caches.

private val BLOCK_HEIGHT = 142.dp
private val BLOCK_WIDTH = 600.dp

@Composable
internal fun NuvioCLoadingInfo(viewModel: PlayerViewModel, uiState: PlayerUiState) {
    if (!NuvioCFeatures.LOADING_SYNOPSIS) return
    val controller = viewModel.controller
    val about = remember(uiState.currentSeason, uiState.currentEpisode, uiState.description, uiState.title) {
        nuvioCAboutInfo(controller, uiState)
    }
    val info: NuvioCPlayerInfoViewModel = hiltViewModel()
    LaunchedEffect(about.contentId, about.type) { info.bind(about.contentId, about.type) }
    val ratings by info.state.collectAsState()
    val isEpisode = about.season != null && about.episode != null
    val episodeRating = ratings.episodeRating(about.season, about.episode)
    val filmRatings = ratings.ratings.takeIf { !isEpisode }
    val ratingsKnown = episodeRating != null || filmRatings != null
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Wait for the ratings (they come from caches, normally at once), never longer than 1.2 s.
        var waited = 0
        while (!info.state.value.let { it.ratings != null || it.episodeRatings.isNotEmpty() } && waited < 12) {
            delay(100); waited++
        }
        alpha.animateTo(1f, tween(400))
    }
    val line = if (isEpisode) {
        listOfNotNull("S${about.season} · E${about.episode}", about.title.takeIf { it.isNotBlank() }).joinToString(" · ")
    } else {
        listOfNotNull(uiState.releaseYear?.takeIf { it.isNotBlank() }, about.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }
    Box(
        modifier = Modifier.width(BLOCK_WIDTH).height(BLOCK_HEIGHT).graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            if (ratingsKnown) {
                Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                    if (episodeRating != null && com.nuvio.tv.NuvioCFeatures.IMDB_LOGO_EVERYWHERE) {
                        com.nuvio.tv.ui.components.NuvioCImdbRating(episodeRating) // [fork] round 32
                    } else if (episodeRating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("IMDb", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFF5C518))
                            Text(episodeRating, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                        }
                    } else if (filmRatings != null) {
                        MDBListRatingsRow(ratings = filmRatings, order = ratings.order, maxItems = 3)
                    }
                }
            }
            about.synopsis?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = if (com.nuvio.tv.NuvioCFeatures.LOADING_TEXT_SMALLER) MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp) else MaterialTheme.typography.bodyMedium, // [fork] round 32
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
