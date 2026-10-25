package com.nuvio.tv.ui.components

// [fork] Nuvio C round 28: the poster reel shared by Shuffle and Library Random pick.
//
// A strip of posters slides past a fixed centre frame, fast at first and then slowing down
// continuously until the chosen title sits in the frame. Why it's smooth on a 2 GB TV:
//  - every poster is fetched and decoded at its exact on-screen size BEFORE the strip moves
//    (same memory key the strip uses, so drawing them is an instant memory hit);
//  - the whole strip moves as one layer (graphicsLayer translationX read from an Animatable),
//    so nothing is laid out or recomposed while it moves, and no image is swapped;
//  - the slow-down is one exponential ease-out curve, so speed falls without steps.
// The pick is decided before the reel starts; the reel only shows it.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow

/** One spin of the reel: [strip] posters left to right, landing on [landIndex]. A new [token] starts a new spin. */
@Immutable
data class NuvioCReelSpin(val token: Int, val strip: List<String?>, val landIndex: Int)

private const val REEL_FILLERS = 12

/**
 * Builds a spin that starts on [from] (the poster showing now, so a re-roll continues from it), runs past
 * up to 12 [others] and lands on [pick], with two more posters after it so the strip doesn't end at the frame.
 * With fewer than 3 others there's nothing to spin through, so it lands at once.
 */
fun nuvioCReelSpin(token: Int, pick: String?, others: List<String?>, from: String?): NuvioCReelSpin {
    val pool = others.filter { !it.isNullOrBlank() && it != pick }.distinct()
    if (pool.size < 3) return NuvioCReelSpin(token, listOf(pick), 0)
    val shuffled = pool.shuffled()
    val fillers = List(REEL_FILLERS) { shuffled[it % shuffled.size] }
    val tail = List(2) { shuffled[(REEL_FILLERS + it) % shuffled.size] }
    val first = from?.takeIf { it.isNotBlank() } ?: shuffled.last()
    val strip = listOf(first) + fillers + listOf(pick) + tail
    return NuvioCReelSpin(token, strip, 1 + REEL_FILLERS)
}

/** Fast start, long smooth slow-down (exponential ease-out). */
private val ReelEasing = Easing { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t) }

/** One request per poster and size, so the warm-up and the strip share the same memory-cache entry. */
private fun reelRequest(context: android.content.Context, url: String, w: Int, h: Int): ImageRequest =
    ImageRequest.Builder(context.applicationContext)
        .data(url)
        .size(width = w, height = h)
        .memoryCacheKey("nuvioc_reel_${w}x${h}_$url")
        .diskCacheKey(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()

/**
 * The reel. [edgeColor] is the colour behind it (the side fades blend into it). [onLanded] gets the spin's token
 * once the pick is in the frame (at once when there's nothing to spin through).
 */
@Composable
internal fun NuvioCPosterReel(
    spin: NuvioCReelSpin,
    cardWidth: Dp,
    cardHeight: Dp,
    edgeColor: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 14.dp,
    edgeFade: Dp = 72.dp,
    durationMs: Int = 1800,
    onLanded: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { cardWidth.roundToPx() }
    val hPx = with(density) { cardHeight.roundToPx() }
    val stepPx = with(density) { (cardWidth + gap).toPx() }
    val requests = remember(spin, wPx, hPx) { spin.strip.map { url -> url?.takeIf { it.isNotBlank() }?.let { reelRequest(context, it, wPx, hPx) } } }
    val offset = remember { Animatable(0f) }
    val landed by rememberUpdatedState(onLanded)
    val frameRoom = 6.dp

    LaunchedEffect(spin.token) {
        offset.snapTo(0f)
        if (spin.landIndex <= 0) {
            landed(spin.token)
            return@LaunchedEffect
        }
        // Warm-up: decode every strip poster at its exact size (6 at a time), at most 0.6 s.
        val loader = context.imageLoader
        withTimeoutOrNull(600L) {
            requests.filterNotNull().distinctBy { it.memoryCacheKey }.chunked(6).forEach { chunk ->
                coroutineScope { chunk.map { req -> async { runCatching { loader.execute(req) } } }.awaitAll() }
            }
        }
        offset.animateTo(spin.landIndex * stepPx, tween(durationMs, easing = ReelEasing))
        landed(spin.token)
    }

    BoxWithConstraints(modifier.height(cardHeight + frameRoom * 2).clipToBounds()) {
        val startPx = with(density) { (maxWidth - cardWidth).toPx() / 2f }
        Row(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .fillMaxHeight()
                .graphicsLayer { translationX = startPx - offset.value },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            requests.forEach { request ->
                Box(
                    Modifier
                        .size(cardWidth, cardHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NuvioTheme.colors.BackgroundCard)
                ) {
                    if (request != null) {
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(cardWidth, cardHeight)
                        )
                    }
                }
            }
        }
        if (spin.landIndex > 0) {
            Box(Modifier.align(Alignment.CenterStart).width(edgeFade).fillMaxHeight().background(Brush.horizontalGradient(listOf(edgeColor, edgeColor.copy(alpha = 0f)))))
            Box(Modifier.align(Alignment.CenterEnd).width(edgeFade).fillMaxHeight().background(Brush.horizontalGradient(listOf(edgeColor.copy(alpha = 0f), edgeColor))))
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(cardWidth + frameRoom, cardHeight + frameRoom)
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
        )
    }
}
