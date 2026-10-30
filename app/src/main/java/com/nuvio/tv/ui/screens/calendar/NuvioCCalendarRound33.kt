package com.nuvio.tv.ui.screens.calendar

// [fork] Nuvio C round 33 (switch `calendar_no_art_card`): what a Calendar picture shows when a title has no
// episode still, backdrop or poster at all (or none of them loads). Instead of the grey play-circle
// placeholder: a deep gradient tinted per show (the hue comes from its name, so it never changes between
// visits), the first letter very faint, and in large tiles the network above the name (two lines at most,
// a smaller size than the logo-fallback title). Thumbnails get the name on one line; the tiny month
// mosaic tiles get the gradient alone. Drawn with plain brushes, no images, so it costs nothing to show.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** How much text a title card carries. */
enum class NuvioCCardSize { LARGE, THUMB, BARE }

@Immutable
data class NuvioCCalendarCard(val name: String?, val network: String?, val size: NuvioCCardSize)

/** Stable hue (0..360) for a show name; pure, unit tested. */
internal fun nuvioCCardHue(name: String?): Float {
    val key = name?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return 220f
    var h = 0
    for (c in key) h = 31 * h + c.code
    return ((h % 360) + 360) % 360f
}

@Composable
internal fun NuvioCCalendarTitleCard(card: NuvioCCalendarCard, modifier: Modifier = Modifier) {
    val hue = nuvioCCardHue(card.name)
    val brush = remember(hue) {
        Brush.linearGradient(
            colors = listOf(
                Color.hsl(hue, 0.45f, 0.30f),
                Color.hsl(hue, 0.35f, 0.14f),
                Color.hsl(hue, 0.25f, 0.08f)
            ),
            start = Offset(0f, Float.POSITIVE_INFINITY),
            end = Offset(Float.POSITIVE_INFINITY, 0f)
        )
    }
    BoxWithConstraints(modifier.fillMaxSize().background(brush)) {
        val initial = card.name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (initial != null && card.size != NuvioCCardSize.BARE) {
            val big = maxHeight * 0.62f
            Text(
                text = initial.toString(),
                color = Color.White.copy(alpha = 0.07f),
                fontWeight = FontWeight.Bold,
                fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { big.toSp() },
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = maxWidth * 0.05f)
            )
        }
        when (card.size) {
            NuvioCCardSize.LARGE -> Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.78f).padding(start = 12.dp, bottom = 10.dp)
            ) {
                card.network?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = card.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            NuvioCCardSize.THUMB -> Text(
                text = card.name.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 5.dp, vertical = 4.dp)
            )
            NuvioCCardSize.BARE -> Box(Modifier)
        }
    }
}
