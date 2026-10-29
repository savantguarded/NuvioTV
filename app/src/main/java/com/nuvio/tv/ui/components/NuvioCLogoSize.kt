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
// Sizes (dp): Home 17,000 dp² (max 340 x 110, never more than the free height above the text), details 9,000 dp² (max 220 x 64),
// player OSD 7,000 dp² (max 200 x 52). E.g. a 5:1 logo: home 250 x 50, details 212 x 42,
// OSD 187 x 37; a 2.4:1 logo: home 173 x 72, details 147 x 61, OSD 125 x 52.
// Square / tall logos (logo_tall_boost, 2026-10-15): a 1:1 logo is home 140 x 140, details 92 x 92,
// OSD 68 x 68 (was 110, 64 and 52 tall).

data class NuvioCLogoBox(
    val area: Float,
    val maxWidth: Dp,
    val maxHeight: Dp,
    /** 2026-10-15 (switch `logo_tall_boost`): height cap for square / tall logos (aspect 1.0 or less). */
    val tallMaxHeight: Dp = maxHeight
) {
    /** Size for a logo with this width / height ratio; null (not loaded yet) = the max box. */
    fun sizeFor(aspect: Float?): DpSize {
        if (aspect == null || !aspect.isFinite() || aspect <= 0f) return DpSize(maxWidth, maxHeight)
        // 2026-10-15: equal area made square / tall logos look small (we judge them by height) and the
        // wide-logo height cap shrank them further. Below 1.6:1 the area grows up to +25% and the
        // height cap rises towards tallMaxHeight; wide logos are unchanged.
        val t = if (NuvioCFeatures.LOGO_TALL_BOOST) ((TALL_FROM - aspect) / (TALL_FROM - 1f)).coerceIn(0f, 1f) else 0f
        val a = area * (1f + 0.25f * t)
        val capH = maxHeight.value + (tallMaxHeight.value - maxHeight.value).coerceAtLeast(0f) * t
        var w = sqrt(a * aspect)
        var h = w / aspect
        if (w > maxWidth.value) { w = maxWidth.value; h = w / aspect }
        if (h > capH) { h = capH; w = h * aspect }
        return DpSize(w.dp, h.dp)
    }

    private companion object {
        const val TALL_FROM = 1.6f
    }
}

object NuvioCLogoSize {
    val enabled: Boolean get() = NuvioCFeatures.LOGO_WEIGHT

    val HOME = NuvioCLogoBox(area = 17_000f, maxWidth = 340.dp, maxHeight = 110.dp, tallMaxHeight = 140.dp) // 2026-10-12: was 12,500 dp² / 260 x 80; still capped by the free height above the text (NuvioCHeroStack)
    /** [fork] round 32 ambient_logo_size: the screensaver's logo, sized by area like Home (max 460 x 150). */
    val AMBIENT = NuvioCLogoBox(area = 24_000f, maxWidth = 460.dp, maxHeight = 150.dp, tallMaxHeight = 170.dp)
    val DETAIL = NuvioCLogoBox(area = 9_000f, maxWidth = 220.dp, maxHeight = 64.dp, tallMaxHeight = 92.dp)
    val OSD = NuvioCLogoBox(area = 7_000f, maxWidth = 200.dp, maxHeight = 52.dp, tallMaxHeight = 68.dp)

    /** Width / height of a loaded logo, from Coil's success state (also remembered for [known]). */
    fun aspectOf(state: AsyncImagePainter.State.Success): Float? {
        val s = state.painter.intrinsicSize
        if (!s.width.isFinite() || !s.height.isFinite() || s.height <= 0f) return null
        val aspect = s.width / s.height
        (state.result.request.data as? String)?.let { aspects.put(it, aspect) }
        return aspect
    }

    // 2026-10-12: aspects already seen this session, so a logo shown again (the details hero
    // scrolling back into view, Home returning to a title) starts at its real size instead of the
    // max box and nothing shifts when it loads.
    private val aspects = android.util.LruCache<String, Float>(300)

    fun known(url: String?): Float? = url?.let { aspects.get(it) }
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
    minLogoHeight: Dp = 0.dp,
    logo: @Composable () -> Unit,
    rest: @Composable () -> Unit
) {
    Layout(contents = listOf(logo, rest), modifier = modifier) { (logoMeasurables, restMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // 2026-10-12: the text never takes the logo's minimum height (a 6th synopsis line ellipsizes instead).
        val gapPx0 = gap.roundToPx()
        val restMax = if (constraints.hasBoundedHeight && minLogoHeight > 0.dp) (constraints.maxHeight - minLogoHeight.roundToPx() - gapPx0).coerceAtLeast(0) else constraints.maxHeight
        val restPlaceable = restMeasurables.firstOrNull()?.measure(loose.copy(maxHeight = restMax))
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
