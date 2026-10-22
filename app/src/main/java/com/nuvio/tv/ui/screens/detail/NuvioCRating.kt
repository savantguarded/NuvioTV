package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C "Rate" on the details page (2026-10-10): star button next to Watched, and a
// dialog in the app's own style (same as the library list picker): 1-10, which trackers get it
// (Trakt / MDBList, only the connected ones), Save, Remove rating. One way only, nothing is read
// back from the trackers; see NuvioCRatingService. Switch: NuvioCFeatures.DETAIL_RATING.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.data.nuvioc.NuvioCRatingService
import com.nuvio.tv.data.nuvioc.NuvioCRatingTarget
import com.nuvio.tv.data.nuvioc.NuvioCTracker
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Movie / show ids the trackers understand, or null (no star button). */
internal fun Meta.nuvioCRatingTarget(): NuvioCRatingTarget? {
    val isShow = type == ContentType.SERIES || type == ContentType.TV ||
        apiType.equals("series", ignoreCase = true) || apiType.equals("tv", ignoreCase = true)
    if (!isShow && type != ContentType.MOVIE && !apiType.equals("movie", ignoreCase = true)) return null
    val imdb = imdbId?.takeIf { it.startsWith("tt") }
        ?: id.takeIf { it.startsWith("tt") }?.substringBefore(":")
    val tmdb = id.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(":")?.toIntOrNull()
    if (imdb == null && tmdb == null) return null
    return NuvioCRatingTarget(isShow, imdb, tmdb)
}

data class NuvioCRatingUiState(
    val target: NuvioCRatingTarget? = null,
    val connected: List<NuvioCTracker> = emptyList(),
    val saved: Int? = null,
    /** Your score on Trakt (pulled), shown in the dialog. */
    val trakt: Int? = null,
    /** Your score on Simkl (from Nuvio's Simkl sync copy), used when Trakt has none. */
    val simkl: Int? = null,
    val open: Boolean = false,
    val score: Int? = null,
    val ticked: Set<NuvioCTracker> = emptySet(),
    val saving: Boolean = false,
    val failed: Set<NuvioCTracker> = emptySet(),
    val needScore: Boolean = false
) {
    val available: Boolean get() = target != null && connected.isNotEmpty()
}

@HiltViewModel
class NuvioCRatingViewModel @Inject constructor(
    private val service: NuvioCRatingService
) : ViewModel() {
    private val _state = MutableStateFlow(NuvioCRatingUiState())
    val state = _state.asStateFlow()

    fun bind(target: NuvioCRatingTarget?) {
        if (target == _state.value.target && _state.value.connected.isNotEmpty()) return
        _state.value = NuvioCRatingUiState(target = target)
        if (target == null) return
        viewModelScope.launch {
            // [fork] off the UI thread: prefs load, the Trakt copy file and the ratings JSON (up to ~150 KB)
            // were read / parsed on the main thread as the details page opened.
            val (connected, saved) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                service.connected() to service.savedScore(target)
            }
            _state.update { it.copy(connected = connected, saved = saved) }
            // Trakt copy: memory lookup, refreshed at most every 15 min (tiny request)
            val trakt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { service.traktScore(target) }
            if (trakt != null && _state.value.target == target) {
                _state.update { if (it.open) it.copy(trakt = trakt) else it.copy(trakt = trakt, saved = trakt) }
            }
            // [fork] rating_simkl: Simkl's copy fills in when Trakt has no score for this title
            if (trakt == null && NuvioCFeatures.RATING_SIMKL) {
                val simkl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { service.simklScore(target) }
                if (simkl != null && _state.value.target == target) {
                    _state.update { if (it.open) it.copy(simkl = simkl) else it.copy(simkl = simkl, saved = it.saved ?: simkl) }
                }
            }
        }
    }

    fun open() = _state.update { s ->
        val remembered = service.lastChoice()?.intersect(s.connected.toSet())?.takeIf { it.isNotEmpty() }
        s.copy(open = true, score = s.saved, ticked = remembered ?: s.connected.toSet(), failed = emptySet(), needScore = false)
    }

    fun close() = _state.update { it.copy(open = false, saving = false) }
    fun pick(score: Int) = _state.update { it.copy(score = score, needScore = false) }
    fun toggle(tracker: NuvioCTracker) = _state.update { s ->
        s.copy(ticked = if (tracker in s.ticked) s.ticked - tracker else s.ticked + tracker, failed = emptySet())
    }

    fun save(remove: Boolean = false) {
        val s = _state.value
        val target = s.target ?: return
        if (s.saving || s.ticked.isEmpty()) return
        val score = if (remove) null else s.score ?: run { _state.update { it.copy(needScore = true) }; return }
        _state.update { it.copy(saving = true, failed = emptySet()) }
        viewModelScope.launch {
            val failed = service.send(target, score, s.ticked)
            val saved = service.savedScore(target)
            _state.update {
                if (failed.isEmpty()) it.copy(saving = false, open = false, saved = saved,
                    trakt = if (NuvioCTracker.TRAKT in s.ticked) score else it.trakt,
                    simkl = if (NuvioCTracker.SIMKL in s.ticked) score else it.simkl)
                // keep only the failed ones ticked, so Save retries just those
                else it.copy(saving = false, saved = saved, failed = failed, ticked = failed)
            }
        }
    }
}

/** Star button state for the hero row, plus the dialog when it is open. */
class NuvioCRatingHandle(val available: Boolean, val rated: Boolean, val open: () -> Unit)

@Composable
internal fun rememberNuvioCRating(meta: Meta, starFocusRequester: FocusRequester): NuvioCRatingHandle {
    if (!NuvioCFeatures.DETAIL_RATING) return NuvioCRatingHandle(false, false) {}
    val viewModel: NuvioCRatingViewModel = hiltViewModel()
    val target = remember(meta.id, meta.imdbId, meta.type) { meta.nuvioCRatingTarget() }
    LaunchedEffect(target) { viewModel.bind(target) }
    val state by viewModel.state.collectAsState()
    if (state.open) {
        NuvioCRatingDialog(
            title = stringResource(R.string.nuvio_c_rate_your_rating),
            subtitle = meta.name,
            state = state,
            onPick = viewModel::pick,
            onToggle = viewModel::toggle,
            onSave = { viewModel.save() },
            onRemove = { viewModel.save(remove = true) },
            onDismiss = viewModel::close
        )
    }
    // focus back on the star when the dialog closes
    var wasOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(state.open) {
        if (wasOpen && !state.open) runCatching { starFocusRequester.requestFocus() }
        wasOpen = state.open
    }
    return NuvioCRatingHandle(state.available, state.saved != null, viewModel::open)
}

@Composable
private fun NuvioCRatingDialog(
    title: String,
    subtitle: String?,
    state: NuvioCRatingUiState,
    onPick: (Int) -> Unit,
    onToggle: (NuvioCTracker) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    val startScore = state.score ?: 5 // 2026-10-11: middle of the scale (was 7)
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // round 20 rate_dialog_fit: md gaps (was lg) so three trackers and the buttons fit on a 1080p TV
    NuvioDialog(onDismiss = onDismiss, title = title, subtitle = subtitle, width = 520.dp,
        contentSpacing = if (NuvioCFeatures.RATE_DIALOG_FIT) NuvioTheme.spacing.md else NuvioTheme.spacing.lg) {
        if (NuvioCFeatures.RATING_STARS) {
            // [fork] rating_stars (round 19): five stars with half steps, one focus stop
            NuvioCStarBar(
                score = state.score,
                cursorStart = startScore,
                enabled = !state.saving,
                onPick = onPick,
                modifier = Modifier.focusRequester(firstFocus)
            )
        } else {
        // 2026-10-11: circles sized from the row width (10 circles, 9 gaps of at least 8 dp), no
        // zoom on focus (the 1.1x zoom made neighbours overlap); focus = white fill + ring inside.
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val circle = ((maxWidth - 8.dp * 9) / 10).coerceIn(28.dp, 44.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (v in 1..10) {
                val selected = state.score == v
                Button(
                    onClick = { onPick(v) },
                    enabled = !state.saving,
                    shape = ButtonDefaults.shape(shape = CircleShape),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, NuvioTheme.colors.TextPrimary),
                            inset = 2.dp,
                            shape = CircleShape
                        )
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier
                        .size(circle)
                        .then(if (v == startScore) Modifier.focusRequester(firstFocus) else Modifier),
                    colors = ButtonDefaults.colors(
                        containerColor = if (selected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                        contentColor = NuvioTheme.colors.TextPrimary,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = v.toString(),
                            textAlign = TextAlign.Center,
                            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        } // BoxWithConstraints
        } // rating_stars else

        val nuvioCYourScore = state.trakt ?: state.simkl ?: state.saved // 2026-10-12: "You rated this 8/10" (Trakt copy is the fresher one, then Simkl, then this TV)
        if (nuvioCYourScore != null && !NuvioCFeatures.RATING_STARS) { // rating_stars: the stars open on it and show it
            Text(
                text = stringResource(R.string.nuvio_c_rate_trakt_score, nuvioCYourScore),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
        }

        // 2026-10-11 round 4: one settings-style toggle row per tracker ("Sync to Trakt").
        // round 20 rate_dialog_fit: rows exactly as tall as their content (48 dp; the settings row's 62 dp
        // minimum left the label and switch sitting high), and if a fourth tracker ever arrives this list
        // scrolls instead of pushing Save off the dialog.
        Column(
            verticalArrangement = Arrangement.spacedBy(if (NuvioCFeatures.RATE_DIALOG_FIT) 6.dp else 8.dp),
            modifier = if (NuvioCFeatures.RATE_DIALOG_FIT) {
                // 4 dp inside the scroll: the focus ring sits just outside a row and the scroll clips at its edges
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)
            } else Modifier
        ) {
            state.connected.forEach { tracker ->
                com.nuvio.tv.ui.screens.settings.SettingsToggleRow(
                    title = stringResource(R.string.nuvio_c_rate_sync_to, tracker.label),
                    subtitle = null,
                    checked = tracker in state.ticked,
                    onToggle = { onToggle(tracker) },
                    enabled = !state.saving,
                    modifier = if (NuvioCFeatures.RATE_DIALOG_FIT) Modifier.height(48.dp) else Modifier
                )
            }
        }

        val error = when {
            state.failed.isNotEmpty() -> stringResource(
                R.string.nuvio_c_rate_failed,
                state.failed.joinToString(" / ") { it.label }
            )
            state.needScore -> stringResource(R.string.nuvio_c_rate_pick_score)
            state.ticked.isEmpty() -> stringResource(R.string.nuvio_c_rate_pick_tracker)
            else -> null
        }
        if (error != null) {
            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFB6B6))
        }

        HorizontalDivider(color = NuvioTheme.colors.Border, thickness = NuvioTheme.spacing.hairline)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.saved != null) {
                Button(
                    onClick = onRemove,
                    enabled = !state.saving,
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundCard,
                        contentColor = NuvioTheme.colors.TextPrimary
                    )
                ) { Text(stringResource(R.string.nuvio_c_rate_remove)) }
            }
            Button(
                onClick = onSave,
                enabled = !state.saving,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(if (state.saving) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
            }
        }
    }
}
