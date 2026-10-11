package com.nuvio.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C trailer preferences. Kept on this TV only, in their own file, so they never
// enter official's settings or the settings that sync to the Nuvio account.
object NuvioCTrailerPrefs {
    private const val FILE = "nuvio_c_prefs"
    const val KEY_BG_TRAILER_MUTED = "bg_trailer_muted"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setBackgroundTrailerMuted(context: Context, muted: Boolean) {
        prefs(context).edit().putBoolean(KEY_BG_TRAILER_MUTED, muted).apply()
    }

    const val KEY_PREFER_IMDB = "prefer_imdb_trailers"

    /** Trailers: IMDb first, YouTube as the fallback (2026-10-11). Off by default. */
    fun preferImdb(context: Context): Boolean = prefs(context).getBoolean(KEY_PREFER_IMDB, false)

    fun setPreferImdb(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREFER_IMDB, value).apply()
    }
}

/** Whether background trailers play muted. Always true when the switch is off (the old behaviour). */
@Composable
fun rememberNuvioCBackgroundTrailerMuted(): Boolean {
    if (!NuvioCFeatures.BG_TRAILER_SOUND_TOGGLE) return true
    val context = LocalContext.current
    val prefs = remember(context) { NuvioCTrailerPrefs.prefs(context) }
    var muted by remember(prefs) {
        mutableStateOf(prefs.getBoolean(NuvioCTrailerPrefs.KEY_BG_TRAILER_MUTED, true))
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == NuvioCTrailerPrefs.KEY_BG_TRAILER_MUTED) {
                muted = p.getBoolean(NuvioCTrailerPrefs.KEY_BG_TRAILER_MUTED, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return muted
}
