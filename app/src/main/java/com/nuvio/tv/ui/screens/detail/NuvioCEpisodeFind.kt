package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 43 `episode_find`: a small Find chip at the end of the season tabs on every show. It opens a
// field in place of the tabs; the episodes row underneath shows the matches across all seasons (with the season in
// each card's code). Matching is local (the episodes the page already has), no network.
//
//   12 / e12      -> episode 12 of every season, in season order, then the 12th episode overall (anime) if different
//   s3e5 / 3x05   -> that one episode
//   words         -> episodes whose name holds every word (accents ignored)
//
// Back closes Find and puts focus back on the chip; the season you were on is unchanged. Playing a match switches the
// page to that episode's season first, so coming back from the player lands on it like any other episode.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.Normalizer

/** True inside Find results: episode cards show the season in their code ("S2 · EPISODE 5"). */
internal val LocalNuvioCEpisodeSeasonCode = compositionLocalOf { false }

private const val MAX_RESULTS = 80

/** The episodes matching [query], in the order described above. Pure, for tests. */
internal fun nuvioCFindEpisodes(videos: List<Video>, query: String): List<Video> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val episodes = videos.filter { it.season != null && it.episode != null }
        .sortedWith(compareBy<Video>({ if (it.season == 0) Int.MAX_VALUE else it.season!! }, { it.episode!! }))
    Regex("""^s?\s*(\d{1,3})\s*[ex]\s*(\d{1,4})$""").find(q)?.let { m ->
        val s = m.groupValues[1].toInt(); val e = m.groupValues[2].toInt()
        return episodes.filter { it.season == s && it.episode == e }
    }
    Regex("""^(?:e|ep|episode)?\s*(\d{1,4})$""").find(q)?.let { m ->
        val n = m.groupValues[1].toInt()
        val bySeason = episodes.filter { it.episode == n && it.season != 0 }
        val absolute = episodes.filter { it.season != 0 }.getOrNull(n - 1)
        return (if (absolute != null && absolute !in bySeason) bySeason + absolute else bySeason).take(MAX_RESULTS)
    }
    val words = fold(q).split(Regex("\\s+")).filter { it.isNotBlank() }
    return episodes.filter { v ->
        val name = fold(v.title.orEmpty())
        words.all { it in name }
    }.take(MAX_RESULTS)
}

private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** The chip at the end of the season tabs. */
@Composable
internal fun NuvioCEpisodeFindChip(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties {
                right = FocusRequester.Cancel
                upFocusRequester?.let { up = it }
                downFocusRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = NuvioTheme.colors.Secondary
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(20.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
        ) {
            val color = if (focused) NuvioTheme.colors.OnSecondary else NuvioTheme.extendedColors.textSecondary
            Icon(Icons.Default.Search, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.nuvio_c_find), style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

/** The field shown in place of the season tabs while finding. */
@Composable
internal fun NuvioCEpisodeFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
    fieldFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { fieldFocusRequester.requestFocus() }
        keyboard?.show()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = com.nuvio.tv.ui.components.nuvioCDetailMargin, vertical = NuvioTheme.spacing.xl)
    ) {
        Box(
            modifier = Modifier
                .width(380.dp)
                .height(44.dp)
                .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(22.dp))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, tint = NuvioTheme.colors.TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.nuvio_c_find_hint), style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.extendedColors.textTertiary, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { onQueryChange(it.take(60)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NuvioTheme.colors.TextPrimary),
                        cursorBrush = SolidColor(NuvioTheme.colors.TextPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            if (resultCount > 0) runCatching { downFocusRequester?.requestFocus() }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocusRequester)
                            .focusProperties {
                                upFocusRequester?.let { up = it }
                                down = if (resultCount > 0 && downFocusRequester != null) downFocusRequester else FocusRequester.Cancel
                            }
                            .onFocusChanged { focused = it.isFocused }
                    )
                }
            }
        }
        val countText = when {
            query.isBlank() -> ""
            resultCount == 0 -> stringResource(R.string.nuvio_c_find_none)
            resultCount == 1 -> stringResource(R.string.nuvio_c_find_one)
            else -> stringResource(R.string.nuvio_c_find_matches, resultCount)
        }
        Text(countText, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.extendedColors.textSecondary, maxLines = 1)
    }
}
