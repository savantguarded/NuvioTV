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
// - Home synopsis 13 sp on 19 sp lines, 5 lines (official 4), in poster and landscape modes alike.
//   The home logo takes only the height it needs (wide logos are often ~50 dp tall, not 90), and
//   the title block may grow upwards, so the 5th line is never squeezed out by the space above
//   the rows (that was why it still showed 4 lines).
// - Details synopsis 12 sp on 17 sp, 8 lines before "Read more", ending at ~38% of the screen,
//   72% opacity until focused (Apple TV grey).
// - Home text block ends at about half the screen (official 43%), and the home fade is longer and
//   darker so every letter sits on at least ~65% dark.
//
// Switch off: everything here falls back to the official values at each call site.
object NuvioCHeroSpec {
    val enabled: Boolean get() = NuvioCFeatures.HERO_SPEC

    val logoMaxWidth: Dp = 260.dp
    val logoHeight: Dp = 90.dp

    const val HOME_SYNOPSIS_LINES = 5

    /** Home: share of the space between the 52 dp row padding and the 48 dp end padding (ends ~50% of a 960 dp screen). */
    const val HOME_TEXT_FRACTION = 0.50f

    /** Details: share of the hero column (48 dp each side). Narrower than home, Apple TV style (~38% of the screen). */
    const val DETAIL_TEXT_FRACTION = 0.42f

    /**
     * Details synopsis opacity when not focused (focused = full). Apple TV draws it as translucent
     * white over the darkened backdrop (tvOS vibrancy), sampled at about 74%, so it reads grey and
     * picks up the picture's tint. Over a background trailer it multiplies with the 85% text dim.
     */
    const val DETAIL_SYNOPSIS_ALPHA = 0.72f

    /** Home fade (not full-screen backdrop): fade length as a share of the backdrop width (official 0.45). */
    const val HOME_FADE_END = 0.58f

    /**
     * Home synopsis as one clean paragraph: some add-ons end descriptions with blank lines (Below), which
     * showed as an empty gap under the text, and a paragraph break would waste one of the 5 lines.
     */
    fun homeSynopsis(raw: String): String? =
        raw.replace("\r\n", "\n")
            .trim { it.isWhitespace() || it == '\u200B' || it == '\uFEFF' }
            .replace(Regex("[\\h\u200B]*\n[\\s\u200B]*"), " ")
            .takeIf { it.isNotEmpty() }

    fun synopsisStyle(base: TextStyle): TextStyle = base.copy(fontSize = 13.sp, lineHeight = 19.sp)

    /** Details synopsis, a step smaller than home (still 8 lines before "Read more"). */
    fun detailSynopsisStyle(base: TextStyle): TextStyle = base.copy(fontSize = 12.sp, lineHeight = 17.sp)
}
