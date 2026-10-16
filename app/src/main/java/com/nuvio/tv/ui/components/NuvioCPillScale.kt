package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C pill focus growth (switch `pill_focus_growth`, 2026-10-17). tv Button zooms 1.1x on
// focus, so a ~200 dp pill (Play / Resume, Details) grew about 10 dp on each side and ate the 12 dp
// gap to its neighbour; the round 48 dp buttons grow only 2.4 dp. Pills now grow by that same 2.4 dp
// per side, so the gap next to any focused button is the same (~8 dp with the 2 dp ring).
internal object NuvioCPillScale {
    val enabled: Boolean get() = NuvioCFeatures.PILL_FOCUS_GROWTH
    /** Growth per side of a round 48 dp button at 1.1x. */
    val GROWTH_PER_SIDE = 2.4.dp
}

internal class NuvioCPillScaleState(val modifier: Modifier, val scale: ButtonScale)

/** Width-aware focus scale for a pill Button. Width is read once per layout change, not per frame. */
@Composable
internal fun rememberNuvioCPillScale(): NuvioCPillScaleState? {
    if (!NuvioCPillScale.enabled) return null
    var widthPx by remember { mutableIntStateOf(0) }
    val growthPx = with(LocalDensity.current) { NuvioCPillScale.GROWTH_PER_SIDE.toPx() }
    val focused = if (widthPx > 0) (1f + 2f * growthPx / widthPx).coerceIn(1f, 1.1f) else 1.03f
    val modifier = remember { Modifier.onSizeChanged { widthPx = it.width } }
    return NuvioCPillScaleState(modifier, ButtonDefaults.scale(focusedScale = focused))
}
