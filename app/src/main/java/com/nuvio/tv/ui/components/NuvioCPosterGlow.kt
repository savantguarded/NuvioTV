package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C round 51 `poster_glow`: the focused poster / card gets a light halo in the chosen accent colour.
// It is drawn outside the card by the card component itself (same layer as the focus ring and zoom), so it never
// changes a card's size, the spacing between cards or what the neighbours do. One helper for every card type, so a
// card added later only needs `glow = nuvioCPosterCardGlow()`. Nothing is drawn for unfocused cards.

/** The focused glow for [LightweightCard] and other cards that take a plain [Glow]; [Glow.None] when switched off. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun nuvioCPosterGlow(fallback: Glow = Glow.None): Glow {
    if (!NuvioCFeatures.POSTER_GLOW) return fallback
    val accent = NuvioTheme.colors.FocusRing
    return remember(accent) { Glow(elevationColor = accent.copy(alpha = 0.42f), elevation = 12.dp) }
}

/** The same glow for tv-material [androidx.tv.material3.Card]s. [fallback] is used when the switch is off. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun nuvioCPosterCardGlow(fallback: CardGlow = CardDefaults.glow()): CardGlow {
    if (!NuvioCFeatures.POSTER_GLOW) return fallback
    val glow = nuvioCPosterGlow()
    return remember(glow) { CardDefaults.glow(focusedGlow = glow) }
}
