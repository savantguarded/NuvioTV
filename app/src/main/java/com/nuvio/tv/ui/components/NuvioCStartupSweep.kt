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

private const val SETTLE_CAP_MS = 10_000L
private const val SETTLE_MIN_MS = 600L

/**
 * startup_settle: false from the moment Home reports ready ([ready]) until it has settled, then true for good.
 * Settled = every frame answered within 50 ms for 0.9 s in a row (at least 0.6 s after ready), or 10 s at most.
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
            if (elapsed >= SETTLE_CAP_MS || (elapsed >= SETTLE_MIN_MS && nuvioCStartupSettled(gaps))) break
        }
        android.util.Log.i("NuvioCStartup", "Home settled after ${(last - start) / 1_000_000L} ms behind the start-up screen")
        settled = true
    }
    return settled
}
