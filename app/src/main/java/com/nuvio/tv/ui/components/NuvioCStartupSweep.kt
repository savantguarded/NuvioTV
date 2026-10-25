package com.nuvio.tv.ui.components

import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.nuvioCStartupSettled

// [fork] Nuvio C round 26b `startup_sweep` and `startup_settle`. See core/nuvioc/NuvioCStartup.kt.

/** The loading sweep under the logo; an AnimatedVectorDrawable in a plain ImageView, so the render thread animates it. */
@Composable
fun NuvioCStartupSweep(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                val sweep = context.getDrawable(R.drawable.nuvio_c_loading_sweep)
                setImageDrawable(sweep)
                (sweep as? Animatable)?.start()
            }
        },
        onRelease = { view -> (view.drawable as? Animatable)?.stop() },
        modifier = modifier.size(width = 160.dp, height = 6.dp)
    )
}

/**
 * startup_breathe: the logo drawn smoothly over a slowly breathing halo. The halo is an AnimatedVectorDrawable
 * (render thread). The logo PNG is far larger than it is shown (1085 px wide for about 150 dp), and drawing it
 * scaled down in one go leaves jagged edges, so it is shrunk once in halving steps, which averages the edge pixels.
 */
@Composable
fun NuvioCStartupBreathe(logoRes: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val heightPx = with(density) { LOGO_HEIGHT.roundToPx() }
    val logo = remember(logoRes, heightPx) { nuvioCSmoothBitmap(context.resources, logoRes, heightPx) }
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    val halo = ctx.getDrawable(R.drawable.nuvio_c_startup_halo)
                    setImageDrawable(halo)
                    (halo as? Animatable)?.start()
                }
            },
            onRelease = { view -> (view.drawable as? Animatable)?.stop() },
            modifier = Modifier.size(width = 420.dp, height = 220.dp)
        )
        if (logo != null) {
            val widthDp = with(density) { logo.width.toDp() }
            val heightDp = with(density) { logo.height.toDp() }
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageBitmap(logo)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        this.contentDescription = contentDescription
                    }
                },
                modifier = Modifier.size(width = widthDp, height = heightDp)
            )
        }
    }
}

/**
 * [fork] round 27 startup_arc (27b: no by-line, thin arc instead of the morph): the still logo (same smooth
 * bitmap, size and centre as round 26c) with a hairline turning arc centred underneath. The arc is an
 * AnimatedVectorDrawable played by the render thread; nothing else on the screen animates.
 */
@Composable
fun NuvioCStartupArc(logoRes: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val heightPx = with(density) { LOGO_HEIGHT.roundToPx() }
    val logo = remember(logoRes, heightPx) { nuvioCSmoothBitmap(context.resources, logoRes, heightPx) }
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            if (logo != null) {
                val widthDp = with(density) { logo.width.toDp() }
                val heightDp = with(density) { logo.height.toDp() }
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            setImageBitmap(logo)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            this.contentDescription = contentDescription
                        }
                    },
                    modifier = Modifier.size(width = widthDp, height = heightDp)
                )
            } else {
                androidx.compose.foundation.layout.Box(Modifier.size(width = 150.dp, height = LOGO_HEIGHT))
            }
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        val arc = ctx.getDrawable(R.drawable.nuvio_c_startup_arc)
                        setImageDrawable(arc)
                        (arc as? Animatable)?.start()
                    }
                },
                onRelease = { view -> (view.drawable as? Animatable)?.stop() },
                modifier = Modifier.size(30.dp)
            )
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val logoP = measurables[0].measure(loose)
        val loaderP = measurables[1].measure(loose)
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val logoY = (h - logoP.height) / 2
        layout(w, h) {
            logoP.place((w - logoP.width) / 2, logoY)
            loaderP.place((w - loaderP.width) / 2, logoY + logoP.height + 44.dp.roundToPx())
        }
    }
}

private val LOGO_HEIGHT = 48.dp

/** Decodes [res] at full size and shrinks it to [targetHeight] px in halving steps (box-like averaging). */
internal fun nuvioCSmoothBitmap(resources: android.content.res.Resources, res: Int, targetHeight: Int): android.graphics.Bitmap? =
    runCatching {
        val options = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        var bitmap = android.graphics.BitmapFactory.decodeResource(resources, res, options) ?: return null
        val targetWidth = (bitmap.width.toLong() * targetHeight / bitmap.height).toInt().coerceAtLeast(1)
        while (bitmap.height / 2 >= targetHeight) {
            val next = android.graphics.Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
            if (next !== bitmap) bitmap.recycle()
            bitmap = next
        }
        if (bitmap.height != targetHeight) {
            val next = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (next !== bitmap) bitmap.recycle()
            bitmap = next
        }
        bitmap
    }.getOrNull()

private const val SETTLE_CAP_MS = 10_000L
private const val SETTLE_SYNC_CAP_MS = 15_000L
private const val SETTLE_MIN_MS = 600L

/**
 * startup_settle: false from the moment Home reports ready ([ready]) until it has settled, then true for good.
 * Settled = every frame answered within 50 ms for 0.9 s in a row (at least 0.6 s after ready) and, with
 * STARTUP_SETTLE_SYNC, no start-up sync running; 10 s at most (15 s with the sync wait).
 * Keyed on [key] (the start-up state, renewed on a profile switch). Always true with the switch off.
 */
@Composable
fun rememberNuvioCStartupSettled(key: Any, ready: Boolean): Boolean {
    if (!NuvioCFeatures.STARTUP_SETTLE) return true
    var settled by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, ready) {
        if (!ready || settled) return@LaunchedEffect
        val gaps = ArrayList<Long>()
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            gaps += (now - last) / 1_000_000L
            if (gaps.size > 240) gaps.subList(0, gaps.size - 240).clear()
            last = now
            val elapsed = (now - start) / 1_000_000L
            // startup_settle_sync: on a cold start the full watched-history sync is what freezes Home, so wait it out.
            val syncing = NuvioCFeatures.STARTUP_SETTLE_SYNC && com.nuvio.tv.core.nuvioc.NuvioCStartup.syncRunning.value
            val cap = if (NuvioCFeatures.STARTUP_SETTLE_SYNC) SETTLE_SYNC_CAP_MS else SETTLE_CAP_MS
            if (elapsed >= cap || (elapsed >= SETTLE_MIN_MS && !syncing && nuvioCStartupSettled(gaps))) break
        }
        android.util.Log.i(
            "NuvioCStartup",
            "Home settled after ${(last - start) / 1_000_000L} ms behind the start-up screen" +
                " (sync still running=${com.nuvio.tv.core.nuvioc.NuvioCStartup.syncRunning.value})"
        )
        settled = true
    }
    return settled
}
