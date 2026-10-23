package com.nuvio.tv.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C card sizes (switch `card_size_xl`, round 21, 2026-10-23). Two presets after Large in
// Settings > Layout > Card width: Extra large and Huge. They are saved as plain widths (156 / 172, height
// x 1.5 as official's setter does), so Nuvio sync and the official app just see a bigger number.
// They only change Home; each layout gets its own size:
//
//  | Preset            | Classic   | Modern / Featured portrait | Modern / Featured landscape |
//  | Large 140 (today) | 140 x 210 | 127 x 190                  | 233 x 131                   |
//  | Extra large 156   | 148 x 222 | 141 x 212                  | 276 x 156                   |
//  | Huge 172          | 156 x 234 | 156 x 234                  | 320 x 181                   |
//
// At Extra large and Huge, Modern / Featured rows are tighter (title-to-poster gap 14 -> 8 dp, gap
// between rows 24 -> 14 dp, focus line 40 -> 34 dp) and the rows area grows only as much as the
// bigger card needs, so the next row still peeks (portrait: its title; landscape: title + ~20 dp of
// art) and the hero synopsis keeps five lines where it can (portrait Huge: four).
// Library, Discover / Search and collection folders stay at Large. Any other stored value passes
// through unchanged, so every official preset looks exactly as before.
object NuvioCCardSize {
    const val LARGE = 140
    const val EXTRA_LARGE = 156
    const val HUGE = 172

    private const val MODERN_PORTRAIT = 0.84f * 1.08f
    private const val MODERN_LANDSCAPE = 1.24f * 1.34f

    private val enabled: Boolean get() = NuvioCFeatures.CARD_SIZE_XL

    /** True for the two Nuvio C presets (only while the switch is on). */
    fun isBig(stored: Int): Boolean = enabled && (stored == EXTRA_LARGE || stored == HUGE)

    /** Classic poster width on Home. */
    fun classicWidth(stored: Int): Int = when {
        !enabled -> stored
        stored == EXTRA_LARGE -> 148
        stored == HUGE -> 156
        else -> stored
    }

    /** Modern / Featured landscape base (the official formula multiplies it by 1.24 x 1.34). */
    fun modernLandscapeBase(stored: Int): Float = when {
        !enabled -> stored.toFloat()
        stored == EXTRA_LARGE -> 276f / MODERN_LANDSCAPE
        stored == HUGE -> 320f / MODERN_LANDSCAPE
        else -> stored.toFloat()
    }

    /** Library, Discover / Search, collection folders: never past Large. */
    fun otherWidth(stored: Int): Int = if (enabled && stored > LARGE) LARGE else stored
    fun otherHeight(storedHeight: Int): Int = if (enabled && storedHeight > LARGE * 3 / 2) LARGE * 3 / 2 else storedHeight

    /** Gap under a Modern row title (official 14 dp). */
    fun rowTitleBottom(stored: Int): Dp = if (isBig(stored)) 8.dp else 14.dp

    /** Gap between Modern rows (official 24 dp). */
    fun rowGap(stored: Int, official: Dp): Dp = if (isBig(stored)) 14.dp else official

    /** Where the focused card's top edge lands in the rows area (official 40 dp). */
    fun focusInset(stored: Int, official: Dp): Dp = if (isBig(stored)) 34.dp else official

    /**
     * Height of Modern's rows area. Official: 52% (portrait) / 49% (landscape) of the screen. At Extra
     * large / Huge it grows to fit focus line + card + gap + the next row's title (portrait) or title and
     * 20 dp of art (landscape). Shared with the Featured carousel, so its slide lands the rows exactly.
     */
    fun rowsViewport(screenHeight: Dp, landscape: Boolean, stored: Int, storedHeight: Int): Dp {
        val official = screenHeight * (if (landscape) 0.49f else 0.52f)
        if (!isBig(stored)) return official
        val title = 24.dp
        val inset = focusInset(stored, 40.dp)
        val gap = rowGap(stored, 24.dp)
        val need = if (landscape) {
            val card = (modernLandscapeBase(stored) * MODERN_LANDSCAPE / 1.77f).dp
            inset + card + gap + title + rowTitleBottom(stored) + 20.dp
        } else {
            val card = (storedHeight * MODERN_PORTRAIT).dp
            inset + card + gap + title + 2.dp
        }
        return maxOf(official, need)
    }
}
