package com.nuvio.tv.ui.components

// [fork] Nuvio C round 20 `sidebar_page_smooth`.
//
// Picking a page in the Modern sidebar used to build the new page (Calendar, Library, Search, Settings,
// Discover) in the same frames as the sidebar's 220 ms close with its live blur, while the nav host
// cross-fades the old page out. On a 2 GB TV the three together stutter. Now the new page's place is
// held by its plain background until the sidebar has closed, then the page is built and fades in over
// 150 ms. Only a page opened from the sidebar waits; Back, deep links and other routes are untouched.
// Home is not gated (its own return-to-top and carousel handling stay as they are).

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.delay

object NuvioCSidebarSettle {
    /** The Modern sidebar's close (sidebar_close_smooth) plus a frame of margin. */
    internal const val SETTLE_MS = 240L
    internal const val FADE_MS = 150

    @Volatile
    private var drawerNavAt = 0L

    /** Called when a sidebar item opens a different page. */
    fun markDrawerNavigation() {
        drawerNavAt = SystemClock.uptimeMillis()
    }

    /** How long a page built now should still wait; 0 when it wasn't opened from the sidebar just now. */
    internal fun remainingWaitMs(now: Long = SystemClock.uptimeMillis()): Long =
        waitFor(now - drawerNavAt)

    internal fun waitFor(elapsedMs: Long): Long =
        if (elapsedMs < 0 || elapsedMs >= SETTLE_MS) 0L else SETTLE_MS - elapsedMs
}

@Composable
fun NuvioCSidebarSettleGate(content: @Composable () -> Unit) {
    if (!NuvioCFeatures.SIDEBAR_PAGE_SMOOTH) {
        content()
        return
    }
    val waitMs = remember { NuvioCSidebarSettle.remainingWaitMs() }
    var ready by remember { mutableStateOf(waitMs == 0L) }
    val alpha = remember { Animatable(if (waitMs == 0L) 1f else 0f) }
    if (!ready) {
        LaunchedEffect(Unit) {
            delay(waitMs)
            ready = true
        }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    // The alpha is read only in the layer (draw phase), so the fade never rebuilds the page.
    LaunchedEffect(Unit) {
        if (alpha.targetValue < 1f) alpha.animateTo(1f, tween(NuvioCSidebarSettle.FADE_MS))
    }
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) {
        content()
    }
}
