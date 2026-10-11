package com.nuvio.tv.core.nuvioc

import android.content.Context
import androidx.core.content.edit

// [fork] Nuvio C: small app-wide settings that official Nuvio has no place for. Plain
// SharedPreferences, so reading them costs nothing on the player's hot paths.
object NuvioCPrefs {
    private const val FILE = "nuvio_c_prefs"
    private const val KEY_PREFER_SDH = "prefer_sdh_subtitles"
    private const val KEY_SUBTITLE_FONT = "subtitle_font"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Player: prefer the SDH version of an embedded subtitle in your language. Off by default. */
    fun preferSdh(context: Context): Boolean = prefs(context).getBoolean(KEY_PREFER_SDH, false)

    fun setPreferSdh(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_PREFER_SDH, value) }
    }

    /** Player: subtitle font id (NuvioCSubtitleFont), "" = official default. */
    fun subtitleFont(context: Context): String = prefs(context).getString(KEY_SUBTITLE_FONT, "") ?: ""

    fun setSubtitleFont(context: Context, id: String) {
        prefs(context).edit { putString(KEY_SUBTITLE_FONT, id) }
    }
}
