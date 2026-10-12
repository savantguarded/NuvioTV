package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import com.nuvio.tv.NuvioCFeatures
import kotlin.math.max
import kotlin.math.sqrt

// [fork] Nuvio C clearlogo sizing (switch `logo_weight`), Apple TV style: every logo gets about the
// same area, so wide logos sit wide and short and tall ones (Chinatown) stay compact, instead of
// each logo filling one fixed box. The area is then capped by a max width and a max height.
// Until the logo has loaded (aspect unknown) the full max box is used; every place this is used
// is anchored so that box change moves nothing else (home and details grow upwards, the OSD logo
// hangs from the top-left).
//
// Sizes (dp): Home 12,500 dp² (max 260 x 80), details 9,000 dp² (max 220 x 64),
// player OSD 7,000 dp² (max 200 x 52). E.g. a 5:1 logo: home 250 x 50, details 212 x 42,
// OSD 187 x 37; a 2.4:1 logo: home 173 x 72, details 147 x 61, OSD 125 x 52.

data class NuvioCLogoBox(val area: Float, val maxWidth: Dp, val maxHeight: Dp) {
    /** Size for a logo with this width / height ratio; null (not loaded yet) = the max box. */
    fun sizeFor(aspect: Float?): DpSize {
        if (aspect == null || !aspect.isFinite() || aspect <= 0f) return DpSize(maxWidth, maxHeight)
        var w = sqrt(area * aspect)
        var h = w / aspect
        if (w > maxWidth.value) { w = maxWidth.value; h = w / aspect }
        if (h > maxHeight.value) { h = maxHeight.value; w = h * aspect }
        return DpSize(w.dp, h.dp)
    }
}

object NuvioCLogoSize {
    val enabled: Boolean get() = NuvioCFeatures.LOGO_WEIGHT

    val HOME = NuvioCLogoBox(area = 12_500f, maxWidth = 260.dp, maxHeight = 80.dp)
    val DETAIL = NuvioCLogoBox(area = 9_000f, maxWidth = 220.dp, maxHeight = 64.dp)
    val OSD = NuvioCLogoBox(area = 7_000f, maxWidth = 200.dp, maxHeight = 52.dp)

    /** Width / height of a loaded logo, from Coil's success state. */
    fun aspectOf(state: AsyncImagePainter.State.Success): Float? {
        val s = state.painter.intrinsicSize
        if (!s.width.isFinite() || !s.height.isFinite() || s.height <= 0f) return null
        return s.width / s.height
    }
}

/** Top clearance for the home title block, so a logo never runs into the top of the screen (the clock sits at 20 dp). */
val NUVIO_C_HOME_HERO_TOP: Dp = 24.dp

/**
 * [fork] Home hero: the text part is measured first and always gets its full height; the logo gets
 * what is left above it (up to its own size), so the block stays bottom-anchored, grows upwards
 * and never pushes past the top clearance or squeezes the synopsis.
 */
@Composable
fun NuvioCHeroStack(
    gap: Dp,
    modifier: Modifier = Modifier,
    logo: @Composable () -> Unit,
    rest: @Composable () -> Unit
) {
    Layout(contents = listOf(logo, rest), modifier = modifier) { (logoMeasurables, restMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val restPlaceable = restMeasurables.firstOrNull()?.measure(loose)
        val restHeight = restPlaceable?.height ?: 0
        val gapPx = gap.roundToPx()
        val left = if (constraints.hasBoundedHeight) (constraints.maxHeight - restHeight - gapPx).coerceAtLeast(0) else Constraints.Infinity
        val logoPlaceable = logoMeasurables.firstOrNull()?.measure(loose.copy(maxHeight = left))
        val logoHeight = logoPlaceable?.height ?: 0
        val g = if (logoHeight > 0 && restPlaceable != null) gapPx else 0
        val width = max(restPlaceable?.width ?: 0, logoPlaceable?.width ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (logoHeight + g + restHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            logoPlaceable?.place(0, 0)
            restPlaceable?.place(0, logoHeight + g)
        }
    }
}
