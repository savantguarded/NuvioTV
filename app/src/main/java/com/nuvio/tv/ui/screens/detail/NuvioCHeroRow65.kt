package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * [fork] round 65 HERO_ROW_V65: one size for every Play / Open pill and the round buttons beside it (details hero,
 * Featured carousel, Roulette result, Library Random pick): pill 44 dp tall with 16 dp sides (text size unchanged),
 * round buttons 44 dp with 20 dp glyphs (vector icons 22 dp), 8 dp between buttons. Off = 48 dp / 24 dp / 12 dp.
 */
internal object NuvioCHeroRow65 {
    val on: Boolean get() = com.nuvio.tv.NuvioCFeatures.HERO_ROW_V65

    /** Pill padding: 12 dp above / below a 20 dp line = 44 dp. */
    val pillPadding: PaddingValues
        @Composable get() = if (on) PaddingValues(horizontal = 16.dp, vertical = 12.dp) else PaddingValues(horizontal = NuvioTheme.spacing.xl, vertical = 14.dp)

    /** Padding for a plain tv-material Button used as a pill elsewhere (Roulette, Library Random pick). */
    val buttonPadding: PaddingValues get() = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    val round: Dp @Composable get() = if (on) 44.dp else NuvioTheme.spacing.xxxl
    val glyph: Dp get() = if (on) 20.dp else 22.dp
    val vectorGlyph: Dp @Composable get() = if (on) 22.dp else NuvioTheme.spacing.xl
    val gap: Dp @Composable get() = if (on) 8.dp else NuvioTheme.spacing.md
}
