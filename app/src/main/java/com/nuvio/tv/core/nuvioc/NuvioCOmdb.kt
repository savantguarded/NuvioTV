package com.nuvio.tv.core.nuvioc

// [fork] Nuvio C round 37 `omdb_awards`: the Awards line for the details page, from OMDb with your own key.
// Only the Awards field is used. One request per title, kept 30 days on this TV (a title OMDb has no awards
// for is kept 14 days), so the free plan's daily allowance is never close. Your meta add-on's own awards text,
// when it sends one, is used first and OMDb isn't asked.

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NuvioCOmdb {
    private const val TAG = "NuvioCOmdb"
    private const val PREFS = "nuvio_c_omdb"
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000
    private const val EMPTY_TTL_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 400
    private const val NONE = "\u0000"

    /** Awards text for an IMDb id, or null (no key, no awards, or OMDb unreachable). Call off the main thread. */
    suspend fun awards(context: Context, imdbId: String): String? {
        if (!com.nuvio.tv.NuvioCFeatures.OMDB_AWARDS || !imdbId.startsWith("tt")) return null
        val key = NuvioCApiKeys.omdb(context).takeIf { it.isNotBlank() } ?: return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.getString(imdbId, null)?.let { kept ->
            val at = kept.substringBefore('|').toLongOrNull() ?: 0L
            val value = kept.substringAfter('|')
            val ttl = if (value == NONE) EMPTY_TTL_MS else TTL_MS
            if (now - at < ttl) return value.takeUnless { it == NONE }
        }
        val fetched = withContext(Dispatchers.IO) { fetch(key, imdbId) } ?: return null // unreachable: ask again next time
        val clean = tidy(fetched.second)
        prefs.edit {
            putString(imdbId, "$now|${clean ?: NONE}")
            if (prefs.all.size > MAX_ENTRIES) {
                prefs.all.entries.sortedBy { (it.value as? String)?.substringBefore('|')?.toLongOrNull() ?: 0L }
                    .take(prefs.all.size - MAX_ENTRIES).forEach { remove(it.key) }
            }
        }
        return clean
    }

    /** "Won 4 Oscars. 152 wins & 220 nominations total" -> "Won 4 Oscars · 152 wins & 220 nominations". */
    fun tidy(raw: String?): String? {
        val t = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("N/A", true) } ?: return null
        return t.removeSuffix(".").replace(Regex("""\.\s+"""), " · ").replace(Regex("""\s+total$""", RegexOption.IGNORE_CASE), "").trim()
    }

    /** True = OMDb accepted the key, false = refused, null = couldn't reach OMDb. */
    suspend fun keyWorks(key: String): Boolean? = withContext(Dispatchers.IO) {
        val r = fetch(key.trim(), "tt0111161") ?: return@withContext null
        r.first
    }

    /** (key accepted, Awards field) or null when OMDb can't be reached. */
    private fun fetch(key: String, imdbId: String): Pair<Boolean, String?>? = runCatching {
        val url = URL("https://www.omdbapi.com/?i=${URLEncoder.encode(imdbId, "UTF-8")}&apikey=${URLEncoder.encode(key, "UTF-8")}")
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
        }
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401) return@runCatching false to null
            if (code !in 200..299) return@runCatching null
            val o = JSONObject(body)
            if (o.optString("Response") == "False") {
                val err = o.optString("Error")
                return@runCatching if (err.contains("key", true)) false to null else true to null
            }
            true to o.optString("Awards").takeIf { it.isNotBlank() }
        } finally {
            c.disconnect()
        }
    }.onFailure { Log.d(TAG, "OMDb not reached: ${it.message}") }.getOrNull()
}
