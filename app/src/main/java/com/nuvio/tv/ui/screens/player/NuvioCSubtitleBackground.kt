package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 29 `subtitle_player_controls`: the background box choices offered in the player's
// subtitle style tab. Stepping cycles Off, Light, Medium, Solid; a custom colour set in Settings shows as
// "Custom" and the first step moves to the nearest end of the list.

import androidx.annotation.StringRes
import com.nuvio.tv.R

internal object NuvioCSubtitleBackground {
    class Preset(@StringRes val label: Int, val argb: Int)

    val presets = listOf(
        Preset(R.string.nuvio_c_subtitle_background_off, 0x00000000),
        Preset(R.string.nuvio_c_subtitle_background_light, 0x66000000),
        Preset(R.string.nuvio_c_subtitle_background_medium, 0x99000000.toInt()),
        Preset(R.string.nuvio_c_subtitle_background_solid, 0xFF000000.toInt())
    )

    fun step(current: Int, delta: Int): Int {
        val index = presets.indexOfFirst { it.argb == current }
        val next = when {
            index >= 0 -> (index + delta).coerceIn(0, presets.lastIndex)
            delta > 0 -> 0
            else -> presets.lastIndex
        }
        return presets[next].argb
    }
}
