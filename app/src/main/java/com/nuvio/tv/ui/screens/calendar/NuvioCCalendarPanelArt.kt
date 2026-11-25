package com.nuvio.tv.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [fork] round 65 CALENDAR_PANEL_STEADY: the side panel's picture and logo are chosen ONCE per focused row.
 * Waits up to [NUVIO_C_PANEL_WAIT_MS] for the row's details (the episode still, the logo), then picks; details that
 * arrive later never swap the picture or turn the name into a logo. Between rows the previous picture stays until
 * the new one has decoded, so the card never flashes empty.
 */
internal const val NUVIO_C_PANEL_WAIT_MS = 300L

/** What the panel shows for one row, decided once. */
internal data class NuvioCPanelPick(val key: String, val pictures: List<String>, val logo: String?)

/** Pure choice, kept apart for tests: still first, then the backdrop, then the poster; row logo, then details logo. */
internal fun nuvioCPanelPick(row: CalendarRow, details: CalendarRowDetails?): NuvioCPanelPick =
    NuvioCPanelPick(
        key = row.key.toString(),
        pictures = listOfNotNull(details?.still(row.season, row.episode), row.backdrop ?: details?.backdrop, row.poster)
            .filter(String::isNotBlank).distinct(),
        logo = (row.logo ?: details?.logo)?.takeIf(String::isNotBlank)
    )

@Composable
internal fun BoxScope.NuvioCCalendarSteadyArt(row: CalendarRow, details: CalendarRowDetails?) {
    val latestDetails by rememberUpdatedState(details)
    // The pick for the focused row (null while waiting for its details).
    var pick by remember(row.key) { mutableStateOf<NuvioCPanelPick?>(null) }
    // The last picture that actually decoded, kept on screen under the next one until that one is ready.
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(row.key) {
        withTimeoutOrNull(NUVIO_C_PANEL_WAIT_MS) { snapshotFlow { latestDetails }.first { it != null } }
        pick = nuvioCPanelPick(row, latestDetails)
    }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.BackgroundCard)) {
        shown?.let { url ->
            val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(false).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }
        val current = pick
        if (current != null) {
            var index by remember(current) { mutableStateOf(0) }
            val chosen = current.pictures.getOrNull(index)
            if (chosen != null && chosen != shown) {
                val request = remember(chosen) { ImageRequest.Builder(context).data(chosen).crossfade(false).build() }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    onSuccess = { shown = chosen },
                    onError = { index++ },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (chosen == null) {
                // Nothing loads for this row: drop the previous row's picture rather than keep a wrong one.
                LaunchedEffect(current, index) { shown = null }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(calendarArtScrim()))
    val current = pick
    if (current != null) {
        CalendarLogo(
            logo = current.logo,
            name = row.name,
            height = 36.dp,
            maxWidth = 220.dp,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(NuvioTheme.spacing.md)
        )
    }
}
