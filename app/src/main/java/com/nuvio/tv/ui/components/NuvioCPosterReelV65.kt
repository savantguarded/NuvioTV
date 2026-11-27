package com.nuvio.tv.ui.components

// [fork] Nuvio C round 65 ROULETTE_SPIN_V65: the reel never shows a still poster.
//  - A pending spin (the pick is still being found) moves at once at a constant speed over posters already in memory,
//    looping without a seam; when the pick arrives the strip eases out from where it is and lands on it (1.8 s).
//  - While it moves every poster is blurred and dimmed to ~45%. The blur is a tiny decode (1/8 size) drawn upscaled,
//    never a live RenderEffect (2 GB TV): one small bitmap per poster, no extra work per frame.
//  - The centre frame shows a softly pulsing "?"; on landing the pick sharpens (tiny -> full-size crossfade) with
//    "YOUR PICK" in the frame while its neighbours stay dim, then the spin reports landed.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** Posters per second while a pending spin waits for its pick. */
private const val REEL_PENDING_SPEED = 8f
/** Posters the strip still travels after the pick arrives (with the cubic ease-out its start speed ~ the pending one). */
private const val REEL_LAND_STEPS = 5
/** Spinning posters are drawn at this brightness. */
private const val REEL_DIM_ALPHA = 0.55f
/** Tiny decode for the blur: 1/8 of the card (round 66 REEL_V66: 1/16). */
private val REEL_BLUR_DIVISOR get() = when {
    com.nuvio.tv.NuvioCFeatures.REEL_BLUR_V67 -> 4 // [fork] round 67: 1/4 decode plus a one-off stack blur (no blocky pixels)
    com.nuvio.tv.NuvioCFeatures.REEL_V66 -> 16
    else -> 8
}
/** [fork] round 67 REEL_BLUR_V67: stack blur radius on the 1/4 bitmap, run once per poster by Coil off the main thread. */
internal const val REEL_BLUR_RADIUS_67 = 6
/** [fork] round 66: spinning posters at ~33% brightness, slightly desaturated. */
private val REEL_DIM get() = if (com.nuvio.tv.NuvioCFeatures.REEL_V66) 0.67f else REEL_DIM_ALPHA
private val ReelDesaturate = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0.55f) })
/** Posters a full spin travels before the pick when no pending phase was drawn. */
private const val REEL_FULL_STEPS = 12

/**
 * [fork] round 66 REEL_V66: a landing needs a full spin of its own when its pending phase was never composed (the pick
 * arrived in the same frame) or the reel still holds the previous landing. Pure, for tests.
 */
internal fun nuvioCReelLandingNeedsFullSpin(continues: Boolean, cycleEmpty: Boolean, previousPickIndex: Int): Boolean =
    com.nuvio.tv.NuvioCFeatures.REEL_V66 && continues && (cycleEmpty || previousPickIndex >= 0)

private val ReelEasing65 = Easing { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t) }
private val ReelLandEasing = Easing { t -> val u = 1f - t; 1f - u * u * u }

/** A spin that waits for its pick: [fillers] loop at a constant speed. The landing spin keeps the same token. */
fun nuvioCReelPending(token: Int, fillers: List<String?>): NuvioCReelSpin =
    NuvioCReelSpin(token, fillers.filter { !it.isNullOrBlank() }.distinct().ifEmpty { listOf(null, null, null) }, -1, pending = true)

/** The landing after a pending spin: [pick] plus two posters after it so the strip doesn't end at the frame. */
fun nuvioCReelLanding(token: Int, pick: String?, others: List<String?>): NuvioCReelSpin {
    val tail = others.filter { !it.isNullOrBlank() && it != pick }.distinct().shuffled().take(2)
    return NuvioCReelSpin(token, listOf(pick) + tail, 0, continues = true)
}

/** Strip positions [from, from + count) mapped onto the looping [cycle]. Pure, for tests. */
internal fun nuvioCReelWindow(cycle: List<String?>, from: Int, count: Int): List<String?> =
    if (cycle.isEmpty()) List(count) { null } else List(count) { cycle[(from + it).mod(cycle.size)] }

/** [fork] round 67: memory cache key of a blurred reel poster. Pure, for tests. */
internal fun nuvioCReelBlurKey(w: Int, h: Int, url: String): String = "nuvioc_reel_blur_${REEL_BLUR_RADIUS_67}_${w}x${h}_$url"

private fun tinyRequest(context: android.content.Context, url: String, w: Int, h: Int): ImageRequest {
    val tw = (w / REEL_BLUR_DIVISOR).coerceAtLeast(8)
    val th = (h / REEL_BLUR_DIVISOR).coerceAtLeast(12)
    if (com.nuvio.tv.NuvioCFeatures.REEL_BLUR_V67) {
        // [fork] round 67: blurred once by Coil and kept in the memory cache under its own key; no per-frame work
        return ImageRequest.Builder(context.applicationContext)
            .data(url)
            .size(width = tw, height = th)
            .transformations(com.nuvio.tv.ui.util.BlurTransformation(REEL_BLUR_RADIUS_67))
            .memoryCacheKey(nuvioCReelBlurKey(tw, th, url))
            .diskCacheKey(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    return ImageRequest.Builder(context.applicationContext)
        .data(url)
        .size(width = tw, height = th)
        .memoryCacheKey("nuvioc_reel_tiny_${tw}x${th}_$url")
        .diskCacheKey(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()
}

private fun fullRequest(context: android.content.Context, url: String, w: Int, h: Int): ImageRequest =
    ImageRequest.Builder(context.applicationContext)
        .data(url)
        .size(width = w, height = h)
        .memoryCacheKey("nuvioc_reel_${w}x${h}_$url")
        .diskCacheKey(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()

@Composable
internal fun NuvioCPosterReelV65(
    spin: NuvioCReelSpin,
    cardWidth: Dp,
    cardHeight: Dp,
    edgeColor: Color,
    modifier: Modifier,
    gap: Dp,
    edgeFade: Dp,
    durationMs: Int,
    onLanded: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { cardWidth.roundToPx() }
    val hPx = with(density) { cardHeight.roundToPx() }
    val stepPx = with(density) { (cardWidth + gap).toPx() }
    val stepDp = (cardWidth + gap).value
    // Posters either side of the frame needed to cover the widest reel (the screen), plus one.
    val half = ceil(LocalConfiguration.current.screenWidthDp / 2f / stepDp).toInt() + 1
    val offset = remember { Animatable(0f) }
    val sharpen = remember { Animatable(0f) }
    val landed by rememberUpdatedState(onLanded)
    var items by remember { mutableStateOf<List<String?>>(emptyList()) }
    var pickIndex by remember { mutableIntStateOf(-1) }
    var cycle by remember { mutableStateOf<List<String?>>(emptyList()) }
    val frameRoom = 6.dp
    val loader = context.imageLoader

    suspend fun warm(urls: List<String?>, full: String?, timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            val reqs = urls.filterNotNull().distinct().map { tinyRequest(context, it, wPx, hPx) } +
                listOfNotNull(full?.let { fullRequest(context, it, wPx, hPx) })
            reqs.chunked(6).forEach { chunk -> coroutineScope { chunk.map { async { runCatching { loader.execute(it) } } }.awaitAll() } }
        }
    }

    LaunchedEffect(spin.token, spin.pending, spin.continues) {
        sharpen.snapTo(0f)
        when {
            !spin.pending && nuvioCReelLandingNeedsFullSpin(spin.continues, cycle.isEmpty(), pickIndex) -> {
                // [fork] round 66: spin in full over fillers (the last loop's posters, else the landing's own), then land.
                val fill = cycle.ifEmpty { spin.strip.drop(1) }.ifEmpty { listOf<String?>(null, null, null) }
                items = nuvioCReelWindow(fill, 0, half + REEL_FULL_STEPS) + spin.strip
                pickIndex = half + REEL_FULL_STEPS
                cycle = emptyList()
                offset.snapTo(half * stepPx)
                warm(fill, spin.strip.firstOrNull(), 250L)
                offset.animateTo(pickIndex * stepPx, tween(1800, easing = ReelLandEasing))
                sharpen.animateTo(1f, tween(450))
                delay(350)
                landed(spin.token)
            }
            spin.pending -> {
                // Loop the fillers: [half] posters before the frame, one full period, [half] after.
                val c = spin.strip
                cycle = c
                pickIndex = -1
                val lead = ceil(half / c.size.toFloat()).toInt() * c.size
                items = nuvioCReelWindow(c, 0, lead + c.size + half + 1)
                val base = lead * stepPx
                val period = c.size * stepPx
                offset.snapTo(base)
                warm(c, null, 250L) // already in memory in practice; never holds the start long
                val msPerPeriod = (c.size / REEL_PENDING_SPEED * 1000f).toInt().coerceAtLeast(1)
                while (true) {
                    offset.animateTo(base + period, tween(((base + period - offset.value) / period * msPerPeriod).toInt().coerceAtLeast(1), easing = LinearEasing))
                    offset.snapTo(base)
                }
            }
            spin.continues && cycle.isNotEmpty() && pickIndex < 0 -> {
                // Carry on from where the looping strip is: same posters at the same spots, then the pick.
                val o = offset.value
                val k0 = floor(o / stepPx).toInt()
                val frac = o - k0 * stepPx
                val before = nuvioCReelWindow(cycle, k0 - half, half + REEL_LAND_STEPS)
                items = before + spin.strip
                pickIndex = half + REEL_LAND_STEPS
                offset.snapTo(half * stepPx + frac)
                offset.animateTo(pickIndex * stepPx, tween(durationMs, easing = ReelLandEasing))
                sharpen.animateTo(1f, tween(450))
                delay(350)
                landed(spin.token)
            }
            else -> {
                // Pick known at once (Library Random pick, or nothing to spin through).
                cycle = emptyList()
                items = spin.strip
                pickIndex = spin.landIndex
                offset.snapTo(0f)
                if (spin.landIndex > 0) {
                    warm(spin.strip, spin.strip.getOrNull(spin.landIndex), 600L)
                    offset.animateTo(spin.landIndex * stepPx, tween(durationMs, easing = ReelEasing65))
                } else {
                    warm(emptyList(), spin.strip.getOrNull(0), 400L)
                }
                sharpen.animateTo(1f, tween(450))
                delay(350)
                landed(spin.token)
            }
        }
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
            items.forEachIndexed { index, url ->
                val isPick = index == pickIndex
                Box(
                    Modifier
                        .size(cardWidth, cardHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NuvioTheme.colors.BackgroundCard)
                ) {
                    if (!url.isNullOrBlank()) {
                        val tiny = remember(url, wPx, hPx) { tinyRequest(context, url, wPx, hPx) }
                        AsyncImage(model = tiny, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            colorFilter = if (com.nuvio.tv.NuvioCFeatures.REEL_V66) ReelDesaturate else null)
                        if (isPick) {
                            val full = remember(url, wPx, hPx) { fullRequest(context, url, wPx, hPx) }
                            AsyncImage(
                                model = full,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = sharpen.value }
                            )
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = if (isPick) 1f - sharpen.value else 1f
                        }.background(Color.Black.copy(alpha = REEL_DIM))
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.CenterStart).width(edgeFade).fillMaxHeight().background(Brush.horizontalGradient(listOf(edgeColor, edgeColor.copy(alpha = 0f)))))
        Box(Modifier.align(Alignment.CenterEnd).width(edgeFade).fillMaxHeight().background(Brush.horizontalGradient(listOf(edgeColor.copy(alpha = 0f), edgeColor))))
        val glowColor = NuvioTheme.colors.FocusRing
        Box(
            Modifier
                .align(Alignment.Center)
                .size(cardWidth + frameRoom, cardHeight + frameRoom)
                .then(
                    if (com.nuvio.tv.NuvioCFeatures.POSTER_GLOW_EVERYWHERE && com.nuvio.tv.NuvioCFeatures.POSTER_GLOW) Modifier.drawBehind {
                        val r = 14.dp.toPx()
                        drawIntoCanvas { canvas ->
                            val paint = androidx.compose.ui.graphics.Paint().apply {
                                color = Color.Transparent
                                asFrameworkPaint().setShadowLayer(14.dp.toPx(), 0f, 0f, glowColor.copy(alpha = 0.5f).toArgb())
                                style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                                strokeWidth = 3.dp.toPx()
                            }
                            canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
                        }
                    } else Modifier
                )
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
        ) {
            val pulse by rememberInfiniteTransition(label = "reelQ").animateFloat(
                initialValue = 0.35f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "reelQAlpha"
            )
            Text(
                text = "?",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = pulse * (1f - sharpen.value) }
            )
            Text(
                text = androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.nuvio_c_reel_your_pick),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .graphicsLayer { alpha = sharpen.value }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
