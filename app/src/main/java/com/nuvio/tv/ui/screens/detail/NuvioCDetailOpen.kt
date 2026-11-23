package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 61 `detail_open_smooth` (2026-11-23). owner: opening a details page felt abrupt and janky.
// Causes: official's loading placeholder has a different shape from the Nuvio C hero (one pill + one circle, a 100 dp
// logo block, no facts / ratings / badge lines), and the page replaced it with a hard cut in the same frame that
// built the whole screen for the first time.
// Now: the placeholder follows the Nuvio C hero (logo box, button row, facts line, ratings, synopsis, quality line) and
// an episode-card row; when the page is ready it fades in over the placeholder in 180 ms; the rows under the hero join
// one frame later (see MetaDetailsContent). Fresh opens only: a return from Sources / the player draws as before.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.crossfade
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.components.nuvioCDetailMargin
import com.nuvio.tv.ui.components.rememberShimmerBrush
import com.nuvio.tv.ui.theme.NuvioTheme

internal const val NUVIO_C_OPEN_FADE_MS = 180

/**
 * 0..1 reveal of the page. Starts at 0 only in the first frame after a placeholder was on screen (a fresh open);
 * otherwise 1, so returns and reloads draw at once.
 */
@Composable
internal fun rememberNuvioCOpenReveal(loading: Boolean, enabled: Boolean): Animatable<Float, *> {
    val sawLoading = remember { booleanArrayOf(false) }
    if (loading) sawLoading[0] = true
    val reveal = remember(loading) {
        Animatable(if (!loading && enabled && NuvioCFeatures.DETAIL_OPEN_SMOOTH && sawLoading[0]) 0f else 1f)
    }
    LaunchedEffect(reveal) {
        if (reveal.value < 1f) {
            sawLoading[0] = false
            reveal.animateTo(1f, tween(NUVIO_C_OPEN_FADE_MS))
        }
    }
    return reveal
}

/** The loading placeholder in the Nuvio C hero's shape (hero spec sizes), then one row of episode cards. */
@Composable
internal fun NuvioCDetailSkeleton(backdropAware: Boolean) {
    val brush = rememberShimmerBrush(backdropAware = backdropAware)
    Box(
        Modifier.fillMaxSize().background(if (backdropAware) Color.Transparent else NuvioTheme.colors.Background)
    ) {
        if (backdropAware) Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background.copy(alpha = 0.35f)))
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().height(540.dp)
                    .padding(start = nuvioCDetailMargin, end = nuvioCDetailMargin, bottom = NuvioTheme.spacing.lg),
                verticalArrangement = Arrangement.Bottom
            ) {
                Bar(200.dp, 64.dp, brush, 10.dp) // logo box
                Spacer(Modifier.height(NuvioTheme.spacing.lg + 7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Bar(150.dp, 48.dp, brush, 24.dp) // Play
                    repeat(4) { Box(Modifier.width(48.dp).height(48.dp).clip(CircleShape).background(brush)) }
                }
                Spacer(Modifier.height(NuvioTheme.spacing.md))
                Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bar(40.dp, 15.dp, brush, 5.dp); Bar(180.dp, 10.dp, brush, 5.dp) // [rating] • genres • seasons
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) { Bar(170.dp, 12.dp, brush, 6.dp) } // ratings
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(bottom = NuvioTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Bar(440.dp, 10.dp, brush, 5.dp); Bar(440.dp, 10.dp, brush, 5.dp); Bar(290.dp, 10.dp, brush, 5.dp) // synopsis
                }
                Row(Modifier.height(17.dp).padding(bottom = 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bar(84.dp, 10.dp, brush, 5.dp); Bar(26.dp, 17.dp, brush, 3.dp); Bar(45.dp, 17.dp, brush, 3.dp); Bar(45.dp, 17.dp, brush, 3.dp) // year • duration • badges
                }
                Spacer(Modifier.height(NuvioTheme.spacing.md))
            }
            Row(
                Modifier.padding(start = nuvioCDetailMargin, top = NuvioTheme.spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
            ) {
                repeat(5) { Bar(260.dp, 146.dp, brush, 12.dp) } // episode / cast cards
            }
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, brush: Brush, radius: Dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(radius)).background(brush))
}

/**
 * [fork] round 62 detail_open_fast: what sits under the page while it fades in. The same picture the loading screen
 * showed (same request, so Coil's memory cache hands it straight back) and the placeholder, so the fade goes from the
 * placeholder to the page and never through black.
 */
@Composable
internal fun NuvioCOpenUnderlay(backdropUrl: String?) {
    if (!backdropUrl.isNullOrBlank()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val request = remember(context, backdropUrl, configuration, density) {
            val w = with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val h = with(density) { configuration.screenHeightDp.dp.roundToPx() }
            coil3.request.ImageRequest.Builder(context).data(backdropUrl).crossfade(false).size(width = w, height = h).build()
        }
        coil3.compose.AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.TopEnd
        )
    }
    NuvioCDetailSkeleton(backdropAware = !backdropUrl.isNullOrBlank())
}
