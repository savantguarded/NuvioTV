package com.nuvio.tv.data.trailer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// [fork] Nuvio C: IMDb trailer lookup, used only as a backup when YouTube rate-limits
// (see NuvioCTrailerBackup.kt). First ported from ysosrs123/NuvioTV-Fork (ImdbTrailerResolver.kt,
// GPL-3.0), which loaded the IMDb title page in a hidden WebView to pass IMDb's bot check.
//
// Round 14 (2026-10-21, `imdb_no_browser`): the hidden WebView is gone. Its renderer is a separate
// process that Android kills first when memory runs low, and an unhandled renderer kill takes the
// whole app down with it (WebView's rule) -- that was the 4K DV crash at ~80% of an episode, when
// the post-play prefetch asked for trailers. IMDb's own GraphQL endpoint (the one imdb.com calls)
// answers without the bot check and returns the same hero video ("Play trailer" slot =
// primaryVideos.edges[0]) with the same signed mp4 rungs. One ~2 KB request, no browser, no process.
// Picks the best progressive mp4 between 720p and 1080p; below 720p or on any failure returns null
// and the caller keeps the YouTube result.
@Singleton
class NuvioCImdbTrailerResolver @Inject constructor() {
    private companion object {
        const val TAG = "NuvioCImdbTrailer"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
        const val GRAPHQL_URL = "https://caching.graphql.imdb.com/"
        const val ATTEMPTS = 2

        const val RES_FLOOR = 720          // never serve below this from IMDb
        const val RES_CAP = 1080           // cap picks at 1080p even when 4K exists

        val TT_ID = Regex("""^tt\d{6,}$""")
        val JSON = "application/json".toMediaType()
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private data class Encoding(val def: String, val height: Int, val mp4: Boolean, val url: String)

    /**
     * Resolve the IMDb hero trailer for [imdbId] (a `tt...` id). Returns a playback source pointing at a
     * signed CloudFront mp4 (>=720p, <=1080p), or null (caller keeps YouTube). [type] is unused.
     */
    suspend fun resolve(imdbId: String, type: String? = null): TrailerPlaybackSource? {
        if (!TT_ID.matches(imdbId)) {
            Log.w(TAG, "resolve called with non-imdb id: $imdbId")
            return null
        }
        repeat(ATTEMPTS) { attempt ->
            val body = runCatching { withContext(Dispatchers.IO) { query(imdbId) } }
                .onFailure { Log.w(TAG, "[$imdbId] request failed (attempt ${attempt + 1}): ${it.message}") }
                .getOrNull()
            if (body != null) return pick(imdbId, body)
        }
        Log.w(TAG, "[$imdbId] no answer from IMDb -> null (keep YouTube)")
        return null
    }

    private fun query(imdbId: String): String? {
        val gql = "query { title(id: \"$imdbId\") { primaryVideos(first: 1) { edges { node { " +
            "id name { value } runtime { value } " +
            "playbackURLs { url videoMimeType videoDefinition displayName { value } } } } } } }"
        val payload = JSONObject().put("query", gql).toString()
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(payload.toRequestBody(JSON))
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Origin", "https://www.imdb.com")
            .header("Referer", "https://www.imdb.com/")
            .header("x-imdb-client-name", "imdb-web-next-localized")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "[$imdbId] IMDb answered HTTP ${response.code}")
                return null
            }
            return response.body?.string()
        }
    }

    private fun pick(imdbId: String, body: String): TrailerPlaybackSource? {
        val node = runCatching {
            JSONObject(body).optJSONObject("data")?.optJSONObject("title")
                ?.optJSONObject("primaryVideos")?.optJSONArray("edges")
                ?.optJSONObject(0)?.optJSONObject("node")
        }.getOrNull()
        if (node == null) {
            Log.i(TAG, "[$imdbId] no hero video on IMDb -> null (keep YouTube)")
            return null
        }
        val vi = node.optString("id", "?")
        val best = pickBestMp4(parsePlaybackURLs(node.optJSONArray("playbackURLs")))
        if (best == null || best.height < RES_FLOOR) {
            Log.i(TAG, "[$imdbId] hero $vi best mp4 ${best?.height ?: "none"} < ${RES_FLOOR}p -> null (keep YouTube)")
            return null
        }
        Log.i(TAG, "[$imdbId] SELECTED $vi def=${best.def} h=${best.height}")
        return TrailerPlaybackSource(videoUrl = normalise(best.url))
    }

    /**
     * Parse an IMDb playbackURLs array into encodings. Resolution: prefer videoDefinition
     * (DEF_1080p/DEF_SD/DEF_AUTO...), then displayName.value ("1080p"/"SD"/"AUTO"), then legacy
     * "definition". mp4 vs HLS from videoMimeType ("MP4"/"M3U8"), url and legacy mimeType as fallback.
     */
    private fun parsePlaybackURLs(urls: JSONArray?): List<Encoding> {
        val encs = mutableListOf<Encoding>()
        if (urls != null) {
            for (i in 0 until urls.length()) {
                val e = urls.optJSONObject(i) ?: continue
                val u = normalise(e.optString("url", ""))
                if (u.isBlank()) continue
                val vdef = e.optString("videoDefinition", "")
                val ddef = deep(e, "displayName", "value") ?: ""
                val def = when {
                    vdef.isNotBlank() -> vdef
                    ddef.isNotBlank() -> ddef
                    else -> e.optString("definition", "")
                }
                val vmime = e.optString("videoMimeType", "").ifBlank { e.optString("mimeType", "") }
                val isHls = vmime.contains("m3u8", true) || def.contains("AUTO", true) || u.contains(".m3u8", true)
                val mp4 = !isHls && (vmime.contains("mp4", true) || u.contains(".mp4", true))
                encs.add(Encoding(def, defHeight(def), mp4, u))
            }
        }
        return encs
    }

    /** Best progressive mp4 <= RES_CAP; if only >cap rungs exist take the smallest of those; height 0 (unknown) last. */
    private fun pickBestMp4(encodings: List<Encoding>): Encoding? {
        val mp4s = encodings.filter { it.mp4 }
        if (mp4s.isEmpty()) return null
        val capped = mp4s.filter { it.height in 1..RES_CAP }
        if (capped.isNotEmpty()) return capped.maxByOrNull { it.height }
        val overCap = mp4s.filter { it.height > RES_CAP }
        if (overCap.isNotEmpty()) return overCap.minByOrNull { it.height }
        return mp4s.maxByOrNull { it.height } // all unknown height
    }

    // ---- json helpers ----

    private fun normalise(s: String): String =
        s.replace("\\u002F", "/").replace("\\u0026", "&").replace("\\/", "/").replace("&amp;", "&")

    private fun deep(o: JSONObject?, vararg keys: String): String? {
        var cur: Any? = o
        for (k in keys) { cur = (cur as? JSONObject)?.opt(k) ?: return null }
        return (cur as? String) ?: cur?.toString()?.takeIf { it != "null" }
    }

    private fun deepInt(o: JSONObject?, vararg keys: String): Int? {
        var cur: Any? = o
        for (k in keys) { cur = (cur as? JSONObject)?.opt(k) ?: return null }
        return when (cur) {
            is Int -> cur
            is Number -> cur.toInt()
            is String -> cur.toIntOrNull()
            else -> null
        }
    }

    private fun deepObj(o: JSONObject?, vararg keys: String): JSONObject? {
        var cur: Any? = o
        for (k in keys) { cur = (cur as? JSONObject)?.opt(k) ?: return null }
        return cur as? JSONObject
    }

    /** Height from an IMDb definition label, hardened against non-`NNNNp` variants (4K/UHD/HD/SD...). */
    private fun defHeight(def: String): Int {
        val d = def.trim().uppercase().removePrefix("DEF_").uppercase()
        when {
            d.contains("2160") || d == "4K" || d == "UHD" -> return 2160
            d.contains("1440") || d == "2K" || d == "QHD" -> return 1440
            d.contains("1080") || d == "FHD" || d == "FULLHD" || d == "FULL HD" -> return 1080
            d.contains("720") || d == "HD" -> return 720
            d.contains("480") -> return 480
            d == "SD" -> return 480
            d.contains("360") -> return 360
            d.contains("240") -> return 240
            d == "AUTO" || d.isBlank() -> return 0 // adaptive/HLS or unknown -> unverified
        }
        return Regex("""(\d{3,4})""").find(d)?.value?.toIntOrNull() ?: 0
    }
}
