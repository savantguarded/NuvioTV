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
 * [fork] round 27 startup_static (27d: Charles still saw the turning arc stutter on the 2 GB TV, so nothing moves):
 * the still logo only, the same smooth bitmap, size and centre as round 26c. With nothing animating there is
 * nothing that can hitch while the app works behind it.
 */
@Composable
fun NuvioCStartupStatic(logoRes: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val heightPx = with(density) { MARK_HEIGHT.roundToPx() }
    val logo = remember(logoRes, heightPx) { nuvioCSmoothBitmap(context.resources, logoRes, heightPx, markOnly = true) }
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
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

private val LOGO_HEIGHT = 48.dp
private val MARK_HEIGHT = 84.dp // [fork] roulette_page round 29: icon-only start-up screen

/**
 * The wordmark image is the triangle icon followed by the "Nuvio" lettering. Keeps only the icon: everything up to
 * the first fully transparent column after the icon begins (the gap before the lettering). Theme colours are kept.
 */
internal fun nuvioCCropToMark(source: android.graphics.Bitmap): android.graphics.Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    var seen = false
    var end = w
    for (x in 0 until w) {
        var opaque = false
        for (y in 0 until h) {
            if ((pixels[y * w + x] ushr 24) > 8) { opaque = true; break }
        }
        if (opaque) seen = true else if (seen) { end = x; break }
    }
    if (end <= 0 || end >= w) return source
    val cropped = android.graphics.Bitmap.createBitmap(source, 0, 0, end, h)
    if (cropped !== source) source.recycle()
    return cropped
}

/** Decodes [res] at full size and shrinks it to [targetHeight] px in halving steps (box-like averaging). */
internal fun nuvioCSmoothBitmap(resources: android.content.res.Resources, res: Int, targetHeight: Int, markOnly: Boolean = false): android.graphics.Bitmap? =
    runCatching {
        val options = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        var bitmap = android.graphics.BitmapFactory.decodeResource(resources, res, options) ?: return null
        if (markOnly) bitmap = nuvioCCropToMark(bitmap)
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
// [fork] round 33 startup_settle_images: longer caps, a 1.2 s minimum and a 1.4 s steady window
private const val SETTLE_IMAGES_CAP_MS = 14_000L
private const val SETTLE_IMAGES_SYNC_CAP_MS = 20_000L
private const val SETTLE_IMAGES_MIN_MS = 1_200L

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
            val images = NuvioCFeatures.STARTUP_SETTLE_IMAGES // [fork] round 33: also wait for the first rows' pictures
            val cap = when {
                images && NuvioCFeatures.STARTUP_SETTLE_SYNC -> SETTLE_IMAGES_SYNC_CAP_MS
                images -> SETTLE_IMAGES_CAP_MS
                NuvioCFeatures.STARTUP_SETTLE_SYNC -> SETTLE_SYNC_CAP_MS
                else -> SETTLE_CAP_MS
            }
            val pictures = !images || com.nuvio.tv.core.nuvioc.NuvioCStartup.imagesQuiet()
            val steady = if (images) nuvioCStartupSettled(gaps, quietMs = 1_400L) else nuvioCStartupSettled(gaps)
            if (elapsed >= cap || (elapsed >= (if (images) SETTLE_IMAGES_MIN_MS else SETTLE_MIN_MS) && !syncing && pictures && steady)) break
        }
        android.util.Log.i(
            "NuvioCStartup",
            "Home settled after ${(last - start) / 1_000_000L} ms behind the start-up screen" +
                " (pictures loading=${com.nuvio.tv.core.nuvioc.NuvioCStartup.imagesInFlight.get()})" +
                " (sync still running=${com.nuvio.tv.core.nuvioc.NuvioCStartup.syncRunning.value})"
        )
        settled = true
    }
    return settled
}
