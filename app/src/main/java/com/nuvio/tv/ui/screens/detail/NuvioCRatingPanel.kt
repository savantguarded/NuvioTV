package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C, beta.3 merge (switch RATINGS_MULTI): official's rating panel look (score with its animation, the
// star row, Remove / Save) driving Nuvio C's multi-tracker sync. In place of official's single "Syncs to X" line,
// a "Sync to" row with one toggle per connected tracker (episodes: Trakt and MDBList only). Toggles apply to this
// rating only. Used for titles (dialog), episodes (inside the episode options page) and the post-play prompt.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.nuvioc.NuvioCTracker
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.settings.SettingsDialogActionButton
import com.nuvio.tv.ui.theme.NuvioTheme

/** The title / post-play rating dialog in official's look. */
@Composable
internal fun NuvioCRatingPanelDialog(
    label: String,
    title: String,
    subtitle: String?,
    state: NuvioCRatingUiState,
    onPick: (Int) -> Unit,
    onToggle: (NuvioCTracker) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    notNow: Boolean = false
) {
    NuvioDialog(onDismiss = onDismiss, title = "", width = 560.dp) {
        NuvioCRatingPanelContent(
            label = label, title = title, subtitle = subtitle, state = state,
            onPick = onPick, onToggle = onToggle, onSave = onSave, onRemove = onRemove,
            onNotNow = if (notNow) onDismiss else null
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCRatingPanelContent(
    label: String?,
    title: String?,
    subtitle: String?,
    state: NuvioCRatingUiState,
    onPick: (Int) -> Unit,
    onToggle: (NuvioCTracker) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onNotNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val starsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { starsFocus.requestFocusAfterFrames() }
    val value = state.score ?: 0
    val rated = state.display != null
    val canSave = !state.saving && value > 0 && state.ticked.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTheme.colors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (label != null || title != null || subtitle != null) Spacer(Modifier.height(20.dp))
        RatingScore(value = value)
        Spacer(Modifier.height(16.dp))
        RatingStars(
            value = value,
            onValueChange = { if (!state.saving) onPick(it) },
            onConfirm = { if (canSave) onSave() },
            focusRequester = starsFocus
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.nuvio_c_rate_sync_label),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            state.connected.forEach { tracker ->
                TrackerToggle(tracker.label, on = tracker in state.ticked, failed = tracker in state.failed, enabled = !state.saving) { onToggle(tracker) }
            }
        }
        if (state.failed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.nuvio_c_rate_failed, state.failed.joinToString { it.label }),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.Error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                onNotNow != null -> SettingsDialogActionButton(
                    text = stringResource(R.string.nuvio_c_rate_not_now), onClick = onNotNow,
                    enabled = !state.saving, modifier = Modifier.weight(1f)
                )
                rated -> SettingsDialogActionButton(
                    text = stringResource(R.string.rating_remove), onClick = onRemove,
                    enabled = !state.saving && state.ticked.isNotEmpty(), modifier = Modifier.weight(1f)
                )
            }
            SettingsDialogActionButton(
                text = stringResource(R.string.action_save), onClick = onSave, primary = true,
                enabled = canSave, modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackerToggle(name: String, on: Boolean, failed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (on) NuvioTheme.colors.Secondary.copy(alpha = 0.22f) else NuvioTheme.colors.BackgroundCard,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            focusedContentColor = NuvioTheme.colors.OnSecondary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, when {
                    failed -> NuvioTheme.colors.Error
                    on -> NuvioTheme.colors.Secondary
                    else -> NuvioTheme.colors.TextPrimary.copy(alpha = 0.18f)
                }),
                shape = shape
            ),
            focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
            if (on) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(name, style = MaterialTheme.typography.labelLarge)
        }
    }
}


/** [fork] beta.3 merge: an episode (or title) reference from official's tracking model, in the fork rating service's terms. */
internal fun com.nuvio.tv.core.tracking.TrackingMediaReference.nuvioCRatingTarget(): com.nuvio.tv.data.nuvioc.NuvioCRatingTarget? {
    val imdb = ids.imdb?.takeIf { it.startsWith("tt") }
    val tmdb = ids.tmdb?.toInt()
    if (imdb == null && tmdb == null) return null
    val ep = episode
    return com.nuvio.tv.data.nuvioc.NuvioCRatingTarget(
        isShow = ep != null || kind != com.nuvio.tv.core.tracking.TrackingMediaKind.MOVIE,
        imdbId = imdb,
        tmdbId = tmdb,
        season = ep?.season,
        episode = ep?.number
    )
}

/** [fork] beta.3 merge: multi-tracker rating state for one episode, for the episode options page. */
@Composable
internal fun rememberNuvioCEpisodeRating(media: com.nuvio.tv.core.tracking.TrackingMediaReference?): NuvioCRatingViewModel? {
    if (!com.nuvio.tv.NuvioCFeatures.RATINGS_MULTI || media == null) return null
    val target = remember(media) { media.nuvioCRatingTarget()?.takeIf { it.isEpisode } } ?: return null
    val vm: NuvioCRatingViewModel = androidx.hilt.navigation.compose.hiltViewModel(key = "nuvio_c_rate_" + target.key)
    LaunchedEffect(target) { vm.bind(target, media) }
    return vm
}
