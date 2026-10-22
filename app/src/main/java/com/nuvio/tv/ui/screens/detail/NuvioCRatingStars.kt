package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C rating_stars (round 19, 2026-10-22): the rate dialog's score picker as five stars
// with half steps. One focus stop for the whole bar: Right adds half a star (one point), Left takes
// one off, 1 to 10 as before (3½ stars = 7). OK picks the score shown. Under it: the word for the
// score on the left, "8/10" on the right. Unrated titles open on 5, drawn faint until you pick.

private val STAR_GOLD = Color(0xFFF5C518)
private val STAR_SIZE = 40.dp

/** Fraction (0..1) of star [index] (0-based) filled at [score] (1..10, half star per point). */
internal fun nuvioCStarFill(score: Int, index: Int): Float = ((score / 2f) - index).coerceIn(0f, 1f)

@Composable
internal fun NuvioCStarBar(
    score: Int?,
    cursorStart: Int,
    enabled: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var cursor by remember { mutableIntStateOf((score ?: cursorStart).coerceIn(1, 10)) }
    var focused by remember { mutableStateOf(false) }
    val picked = score != null
    val shown = score ?: cursor
    val words = stringArrayResource(R.array.nuvio_c_rate_words)
    val shape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> { cursor = (shown + 1).coerceAtMost(10); onPick(cursor); true }
                        Key.DirectionLeft -> { cursor = (shown - 1).coerceAtLeast(1); onPick(cursor); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onPick(shown); true }
                        else -> false
                    }
                }
                .focusable(enabled)
                .border(2.dp, if (focused) NuvioTheme.colors.TextPrimary else Color.Transparent, shape)
                .padding(vertical = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 5) {
                    val fill = nuvioCStarFill(shown, i)
                    Box(Modifier.size(STAR_SIZE)) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = NuvioTheme.colors.TextPrimary.copy(alpha = 0.18f),
                            modifier = Modifier.size(STAR_SIZE)
                        )
                        if (fill > 0f) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (picked) STAR_GOLD else STAR_GOLD.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(STAR_SIZE)
                                    .drawWithContent { clipRect(right = size.width * fill) { this@drawWithContent.drawContent() } }
                            )
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Text(
                text = if (picked) words.getOrElse(shown - 1) { "" } else stringResource(R.string.nuvio_c_rate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
            Text(
                text = stringResource(R.string.nuvio_c_rate_out_of_ten, shown),
                style = MaterialTheme.typography.titleSmall,
                color = if (picked) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary
            )
        }
    }
}
