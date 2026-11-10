package com.nuvio.tv.core.nuvioc

// [fork] Nuvio C round 37 `api_keys`: your own TheTVDB and OMDb keys, entered in Settings > Integrations.
//
// Kept on this TV only, in the fork's own private file: never sent to your Nuvio account (not in official's
// provider-credential sync, not in the fork's settings sync, which rides in the account's settings blob).
// A key built into the app (TVDB_API_KEY in local.properties) still works when no key is entered here.
//  - TheTVDB: Calendar air times only (TvdbCalendarClient).
//  - OMDb: the Awards line in the details page's Information block (NuvioCOmdb).

import android.content.Context
import androidx.core.content.edit
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NuvioCApiKeys {
    private const val PREFS = "nuvio_c_api_keys"
    private const val TVDB = "tvdb"
    private const val OMDB = "omdb"

    data class Keys(val tvdb: String = "", val omdb: String = "")

    private val _keys = MutableStateFlow<Keys?>(null)
    val keys: StateFlow<Keys?> = _keys.asStateFlow()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): Keys = _keys.value ?: prefs(context).let {
        Keys(it.getString(TVDB, "").orEmpty(), it.getString(OMDB, "").orEmpty())
    }.also { _keys.value = it }

    fun ensureLoaded(context: Context) { load(context) }

    /** The key TheTVDB calls use: yours, else the built-in one, else blank (TheTVDB off). */
    fun tvdb(context: Context): String {
        val own = if (NuvioCFeatures.API_KEYS) load(context).tvdb else ""
        return own.ifBlank { BuildConfig.TVDB_API_KEY }
    }

    /** Your OMDb key, blank when not set. */
    fun omdb(context: Context): String = if (NuvioCFeatures.API_KEYS) load(context).omdb else ""

    fun setTvdb(context: Context, key: String) = save(context) { it.copy(tvdb = key.trim()) }
    fun setOmdb(context: Context, key: String) = save(context) { it.copy(omdb = key.trim()) }

    private fun save(context: Context, change: (Keys) -> Keys) {
        val next = change(load(context))
        prefs(context).edit { putString(TVDB, next.tvdb); putString(OMDB, next.omdb) }
        _keys.value = next
    }

    /** "Not set" or the last four characters, like the MDBList and Anime-Skip rows. */
    fun masked(key: String, notSet: String): String =
        if (key.isBlank()) notSet else "••••" + key.takeLast(4)
}
