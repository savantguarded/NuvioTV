package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.MDBListRatingsRow
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.nuvioCRuntime
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// [fork] Nuvio C round 31 (switch `osd_about_tab`): the player's Cast panel gets an About tab, so you can check
// what you're watching without leaving it: title (episode name for a show), season / episode, air or release
// date, running time, ratings (three MDBList ratings for a film, the IMDb rating of the episode), genres and
// the synopsis. All of it is already in memory (the player's metadata and the ratings the details page
// loaded), and nothing is built until the panel opens, so playback isn't touched. App font throughout; the
// panel dims in HDR with the rest of the player (NuvioCOsdCastPanel).

/** What the About tab shows. */
internal data class NuvioCAboutInfo(
    val contentId: String?,
    val type: String?,
    val title: String,
    val line: String?,
    val synopsis: String?,
    val genres: List<String>,
    val season: Int?,
    val episode: Int?
)

internal fun nuvioCAboutInfo(controller: PlayerRuntimeController, ui: PlayerUiState): NuvioCAboutInfo {
    val season = ui.currentSeason
    val episode = ui.currentEpisode
    val video = if (season != null && episode != null) {
        controller.metaVideos.firstOrNull { it.season == season && it.episode == episode }
    } else null
    val isEpisode = video != null
    val title = when {
        isEpisode -> NuvioCEpisodeTitles.known(video!!) ?: ui.currentEpisodeTitle ?: video.title
        else -> ui.contentName ?: ui.title
    }.ifBlank { ui.title }
    val date = (video?.released)?.let(::nuvioCPrettyDate)
    val runtime = video?.runtime?.takeIf { it > 0 }?.let { nuvioCRuntime("$it") }
    val line = buildList {
        if (isEpisode) add("S$season · E$episode")
        if (date != null) add(date) else if (!isEpisode) ui.releaseYear?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (runtime != null) add(runtime)
    }.joinToString(" · ").takeIf { it.isNotBlank() }
    return NuvioCAboutInfo(
        contentId = controller.contentId,
        type = controller.contentType,
        title = title,
        line = line,
        synopsis = (video?.overview?.takeIf { it.isNotBlank() } ?: ui.description)?.trim(),
        genres = controller.metaGenres.take(4),
        season = season,
        episode = episode
    )
}

private fun nuvioCPrettyDate(raw: String): String? = runCatching {
    LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
}.getOrNull()

/** About / Cast tabs at the top of the panel; [castContent] gets the tab row's requester for Up. */
@Composable
internal fun NuvioCAboutCastTabs(
    tab: Int,
    hasCast: Boolean,
    onTab: (Int) -> Unit,
    about: NuvioCAboutInfo,
    castContent: @Composable (FocusRequester) -> Unit
) {
    val aboutTab = remember { FocusRequester() }
    val castTab = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (tab == 0) aboutTab.requestFocusAfterFrames() }
    Column(Modifier.fillMaxSize().padding(NuvioTheme.spacing.xl)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioCTabPill(stringResource(R.string.nuvio_c_osd_about), tab == 0, aboutTab) { onTab(0) }
            if (hasCast) NuvioCTabPill(stringResource(R.string.nuvio_c_osd_cast), tab == 1, castTab) { onTab(1) }
        }
        Spacer(Modifier.height(NuvioTheme.spacing.md))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0 || !hasCast) NuvioCAboutContent(about) else castContent(castTab)
        }
    }
}

@Composable
private fun NuvioCTabPill(label: String, selected: Boolean, requester: FocusRequester, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.focusRequester(requester),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f),
            contentColor = if (selected) Color.Black else NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50))
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun NuvioCAboutContent(about: NuvioCAboutInfo) {
    val info: NuvioCPlayerInfoViewModel = hiltViewModel()
    LaunchedEffect(about.contentId, about.type) { info.bind(about.contentId, about.type) }
    val ratings by info.state.collectAsState()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    // The text holds focus so Up / Down scroll a long synopsis; Up at the top goes back to the tabs.
    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> if (scroll.value < scroll.maxValue) { scope.launch { scroll.animateScrollBy(160f) }; true } else true
                    Key.DirectionUp -> if (scroll.value > 0) { scope.launch { scroll.animateScrollBy(-160f) }; true } else false
                    else -> false
                }
            }
            .focusable()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = about.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        about.line?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val episodeRating = ratings.episodeRating(about.season, about.episode)
        val filmRatings = ratings.ratings
        if (episodeRating != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("IMDb", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFF5C518))
                Text(episodeRating, style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextPrimary)
            }
        } else if (filmRatings != null && about.season == null) {
            MDBListRatingsRow(ratings = filmRatings, order = ratings.order, maxItems = 3)
        }
        about.synopsis?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
        }
        if (about.genres.isNotEmpty()) {
            Text(
                about.genres.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
