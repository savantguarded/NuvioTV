package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nuvio.tv.domain.model.MetaPreview
import android.os.SystemClock
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

// [fork] Nuvio C hero_autoadvance (2026-10-15). Official HeroCarousel only advanced while it did NOT
// have focus, and Classic gives it focus at app start, so it sat still; every focus change and every
// hero-list size change also restarted a 20 s wait. Here: the slide changes after 10 s without a key
// press, focused or not; a key press or a slide change restarts the 10 s; nothing runs while the
// screen isn't resumed (app in background, details / player on top).
internal object NuvioCHeroAutoAdvance {
    const val INTERVAL_MS = 10_000L

    /** Keeps the same title on screen when the list is rebuilt (enrichment, catalogs arriving, refresh). */
    fun indexAfterListChange(items: List<MetaPreview>, activeId: String?, currentIndex: Int): Int {
        if (items.isEmpty()) return 0
        val byId = activeId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
        return if (byId >= 0) byId else currentIndex.coerceIn(0, items.size - 1)
    }
}

@Composable
internal fun NuvioCHeroAutoAdvanceEffect(
    count: Int,
    activeIndex: Int,
    interaction: Int,
    onAdvance: () -> Unit
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val advance by rememberUpdatedState(onAdvance)
    LaunchedEffect(count > 1, resumed, activeIndex, interaction) {
        if (count <= 1 || !resumed) return@LaunchedEffect
        delay(NuvioCHeroAutoAdvance.INTERVAL_MS)
        advance()
    }
}

/**
 * [fork] idle_timer_light (2026-10-17): when the last key press happened, kept outside Compose state, so
 * a press doesn't rebuild the carousel (the old counter was screen state read by the timer effect).
 */
internal class NuvioCIdleClock {
    @Volatile var lastMs: Long = SystemClock.uptimeMillis()
        private set

    fun touch() {
        lastMs = SystemClock.uptimeMillis()
    }
}

@Composable
internal fun rememberNuvioCIdleClock(): NuvioCIdleClock = remember { NuvioCIdleClock() }

/** Same rule as above (10 s without a key press, only while resumed), timed from [clock]. */
@Composable
internal fun NuvioCHeroAutoAdvanceEffect(
    count: Int,
    activeIndex: Int,
    clock: NuvioCIdleClock,
    onAdvance: () -> Unit
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val advance by rememberUpdatedState(onAdvance)
    LaunchedEffect(count > 1, resumed, activeIndex) {
        if (count <= 1 || !resumed) return@LaunchedEffect
        val startedMs = SystemClock.uptimeMillis()
        while (true) {
            val from = maxOf(startedMs, clock.lastMs)
            val waitMs = from + NuvioCHeroAutoAdvance.INTERVAL_MS - SystemClock.uptimeMillis()
            if (waitMs <= 0) break
            delay(waitMs)
        }
        advance()
    }
}
