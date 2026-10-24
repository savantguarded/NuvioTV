package com.nuvio.tv.ui.screens.library

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C round 27 `library_random`: a Random pick button after Sync. It picks from exactly what the
// page shows (uiState.visibleItems: the current list, type, genre, year and watched filter) and skips the last
// few picks while the page has enough titles. The dialog shows the poster, title, year and two lines of the
// description, with Pick again and Open (Open is focused and goes to the details page, as a poster click does).
// Mockup: artifact "Nuvio C Round 27".

/** Gaps between roll frames: quick, then slowing like a reel (about 0.85 s in all). */
private val ROLL_STEPS_MS = longArrayOf(55, 60, 70, 85, 105, 130, 160, 190)

/** Remembers the last few picks so Pick again doesn't bounce between the same titles. */
internal class NuvioCRandomPicker {
    private val recent = ArrayDeque<String>()

    fun pick(items: List<LibraryEntry>): LibraryEntry? {
        if (items.isEmpty()) return null
        val keep = (items.size - 1).coerceIn(0, RECENT_MAX)
        while (recent.size > keep) recent.removeFirst()
        val fresh = items.filter { it.key() !in recent }
        val choice = (fresh.ifEmpty { items }).random()
        recent.addLast(choice.key())
        while (recent.size > keep) recent.removeFirst()
        return choice
    }

    private fun LibraryEntry.key() = "$type:$id"

    private companion object { const val RECENT_MAX = 5 }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCRandomPickButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            contentColor = NuvioTheme.colors.TextPrimary
        )
    ) {
        Icon(
            painter = painterResource(R.drawable.nuvio_c_ic_shuffle),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.nuvio_c_random_pick))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCRandomPickDialog(
    item: LibraryEntry,
    subtitle: String?,
    pool: List<LibraryEntry>,
    pickToken: Int,
    onPickAgain: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val openRequester = remember { FocusRequester() }
    // The roll: the poster flicks through a few other titles from the page, slowing like a reel, then the pick
    // settles (96% -> 100%) and its text fades in. Only the poster's image source changes during the roll, and the
    // scale / fade are read in graphicsLayer, so nothing is laid out again. Posters come from the page, which
    // has mostly loaded them already.
    var shown by remember { androidx.compose.runtime.mutableStateOf(item) }
    var rolling by remember { androidx.compose.runtime.mutableStateOf(false) }
    val settle = remember { androidx.compose.animation.core.Animatable(1f) }
    val textAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pickToken) {
        val others = pool.filter { it.poster != null && !(it.id == item.id && it.type == item.type) }.shuffled().take(ROLL_STEPS_MS.size)
        if (others.size >= 3) {
            rolling = true
            textAlpha.snapTo(0f)
            others.forEachIndexed { i, entry ->
                shown = entry
                kotlinx.coroutines.delay(ROLL_STEPS_MS[i])
            }
        }
        shown = item
        rolling = false
        settle.snapTo(0.96f)
        kotlinx.coroutines.coroutineScope {
            launch { settle.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f)) }
            launch { textAlpha.animateTo(1f, androidx.compose.animation.core.tween(220)) }
        }
    }
    // Each pick lands back on Open, so the remote's centre key always means "go to this one".
    LaunchedEffect(item.id, item.type) {
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        runCatching { openRequester.requestFocus() }
    }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.nuvio_c_random_pick),
        subtitle = subtitle,
        width = 440.dp,
        contentSpacing = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val poster = shown.poster ?: shown.rawPosterUrl
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(198.dp)
                    .graphicsLayer {
                        scaleX = settle.value
                        scaleY = settle.value
                        alpha = if (rolling) 0.75f else 1f
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(NuvioTheme.colors.BackgroundCard)
            ) {
                if (!poster.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(false).build(),
                        contentDescription = shown.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(132.dp).height(198.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = textAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val year = item.releaseInfo?.takeIf { it.isNotBlank() } ?: " " // keeps the line's height
            run {
                Text(
                    text = year,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1
                )
            }
            val description = item.description?.takeIf { it.isNotBlank() } ?: " "
            run {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2, // fixed height, so Pick again never resizes the dialog
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = { if (!rolling) onPickAgain() },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Icon(painterResource(R.drawable.nuvio_c_ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nuvio_c_random_pick_again))
            }
            Button(
                onClick = { if (!rolling) onOpen() },
                modifier = Modifier.focusRequester(openRequester),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nuvio_c_random_open))
            }
        }
    }
}

/**
 * [fork] round 27 `library_down_first`: Down from a header row (list pickers, Manage lists / Sync / Random pick)
 * goes to the first poster. Official left it to the geometry, which picked the poster under the focused
 * control (often the second column). Falls back to normal focus search if that poster isn't on screen.
 */
internal fun Modifier.nuvioCDownToFirst(target: () -> FocusRequester?): Modifier =
    if (!NuvioCFeatures.LIBRARY_DOWN_FIRST) this else onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) return@onPreviewKeyEvent false
        if (event.nativeKeyEvent.keyCode != AndroidKeyEvent.KEYCODE_DPAD_DOWN) return@onPreviewKeyEvent false
        val requester = target() ?: return@onPreviewKeyEvent false
        runCatching { requester.requestFocus() }.getOrDefault(false)
    }
