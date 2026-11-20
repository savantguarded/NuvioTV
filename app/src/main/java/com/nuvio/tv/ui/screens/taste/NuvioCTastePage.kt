package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 40 `taste_v2`: the reworked Taste page.
//
// Top to bottom: "Taste" with the profile name and the range pills (All time, This year, 90 days, Month);
// the Taste DNA ring with its one-line summary beside the Spin my taste card (first focus); live tiles; six spotlight
// cards (redrawn each visit, each one opens what it describes); Your people; this visit's poster rows; Recaps.
// A month view swaps the live tiles for that month's numbers. The first build shows the genres forming from real
// lookup progress instead of a bare line. Charts are static native drawings; the only motion is the tile count-up
// (once, 200 ms), the first-build screen and marquees on focused, truncated text.
//
// Focus: Spin first; Down = live tiles are skipped (not focusable) so the spotlight's first card; Up from the hero =
// the pills. Every row restores the item you left and keeps its focused item at the row's start (no bounce), and a
// held Left on a row's first item stops there (a fresh press opens the sidebar).

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.data.nuvioc.NuvioCTasteState
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.NumberFormat
import java.util.Locale

private val NF: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

/** Ring and legend colours, a little muted so they sit on the dark page. */
internal val TASTE_RING = listOf(
    Color(0xFFF0607E), Color(0xFFF39A4A), Color(0xFFF2C95C), Color(0xFF4FC4BA), Color(0xFF5AA3F0), Color(0xFF9B8CF5)
)
/** Per-row memory of the last focused item, kept for the page's life (Back from a title lands where you were). */
class NuvioCTasteRowMemory { var index by mutableIntStateOf(0) }

/** First build: posters from the profile's history drift down into genre bars that grow as titles are read. */
@Composable
internal fun TasteBuilding(state: NuvioCTasteState, accent: Color) {
    val context = LocalContext.current
    val total = state.progressTotal.coerceAtLeast(1)
    val done = (total - state.pending).coerceIn(0, total)
    val transition = rememberInfiniteTransition(label = "tasteBuild")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("TASTE", style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextTertiary)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(520.dp).height(150.dp)) {
            state.progressPosters.take(5).forEachIndexed { i, url ->
                val phase = ((t + i * 0.2f) % 1f)
                val request = remember(url) { ImageRequest.Builder(context).data(url).size(108, 162).build() }
                AsyncImage(
                    model = request, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.offset(x = (40 + i * 96).dp).width(54.dp).height(81.dp).graphicsLayer {
                        translationY = phase * 70.dp.toPx()
                        val s = 1f - 0.6f * phase
                        scaleX = s; scaleY = s
                        alpha = when { phase < 0.2f -> phase / 0.2f; phase > 0.75f -> (1f - phase) / 0.25f; else -> 1f }
                    }.clip(RoundedCornerShape(6.dp))
                )
            }
        }
        val genres = state.progressGenres
        val max = (genres.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        Row(Modifier.height(80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            genres.forEachIndexed { i, (_, n) ->
                val h by animateFloatAsState(n.toFloat() / max, tween(600), label = "bar$i")
                Box(Modifier.width(48.dp).fillMaxHeight(h.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(TASTE_RING[i % TASTE_RING.size]))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            genres.forEach { (g, _) ->
                Text(g, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(48.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("${NF.format(done)} of ${NF.format(total)} titles read", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(220.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(done.toFloat() / total).background(accent))
        }
    }
}

// ---- focus helpers ---------------------------------------------------------------------------------------
