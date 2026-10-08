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

// [fork] Nuvio C details-page sections: one row per section (Cast, Ratings, More like this,
// Trailers, Collection) instead of official's tab row, with order and on/off kept on this TV only
// (same file as the trailer prefs, never in official settings or account sync).
object NuvioCSectionPrefs {
    const val KEY_ROWS = "section_rows"
    const val KEY_ORDER = "section_order"
    const val KEY_HIDDEN = "section_hidden"

    /** Official tab order; also the order shown first time and after "reset". */
    val DEFAULT_ORDER = listOf("CAST", "RATINGS", "MORE_LIKE_THIS", "TRAILER", "COLLECTION")

    fun prefs(context: Context): SharedPreferences = NuvioCTrailerPrefs.prefs(context)

    fun read(p: SharedPreferences): Settings = Settings(
        rows = p.getBoolean(KEY_ROWS, true),
        order = normalize(p.getString(KEY_ORDER, null)?.split(',')),
        hidden = p.getString(KEY_HIDDEN, null)?.split(',')?.filter { it in DEFAULT_ORDER }?.toSet() ?: emptySet()
    )

    fun setRows(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ROWS, on).apply()

    fun setHidden(context: Context, section: String, hidden: Boolean) {
        val current = read(prefs(context)).hidden
        val next = if (hidden) current + section else current - section
        prefs(context).edit().putString(KEY_HIDDEN, next.joinToString(",")).apply()
    }

    /** Moves [section] one place up (delta -1) or down (+1). */
    fun move(context: Context, section: String, delta: Int) {
        val order = read(prefs(context)).order.toMutableList()
        val from = order.indexOf(section)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.removeAt(from)
        order.add(to, section)
        prefs(context).edit().putString(KEY_ORDER, order.joinToString(",")).apply()
    }

    fun reset(context: Context) = prefs(context).edit().remove(KEY_ORDER).remove(KEY_HIDDEN).apply()

    /** Known names only, each once, missing ones appended in official order. */
    fun normalize(saved: List<String>?): List<String> {
        val known = saved.orEmpty().map { it.trim() }.filter { it in DEFAULT_ORDER }.distinct()
        return known + DEFAULT_ORDER.filter { it !in known }
    }

    data class Settings(val rows: Boolean, val order: List<String>, val hidden: Set<String>)
}

/** Live section settings; rows off (official tabs) whenever the switch is off. */
@Composable
fun rememberNuvioCSectionSettings(): NuvioCSectionPrefs.Settings {
    val off = NuvioCSectionPrefs.Settings(false, NuvioCSectionPrefs.DEFAULT_ORDER, emptySet())
    if (!NuvioCFeatures.SECTION_ROWS) return off
    val context = LocalContext.current
    val prefs = remember(context) { NuvioCSectionPrefs.prefs(context) }
    var settings by remember(prefs) { mutableStateOf(NuvioCSectionPrefs.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == NuvioCSectionPrefs.KEY_ROWS || key == NuvioCSectionPrefs.KEY_ORDER ||
                key == NuvioCSectionPrefs.KEY_HIDDEN || key == null
            ) {
                settings = NuvioCSectionPrefs.read(p)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
