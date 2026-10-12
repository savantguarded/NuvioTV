package com.nuvio.tv.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C hero spec (switch `hero_spec`): one size and scale for the title block on
// Home / collection folders (Modern layout) and on the details page.
//
// - Logo fitted in a 260 × 90 dp box on both (official: 220 × 100 on home, up to 40% of the width on
//   details). Wide logos are limited by the width, tall ones by the height, never cropped.
// - Synopsis 13 sp on 19 sp lines on both, in poster and landscape modes alike.
//   Home shows 5 lines (official 4); details keeps official's 8 lines before "Read more".
// - The text block ends at about half the screen on both (home official 43%, details 60% of the
//   column), and the home fade is longer and darker so every letter sits on at least ~65% dark.
//
// Switch off: everything here falls back to the official values at each call site.
object NuvioCHeroSpec {
    val enabled: Boolean get() = NuvioCFeatures.HERO_SPEC

    val logoMaxWidth: Dp = 260.dp
    val logoHeight: Dp = 90.dp

    const val HOME_SYNOPSIS_LINES = 5

    /** Home: share of the space between the 52 dp row padding and the 48 dp end padding (ends ~50% of a 960 dp screen). */
    const val HOME_TEXT_FRACTION = 0.50f

    /** Details: share of the hero column (48 dp each side), same end point as home. */
    const val DETAIL_TEXT_FRACTION = 0.50f

    /** Home fade (not full-screen backdrop): fade length as a share of the backdrop width (official 0.45). */
    const val HOME_FADE_END = 0.58f

    fun synopsisStyle(base: TextStyle): TextStyle = base.copy(fontSize = 13.sp, lineHeight = 19.sp)
}
