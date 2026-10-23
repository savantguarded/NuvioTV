package com.nuvio.tv.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C card sizes (switch `card_size_xl`, round 21, 2026-10-23; round 22 same day).
// One preset after Large in Settings > Layout > Card width: Extra large. Saved as a plain width (156,
// height x 1.5 as official's setter does), so Nuvio sync and the official app just see a bigger number.
// It changes Home and collection folders that follow the Home layout; each layout gets its own size:
//
//  | Preset            | Classic   | Modern / Featured portrait | Modern / Featured landscape |
//  | Large 140 (today) | 140 x 210 | 127 x 190                  | 233 x 131                   |
//  | Extra large 156   | 148 x 222 | 141 x 212                  | 276 x 156                   |
//
// Round 22 (Charles): Huge (172) is gone; a profile still saved at 172 x 258 reads as Extra large
// (LayoutPreferenceDataStore, never written back). The rows area stays exactly Large's height
// (52% portrait / 49% landscape), so the hero's logo, synopsis lines and width match Large; the
// bigger cards fit through tighter spacing only: title-to-poster gap 14 -> 8 dp, gap between rows
// 24 -> 12 dp, focus line 40 -> 32 dp (24 dp title line + 8 dp gap). Row titles stay official
// (round 22b: the 18 sp titles were reverted). Portrait: 32 + 212 + 12 + 24 = 280 dp of 280.8 (next row's title shows in full);
// landscape: 32 + 156 + 12 + 24 + 8 = 232 of 264.6 (about 32 dp of the next row's art).
// Library and Discover / Search stay at Large. Any other stored value passes through unchanged, so
// every official preset looks exactly as before.
object NuvioCCardSize {
    const val LARGE = 140
    const val EXTRA_LARGE = 156
    /** Round 21's Huge, removed in round 22: read as Extra large. */
    const val LEGACY_HUGE = 172
    private const val LEGACY_HUGE_HEIGHT = 258

    private const val MODERN_PORTRAIT = 0.84f * 1.08f
    private const val MODERN_LANDSCAPE = 1.24f * 1.34f

    private val enabled: Boolean get() = NuvioCFeatures.CARD_SIZE_XL

    /** True for the two Nuvio C presets (only while the switch is on). */
    fun isBig(stored: Int): Boolean = enabled && (stored == EXTRA_LARGE || stored == LEGACY_HUGE)

    /** Stored width as shown: the old Huge reads as Extra large. */
    fun normalizeWidth(stored: Int): Int = if (enabled && stored == LEGACY_HUGE) EXTRA_LARGE else stored
    fun normalizeHeight(stored: Int): Int = if (enabled && stored == LEGACY_HUGE_HEIGHT) EXTRA_LARGE * 3 / 2 else stored

    /** Large's Modern landscape card width (official 140 x 1.24 x 1.34). */
    val LARGE_LANDSCAPE_CARD: Dp = (LARGE * MODERN_LANDSCAPE).dp

    /**
     * Home hero text width: to the second landscape poster's right edge, never wider than at Large
     * (switch `hero_width_cap`), so Extra large keeps Large's synopsis width and line count.
     * Round 22b: the cap used to sit after a line break and only clamped the gap, never the width.
     */
    fun heroTextWidth(landscapeCardWidth: Dp, gap: Dp): Dp {
        val card = if (NuvioCFeatures.HERO_WIDTH_CAP) minOf(landscapeCardWidth, LARGE_LANDSCAPE_CARD) else landscapeCardWidth
        return card * 2 + gap
    }

    /** Classic poster width on Home. */
    fun classicWidth(stored: Int): Int = when {
        !enabled -> stored
        stored == EXTRA_LARGE -> 148
        stored == LEGACY_HUGE -> 148
        else -> stored
    }

    /** Modern / Featured landscape base (the official formula multiplies it by 1.24 x 1.34). */
    fun modernLandscapeBase(stored: Int): Float = when {
        !enabled -> stored.toFloat()
        stored == EXTRA_LARGE -> 276f / MODERN_LANDSCAPE
        stored == LEGACY_HUGE -> 276f / MODERN_LANDSCAPE
        else -> stored.toFloat()
    }

    /** Library, Discover / Search, collection folders: never past Large. */
    fun otherWidth(stored: Int): Int = if (enabled && stored > LARGE) LARGE else stored
    fun otherHeight(storedHeight: Int): Int = if (enabled && storedHeight > LARGE * 3 / 2) LARGE * 3 / 2 else storedHeight

    /** Gap under a Modern row title (official 14 dp). */
    fun rowTitleBottom(stored: Int): Dp = if (isBig(stored)) 8.dp else 14.dp

    /** Gap between Modern rows (official 24 dp). */
    fun rowGap(stored: Int, official: Dp): Dp = if (isBig(stored)) 12.dp else official

    /** Where the focused card's top edge lands in the rows area (official 40 dp): title line + gap. */
    fun focusInset(stored: Int, official: Dp): Dp = if (isBig(stored)) 32.dp else official

    /**
     * Height of Modern's rows area. Official: 52% (portrait) / 49% (landscape) of the screen, also at
     * Extra large on a 540 dp tall screen (round 22). It only grows on a shorter screen, where focus
     * line + card + gap + the next row's title (portrait) or title and 20 dp of art (landscape) wouldn't
     * fit. Shared with the Featured carousel, so its slide lands the rows exactly.
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
            val card = (normalizeHeight(storedHeight) * MODERN_PORTRAIT).dp
            inset + card + gap + title
        }
        return maxOf(official, need)
    }
}
